package com.example.bspos.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.*

/** Adds ownership without changing any existing business value or deleting a record. */
object TenantMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        // The old trigger rejected even metadata-only backfill. It is reinstated below.
        db.execSQL("DROP TRIGGER IF EXISTS inventory_movements_no_update")
        TenantIntegrity.tables.forEach { table ->
            db.execSQL("ALTER TABLE `$table` ADD COLUMN remote_shop_id TEXT")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN tenancy_status TEXT NOT NULL DEFAULT 'legacy_ambiguous'")
            db.execSQL("CREATE INDEX idx_${table}_shop ON `$table`(remote_shop_id)")
        }
        db.execSQL("ALTER TABLE cash_sessions ADD COLUMN remote_session_id TEXT")
        db.execSQL("CREATE UNIQUE INDEX idx_cash_sessions_shop_remote ON cash_sessions(remote_shop_id, remote_session_id)")
        db.execSQL("ALTER TABLE route_customers ADD COLUMN active_slot INTEGER DEFAULT 1")
        val unique = mapOf(
            "idx_products_internal_code" to "products(remote_shop_id,internal_code)",
            "idx_customers_micatalogo_customer_id" to "customers(remote_shop_id,micatalogo_customer_id)",
            "idx_routes_code" to "routes(remote_shop_id,code)",
            "idx_sales_invoice_number" to "sales(remote_shop_id,invoice_number)",
            "idx_payments_receipt_number" to "payments(remote_shop_id,receipt_number)",
            "idx_returns_return_number" to "returns(remote_shop_id,return_number)",
            "idx_route_customers_customer_id" to "route_customers(customer_id,active_slot)",
            "idx_route_customers_visit_order" to "route_customers(route_id,visit_order,active_slot)"
        )
        unique.forEach { (index, columns) ->
            db.execSQL("DROP INDEX IF EXISTS `$index`")
            db.execSQL("CREATE UNIQUE INDEX `$index` ON $columns")
        }
        db.execSQL("UPDATE products SET remote_shop_id=NULLIF(trim(micatalogo_shop_id),''),tenancy_status=CASE WHEN length(trim(COALESCE(micatalogo_shop_id,'')))>0 THEN 'assigned' ELSE 'legacy_ambiguous' END")
        db.execSQL("UPDATE customers SET remote_shop_id=NULLIF(trim(micatalogo_customer_shop_id),''),tenancy_status=CASE WHEN length(trim(COALESCE(micatalogo_customer_shop_id,'')))>0 THEN 'assigned' ELSE 'legacy_ambiguous' END")

        val evidence = mutableMapOf<Pair<String, String>, MutableSet<String>>()
        val conflictingEvidence = mutableMapOf<String, MutableSet<String>>()
        fun record(table: String, id: String?, shop: String?) {
            if (!id.isNullOrBlank() && !shop.isNullOrBlank()) evidence.getOrPut(table to id) { mutableSetOf() } += shop
        }
        db.query("SELECT sale_id,remote_shop_id FROM pos_sale_outbox").use { c -> while (c.moveToNext()) record("sales", c.getString(0), c.getString(1)) }
        db.query("SELECT id,shopId FROM payment_sync_outbox").use { c -> while (c.moveToNext()) record("payments", c.getString(0), c.getString(1)) }
        db.query("SELECT shopId,payload FROM operation_outbox").use { c ->
            while (c.moveToNext()) {
                val payload = runCatching { Json.parseToJsonElement(c.getString(1)).jsonObject }.getOrNull() ?: continue
                val type = (payload["type"] as? JsonPrimitive)?.contentOrNull
                if (type == "customer_upsert") record("customers", (payload["client_customer_uuid"] as? JsonPrimitive)?.contentOrNull, c.getString(0))
                record("cash_sessions", (payload["local_cash_session_uuid"] as? JsonPrimitive)?.contentOrNull, c.getString(0))
            }
        }
        evidence.forEach { (key, shops) ->
            val (table, id) = key
            val existing = db.query("SELECT remote_shop_id FROM `$table` WHERE id=?", arrayOf(id)).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
            val owners = shops + listOfNotNull(existing)
            if (owners.size == 1) db.execSQL("UPDATE `$table` SET remote_shop_id=?,tenancy_status='assigned' WHERE id=?", arrayOf(owners.single(), id))
            else {
                conflictingEvidence.getOrPut(table) { mutableSetOf() } += id
                db.execSQL("UPDATE `$table` SET tenancy_status='legacy_ambiguous' WHERE id=?", arrayOf(id))
            }
        }

        // Old versions did not make a remote product unique within its shop. Preserve all
        // duplicates for reconciliation instead of arbitrarily retaining one or failing open.
        db.execSQL("""UPDATE products SET remote_shop_id=NULL,tenancy_status='legacy_ambiguous'
            WHERE id IN (SELECT p.id FROM products p JOIN
                (SELECT remote_shop_id,micatalogo_product_id FROM products
                 WHERE remote_shop_id IS NOT NULL AND micatalogo_product_id IS NOT NULL
                 GROUP BY remote_shop_id,micatalogo_product_id HAVING COUNT(*)>1) d
                ON p.remote_shop_id=d.remote_shop_id AND p.micatalogo_product_id=d.micatalogo_product_id)""")
        db.execSQL("CREATE UNIQUE INDEX idx_products_shop_remote ON products(remote_shop_id,micatalogo_product_id)")

        fun derive(table: String, candidates: List<String>, key: String = "id") {
            val union = candidates.joinToString(" UNION ALL ")
            val where = "p.record_id=`$table`.`$key`"
            val blocked = conflictingEvidence[table].orEmpty().toTypedArray()
            val notConflicted = if (blocked.isEmpty()) "" else " AND `$key` NOT IN (${blocked.joinToString { "?" }})"
            db.execSQL("""UPDATE `$table` SET remote_shop_id=(SELECT MIN(shop) FROM ($union) p WHERE $where),tenancy_status='assigned'
                WHERE remote_shop_id IS NULL $notConflicted AND
                (SELECT COUNT(DISTINCT shop) FROM ($union) p WHERE $where)=1 AND
                (SELECT COUNT(*) FROM ($union) p WHERE $where AND (shop IS NULL OR state!='assigned'))=0""", blocked)
        }
        fun parent(child: String, fk: String, table: String) = "SELECT c.id AS record_id,p.remote_shop_id AS shop,p.tenancy_status AS state FROM `$child` c LEFT JOIN `$table` p ON p.id=c.`$fk` WHERE c.`$fk` IS NOT NULL"
        fun children(lines: String, fk: String) = "SELECT c.`$fk` AS record_id,p.remote_shop_id AS shop,p.tenancy_status AS state FROM `$lines` c LEFT JOIN products p ON p.id=c.product_id"
        fun assignedCount() = TenantIntegrity.tables.sumOf { table ->
            db.query("SELECT COUNT(*) FROM `$table` WHERE tenancy_status='assigned'").use { c -> c.moveToFirst(); c.getLong(0) }
        }
        var previous: Long
        do {
            previous = assignedCount()
            derive("categories", listOf("SELECT category_id AS record_id,remote_shop_id AS shop,tenancy_status AS state FROM products"))
            derive("units_of_measure", listOf("SELECT unit_id AS record_id,remote_shop_id AS shop,tenancy_status AS state FROM products"))
            derive("sales", listOf(parent("sales", "customer_id", "customers"), children("sale_items", "sale_id")))
            derive("stock_entries", listOf(children("stock_entry_items", "entry_id")))
            derive("purchases", listOf(children("purchase_items", "purchase_id")))
            derive("inventory_counts", listOf(children("inventory_count_items", "count_id")))
            derive("suppliers", listOf("SELECT supplier_id AS record_id,remote_shop_id AS shop,tenancy_status AS state FROM purchases", "SELECT supplier_id AS record_id,remote_shop_id AS shop,tenancy_status AS state FROM stock_entries WHERE supplier_id IS NOT NULL"))
            derive("routes", listOf("SELECT r.route_id AS record_id,c.remote_shop_id AS shop,c.tenancy_status AS state FROM route_customers r LEFT JOIN customers c ON c.id=r.customer_id"))
            derive("inventory_adjustment_reasons", listOf("SELECT m.reason_id AS record_id,p.remote_shop_id AS shop,p.tenancy_status AS state FROM inventory_movements m LEFT JOIN products p ON p.id=m.product_id WHERE m.reason_id IS NOT NULL"))
            TenantIntegrity.relations.forEach { (table, refs) ->
                if (table == "products" || table == "sales" || table == "purchases" || table == "stock_entries") return@forEach
                if (table == "route_customers") {
                    derive(table, refs.map { (fk, owner) -> "SELECT c.rowid AS record_id,p.remote_shop_id AS shop,p.tenancy_status AS state FROM route_customers c LEFT JOIN `$owner` p ON p.id=c.`$fk`" }, "rowid")
                } else derive(table, refs.map { (fk, owner) -> parent(table, fk, owner) })
            }
        } while (assignedCount() > previous)
        // Conflicting relations are quarantined; their original UUIDs, amounts and FK values survive.
        val documents = mapOf("sales" to ("sale_items" to "sale_id"),
            "stock_entries" to ("stock_entry_items" to "entry_id"),
            "purchases" to ("purchase_items" to "purchase_id"),
            "inventory_counts" to ("inventory_count_items" to "count_id"),
            "route_loads" to ("route_load_items" to "route_load_id"),
            "returns" to ("return_items" to "return_id"))
        do {
            previous = assignedCount()
            TenantIntegrity.relations.forEach { (table, refs) ->
                val conflict = refs.joinToString(" OR ") { (fk, parent) ->
                    "(`$fk` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `$parent` p WHERE p.id=`$table`.`$fk` AND p.remote_shop_id=`$table`.remote_shop_id AND p.tenancy_status='assigned'))"
                }
                db.execSQL("UPDATE `$table` SET tenancy_status='legacy_ambiguous' WHERE tenancy_status='assigned' AND ($conflict)")
            }
            documents.forEach { (header, detail) ->
                val (lines, fk) = detail
                db.execSQL("""UPDATE `$header` SET tenancy_status='legacy_ambiguous' WHERE tenancy_status='assigned'
                    AND EXISTS(SELECT 1 FROM `$lines` c WHERE c.`$fk`=`$header`.id
                        AND (c.tenancy_status!='assigned' OR c.remote_shop_id IS NOT `$header`.remote_shop_id))""")
            }
            db.execSQL("""UPDATE payments SET tenancy_status='legacy_ambiguous' WHERE tenancy_status='assigned'
                AND EXISTS(SELECT 1 FROM payment_allocations a WHERE a.payment_id=payments.id
                    AND (a.tenancy_status!='assigned' OR a.remote_shop_id IS NOT payments.remote_shop_id))""")
        } while (assignedCount() < previous)
        InventoryIntegrity.install(db)
        TenantIntegrity.install(db)
    }
}
