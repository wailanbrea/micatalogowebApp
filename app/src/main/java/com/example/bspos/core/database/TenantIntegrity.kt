package com.example.bspos.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Defense in depth: a supplied UUID cannot connect records from different tenants. */
object TenantIntegrity : RoomDatabase.Callback() {
    val tables = listOf("categories", "units_of_measure", "products", "suppliers", "customers", "routes", "route_customers",
        "inventory_stock", "inventory_movements", "inventory_adjustment_reasons", "stock_entries", "stock_entry_items",
        "purchases", "purchase_items", "inventory_counts", "inventory_count_items", "route_loads", "route_load_items",
        "sales", "sale_items", "payments", "payment_allocations", "returns", "return_items", "cash_sessions", "cash_movements")

    val relations = mapOf(
        "products" to listOf("category_id" to "categories", "unit_id" to "units_of_measure"),
        "route_customers" to listOf("route_id" to "routes", "customer_id" to "customers"),
        "inventory_stock" to listOf("product_id" to "products"),
        "inventory_movements" to listOf("product_id" to "products", "reason_id" to "inventory_adjustment_reasons"),
        "stock_entries" to listOf("supplier_id" to "suppliers"),
        "stock_entry_items" to listOf("entry_id" to "stock_entries", "product_id" to "products"),
        "purchases" to listOf("supplier_id" to "suppliers"),
        "purchase_items" to listOf("purchase_id" to "purchases", "product_id" to "products"),
        "inventory_count_items" to listOf("count_id" to "inventory_counts", "product_id" to "products"),
        "route_loads" to listOf("route_id" to "routes"),
        "route_load_items" to listOf("route_load_id" to "route_loads", "product_id" to "products"),
        "sales" to listOf("customer_id" to "customers", "route_id" to "routes"),
        "sale_items" to listOf("sale_id" to "sales", "product_id" to "products"),
        "payments" to listOf("customer_id" to "customers", "route_id" to "routes"),
        "payment_allocations" to listOf("payment_id" to "payments", "sale_id" to "sales"),
        "returns" to listOf("sale_id" to "sales", "customer_id" to "customers"),
        "return_items" to listOf("return_id" to "returns", "sale_item_id" to "sale_items", "product_id" to "products"),
        "cash_movements" to listOf("session_id" to "cash_sessions")
    )

    override fun onCreate(db: SupportSQLiteDatabase) = install(db)

    fun install(db: SupportSQLiteDatabase) {
        tables.forEach { table ->
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS tenant_${table}_immutable
                BEFORE UPDATE ON `$table` WHEN OLD.remote_shop_id IS NOT NULL AND NEW.remote_shop_id IS NOT OLD.remote_shop_id
                BEGIN SELECT RAISE(ABORT, 'No se puede trasladar un registro a otra tienda'); END""")
            for (event in listOf("INSERT", "UPDATE")) {
                val checks = relations[table].orEmpty().map { (fk, parent) ->
                    "(NEW.`$fk` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `$parent` p WHERE p.id=NEW.`$fk` AND p.remote_shop_id=NEW.remote_shop_id AND p.tenancy_status='assigned'))"
                }.toMutableList()
                val alias = when (table) { "products" -> "micatalogo_shop_id"; "customers" -> "micatalogo_customer_shop_id"; else -> null }
                if (alias != null) checks += "(NEW.`$alias` IS NOT NULL AND NEW.`$alias` IS NOT NEW.remote_shop_id)"
                val invalid = if (checks.isEmpty()) "0" else checks.joinToString(" OR ")
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS tenant_${table}_${event.lowercase()}
                    BEFORE $event ON `$table` WHEN NEW.tenancy_status='assigned' AND
                    (NEW.remote_shop_id IS NULL OR length(trim(NEW.remote_shop_id))=0 OR $invalid)
                    BEGIN SELECT RAISE(ABORT, 'La relación no pertenece a la misma tienda o requiere conciliación'); END""")
            }
        }
    }
}
