package com.example.bspos.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE payment_sync_outbox ADD COLUMN server_response TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE operation_outbox ADD COLUMN server_response TEXT DEFAULT NULL")
        }
    }
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sales ADD COLUMN sale_mode TEXT NOT NULL DEFAULT 'retail'")
        }
    }
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS inventory_adjustment_reasons (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS inventory_stock (
                    id TEXT NOT NULL PRIMARY KEY,
                    product_id TEXT NOT NULL,
                    location_type TEXT NOT NULL,
                    location_id TEXT NOT NULL,
                    quantity INTEGER NOT NULL DEFAULT 0,
                    reserved_quantity INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX idx_inventory_stock_unique ON inventory_stock(product_id, location_type, location_id)")
            db.execSQL("CREATE INDEX idx_inventory_stock_product_id ON inventory_stock(product_id)")
            db.execSQL("CREATE INDEX idx_inventory_stock_location ON inventory_stock(location_type, location_id)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS inventory_movements (
                    id TEXT NOT NULL PRIMARY KEY,
                    product_id TEXT NOT NULL,
                    location_type TEXT NOT NULL,
                    location_id TEXT NOT NULL,
                    movement_type TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    previous_quantity INTEGER NOT NULL,
                    new_quantity INTEGER NOT NULL,
                    unit_cost INTEGER NOT NULL DEFAULT 0,
                    total_cost INTEGER NOT NULL DEFAULT 0,
                    reference_type TEXT,
                    reference_id TEXT,
                    reason_id TEXT,
                    notes TEXT,
                    created_at INTEGER NOT NULL,
                    created_by TEXT,
                    FOREIGN KEY (product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY (reason_id) REFERENCES inventory_adjustment_reasons(id) ON UPDATE CASCADE ON DELETE RESTRICT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_movements_product_created ON inventory_movements(product_id ASC, created_at DESC)")
            db.execSQL("CREATE INDEX idx_movements_reference ON inventory_movements(reference_type, reference_id)")
            db.execSQL("CREATE INDEX idx_movements_location ON inventory_movements(location_type, location_id)")
            db.execSQL("CREATE INDEX idx_movements_reason_id ON inventory_movements(reason_id)")
            InventoryIntegrity.install(db)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE stock_entries (id TEXT NOT NULL, document_number TEXT NOT NULL, supplier_id TEXT, date INTEGER NOT NULL, notes TEXT, status TEXT NOT NULL DEFAULT 'COMPLETED', created_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(supplier_id) REFERENCES suppliers(id) ON UPDATE CASCADE ON DELETE SET NULL)""")
            db.execSQL("CREATE INDEX idx_stock_entries_supplier_id ON stock_entries(supplier_id)")
            db.execSQL("CREATE INDEX idx_stock_entries_date ON stock_entries(date)")
            db.execSQL("""CREATE TABLE stock_entry_items (id TEXT NOT NULL, entry_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, unit_cost INTEGER NOT NULL, subtotal INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(entry_id) REFERENCES stock_entries(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX idx_stock_entry_items_entry_id ON stock_entry_items(entry_id)")
            db.execSQL("CREATE INDEX idx_stock_entry_items_product_id ON stock_entry_items(product_id)")
            db.execSQL("""CREATE TABLE purchases (id TEXT NOT NULL, supplier_id TEXT NOT NULL, document_number TEXT NOT NULL, invoice_number TEXT, date INTEGER NOT NULL, subtotal INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0, tax INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL, notes TEXT, status TEXT NOT NULL DEFAULT 'COMPLETED', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(supplier_id) REFERENCES suppliers(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX idx_purchases_supplier_id ON purchases(supplier_id)")
            db.execSQL("CREATE INDEX idx_purchases_date ON purchases(date)")
            db.execSQL("""CREATE TABLE purchase_items (id TEXT NOT NULL, purchase_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, unit_cost INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0, tax INTEGER NOT NULL DEFAULT 0, subtotal INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(purchase_id) REFERENCES purchases(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX idx_purchase_items_purchase_id ON purchase_items(purchase_id)")
            db.execSQL("CREATE INDEX idx_purchase_items_product_id ON purchase_items(product_id)")
            db.execSQL("""CREATE TABLE inventory_counts (id TEXT NOT NULL, started_at INTEGER NOT NULL, completed_at INTEGER, notes TEXT, status TEXT NOT NULL DEFAULT 'DRAFT', PRIMARY KEY(id))""")
            db.execSQL("""CREATE TABLE inventory_count_items (id TEXT NOT NULL, count_id TEXT NOT NULL, product_id TEXT NOT NULL, system_quantity INTEGER NOT NULL, physical_quantity INTEGER NOT NULL, difference INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(count_id) REFERENCES inventory_counts(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX idx_count_items_count_id ON inventory_count_items(count_id)")
            db.execSQL("CREATE INDEX idx_count_items_product_id ON inventory_count_items(product_id)")
            db.execSQL("CREATE UNIQUE INDEX idx_count_items_unique ON inventory_count_items(count_id, product_id)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE customers (id TEXT NOT NULL, business_name TEXT NOT NULL, owner_name TEXT, phone TEXT, whatsapp TEXT, address TEXT, reference TEXT, tax_id TEXT, visit_days TEXT, credit_limit INTEGER NOT NULL DEFAULT 0, balance INTEGER NOT NULL DEFAULT 0, latitude REAL, longitude REAL, notes TEXT, is_active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER, PRIMARY KEY(id))""")
            db.execSQL("CREATE INDEX idx_customers_balance ON customers(balance)")
            db.execSQL("CREATE INDEX idx_customers_is_active ON customers(is_active)")
            db.execSQL("""CREATE TABLE routes (id TEXT NOT NULL, name TEXT NOT NULL, code TEXT NOT NULL, description TEXT, is_active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id))""")
            db.execSQL("CREATE UNIQUE INDEX idx_routes_code ON routes(code)")
            db.execSQL("""CREATE TABLE route_customers (route_id TEXT NOT NULL, customer_id TEXT NOT NULL, visit_order INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(route_id, customer_id), FOREIGN KEY(route_id) REFERENCES routes(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(customer_id) REFERENCES customers(id) ON UPDATE CASCADE ON DELETE CASCADE)""")
            db.execSQL("CREATE UNIQUE INDEX idx_route_customers_customer_id ON route_customers(customer_id)")
            db.execSQL("CREATE UNIQUE INDEX idx_route_customers_visit_order ON route_customers(route_id, visit_order)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE route_loads (id TEXT NOT NULL, route_id TEXT NOT NULL, date INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN', notes TEXT, created_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(route_id) REFERENCES routes(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX idx_route_loads_route_id ON route_loads(route_id)")
            db.execSQL("CREATE INDEX idx_route_loads_date ON route_loads(date)")
            db.execSQL("""CREATE TABLE route_load_items (id TEXT NOT NULL, route_load_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, unit_cost_snapshot INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(route_load_id) REFERENCES route_loads(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
            db.execSQL("CREATE INDEX idx_route_load_items_load ON route_load_items(route_load_id)")
            db.execSQL("CREATE INDEX idx_route_load_items_product_id ON route_load_items(product_id)")
            db.execSQL("CREATE UNIQUE INDEX idx_route_load_items_unique ON route_load_items(route_load_id, product_id)")
        }
    }
    val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE sales (id TEXT NOT NULL, invoice_number TEXT NOT NULL, customer_id TEXT, route_id TEXT, date INTEGER NOT NULL, subtotal INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0, tax INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL, payment_type TEXT NOT NULL, paid_amount INTEGER NOT NULL DEFAULT 0, pending_amount INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'COMPLETED', notes TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(customer_id) REFERENCES customers(id) ON UPDATE CASCADE ON DELETE RESTRICT, FOREIGN KEY(route_id) REFERENCES routes(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX idx_sales_invoice_number ON sales(invoice_number)");db.execSQL("CREATE INDEX idx_sales_customer_id ON sales(customer_id)");db.execSQL("CREATE INDEX idx_sales_route_id ON sales(route_id)");db.execSQL("CREATE INDEX idx_sales_date ON sales(date)");db.execSQL("CREATE INDEX idx_sales_status ON sales(status)")
        db.execSQL("""CREATE TABLE sale_items (id TEXT NOT NULL, sale_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price INTEGER NOT NULL, unit_cost_snapshot INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0, tax INTEGER NOT NULL DEFAULT 0, subtotal INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(sale_id) REFERENCES sales(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX idx_sale_items_sale_id ON sale_items(sale_id)");db.execSQL("CREATE INDEX idx_sale_items_product_id ON sale_items(product_id)");db.execSQL("CREATE UNIQUE INDEX idx_sale_items_unique ON sale_items(sale_id,product_id)")
    }}
    val MIGRATION_6_7 = object : Migration(6,7){override fun migrate(db:SupportSQLiteDatabase){
      db.execSQL("""CREATE TABLE payments (id TEXT NOT NULL, receipt_number TEXT NOT NULL, customer_id TEXT NOT NULL, route_id TEXT, date INTEGER NOT NULL, amount INTEGER NOT NULL, payment_method TEXT NOT NULL, reference TEXT, notes TEXT, created_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(customer_id) REFERENCES customers(id) ON UPDATE CASCADE ON DELETE RESTRICT, FOREIGN KEY(route_id) REFERENCES routes(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
      db.execSQL("CREATE UNIQUE INDEX idx_payments_receipt_number ON payments(receipt_number)");db.execSQL("CREATE INDEX idx_payments_customer_id ON payments(customer_id)");db.execSQL("CREATE INDEX idx_payments_route_id ON payments(route_id)");db.execSQL("CREATE INDEX idx_payments_date ON payments(date)")
      db.execSQL("""CREATE TABLE payment_allocations (id TEXT NOT NULL, payment_id TEXT NOT NULL, sale_id TEXT NOT NULL, allocated_amount INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(payment_id) REFERENCES payments(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(sale_id) REFERENCES sales(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
      db.execSQL("CREATE INDEX idx_allocations_payment_id ON payment_allocations(payment_id)");db.execSQL("CREATE INDEX idx_allocations_sale_id ON payment_allocations(sale_id)");db.execSQL("CREATE UNIQUE INDEX idx_allocations_unique ON payment_allocations(payment_id,sale_id)")
    }}
    val MIGRATION_7_8 = object : Migration(7,8){override fun migrate(db:SupportSQLiteDatabase){
      db.execSQL("""CREATE TABLE returns (id TEXT NOT NULL, return_number TEXT NOT NULL, sale_id TEXT NOT NULL, customer_id TEXT, date INTEGER NOT NULL, total_amount INTEGER NOT NULL, reason TEXT, created_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(sale_id) REFERENCES sales(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
      db.execSQL("CREATE UNIQUE INDEX idx_returns_return_number ON returns(return_number)");db.execSQL("CREATE INDEX idx_returns_sale_id ON returns(sale_id)");db.execSQL("CREATE INDEX idx_returns_customer_id ON returns(customer_id)");db.execSQL("CREATE INDEX idx_returns_date ON returns(date)")
      db.execSQL("""CREATE TABLE return_items (id TEXT NOT NULL, return_id TEXT NOT NULL, sale_item_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, refund_price INTEGER NOT NULL, unit_cost_snapshot INTEGER NOT NULL, restock_inventory INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(id), FOREIGN KEY(return_id) REFERENCES returns(id) ON UPDATE CASCADE ON DELETE CASCADE, FOREIGN KEY(sale_item_id) REFERENCES sale_items(id) ON UPDATE CASCADE ON DELETE RESTRICT, FOREIGN KEY(product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
      db.execSQL("CREATE INDEX idx_return_items_return_id ON return_items(return_id)");db.execSQL("CREATE INDEX idx_return_items_sale_item_id ON return_items(sale_item_id)");db.execSQL("CREATE INDEX idx_return_items_product_id ON return_items(product_id)");db.execSQL("CREATE UNIQUE INDEX idx_return_items_unique ON return_items(return_id,sale_item_id)")
    }}
    val MIGRATION_8_9 = object : Migration(8,9){override fun migrate(db:SupportSQLiteDatabase){
      db.execSQL("CREATE TABLE backup_history (id TEXT NOT NULL, file_name TEXT NOT NULL, file_path TEXT NOT NULL, size_bytes INTEGER NOT NULL, type TEXT NOT NULL, status TEXT NOT NULL, sha256_hash TEXT, created_at INTEGER NOT NULL, PRIMARY KEY(id))")
      db.execSQL("CREATE INDEX idx_backup_history_created_at ON backup_history(created_at)");db.execSQL("CREATE INDEX idx_backup_history_status ON backup_history(status)")
    }}
    val MIGRATION_9_10 = object : Migration(9,10){override fun migrate(db:SupportSQLiteDatabase){
      db.execSQL("CREATE TABLE cash_sessions (id TEXT NOT NULL, opened_at INTEGER NOT NULL, closed_at INTEGER, opening_amount INTEGER NOT NULL, expected_amount INTEGER, actual_amount INTEGER, difference INTEGER, status TEXT NOT NULL, notes TEXT, PRIMARY KEY(id))")
      db.execSQL("CREATE INDEX idx_cash_sessions_status ON cash_sessions(status)");db.execSQL("CREATE INDEX idx_cash_sessions_opened_at ON cash_sessions(opened_at)")
      db.execSQL("CREATE TABLE cash_movements (id TEXT NOT NULL, session_id TEXT NOT NULL, type TEXT NOT NULL, amount INTEGER NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(session_id) REFERENCES cash_sessions(id) ON UPDATE CASCADE ON DELETE CASCADE)")
      db.execSQL("CREATE INDEX idx_cash_movements_session_id ON cash_movements(session_id)");db.execSQL("CREATE INDEX idx_cash_movements_created_at ON cash_movements(created_at)")
    }}
    val MIGRATION_10_11 = object : Migration(10,11){override fun migrate(db:SupportSQLiteDatabase){
      db.execSQL("ALTER TABLE products ADD COLUMN micatalogo_shop_id TEXT")
      db.execSQL("ALTER TABLE products ADD COLUMN micatalogo_product_id TEXT")
      db.execSQL("CREATE INDEX idx_products_micatalogo_shop_id ON products(micatalogo_shop_id)")
      db.execSQL("CREATE INDEX idx_products_micatalogo_product_id ON products(micatalogo_product_id)")
      db.execSQL("""CREATE TABLE pos_sale_outbox (sale_id TEXT NOT NULL, remote_shop_id TEXT NOT NULL, payload_json TEXT NOT NULL, state TEXT NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, last_attempt_at INTEGER, next_attempt_at INTEGER NOT NULL, last_error TEXT, remote_invoice_number TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, sent_at INTEGER, PRIMARY KEY(sale_id), FOREIGN KEY(sale_id) REFERENCES sales(id) ON UPDATE CASCADE ON DELETE CASCADE)""")
      db.execSQL("CREATE INDEX idx_pos_sale_outbox_remote_shop_id ON pos_sale_outbox(remote_shop_id)")
       db.execSQL("CREATE INDEX idx_pos_sale_outbox_due ON pos_sale_outbox(state, next_attempt_at)")
    }}
    val MIGRATION_11_12 = object : Migration(11, 12) { override fun migrate(db: SupportSQLiteDatabase) {
        // Local UUIDs remain stable; this stores the Laravel ULID after a customer is synchronized.
        db.execSQL("ALTER TABLE customers ADD COLUMN micatalogo_customer_id TEXT")
        db.execSQL("CREATE UNIQUE INDEX idx_customers_micatalogo_customer_id ON customers(micatalogo_customer_id)")
    }}
    val MIGRATION_12_13 = object : Migration(12, 13) { override fun migrate(db: SupportSQLiteDatabase) {
        // Align fresh v12 installations with the index already created by 11 -> 12.
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_micatalogo_customer_id ON customers(micatalogo_customer_id)")
    }}
    val MIGRATION_18_19 = object : Migration(18, 19) { override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE catalog_refresh (shopId TEXT NOT NULL, revision TEXT NOT NULL, error TEXT, PRIMARY KEY(shopId))")
    }}
    val MIGRATION_17_18 = object : Migration(17, 18) { override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE operation_outbox ADD COLUMN queue_sequence INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pos_sale_outbox ADD COLUMN queue_sequence INTEGER NOT NULL DEFAULT 0")
        // Legacy published installations contain only sale entries. Local pre-release
        // operations have no causal sequence; retain their previous chronological order.
        var sequence = 0L
        db.query("SELECT id, kind FROM (SELECT sale_id AS id, 0 AS kind, created_at AS stamp, rowid AS position FROM pos_sale_outbox UNION ALL SELECT id, 1 AS kind, createdAt AS stamp, rowid AS position FROM operation_outbox) ORDER BY stamp, kind, position").use { rows ->
            while (rows.moveToNext()) {
                sequence++
                val table = if (rows.getInt(1) == 0) "pos_sale_outbox" else "operation_outbox"
                val key = if (rows.getInt(1) == 0) "sale_id" else "id"
                db.execSQL("UPDATE $table SET queue_sequence=? WHERE $key=?", arrayOf(sequence, rows.getString(0)))
            }
        }
    }}
    val MIGRATION_16_17 = object : Migration(16, 17) { override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE return_items ADD COLUMN refund_total_cents INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE sale_items ADD COLUMN sale_unit_snapshot TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE sale_items ADD COLUMN volume_ml_snapshot INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE sale_items ADD COLUMN remote_source_snapshot TEXT DEFAULT NULL")
    }}
    val MIGRATION_15_16 = object : Migration(15, 16) { override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN micatalogo_internal_code TEXT")
        db.execSQL("CREATE TABLE operation_outbox (id TEXT NOT NULL, shopId TEXT NOT NULL, productIds TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL, state TEXT NOT NULL, error TEXT, PRIMARY KEY(id))")
    }}
    val MIGRATION_14_15 = object : Migration(14, 15) { override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN micatalogo_source_product_id TEXT")
        db.execSQL("ALTER TABLE products ADD COLUMN micatalogo_volume_ml INTEGER")
        db.execSQL("ALTER TABLE products ADD COLUMN micatalogo_available_ml INTEGER")
        db.execSQL("ALTER TABLE products ADD COLUMN micatalogo_sale_unit TEXT")
        db.execSQL("CREATE TABLE payment_sync_outbox (id TEXT NOT NULL, shopId TEXT NOT NULL, customerId TEXT NOT NULL, payload TEXT NOT NULL, dependencies TEXT NOT NULL, state TEXT NOT NULL, error TEXT, PRIMARY KEY(id))")
    }}
    val MIGRATION_13_14 = object : Migration(13, 14) { override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customers ADD COLUMN first_name TEXT")
        db.execSQL("ALTER TABLE customers ADD COLUMN last_name TEXT")
        db.execSQL("ALTER TABLE customers ADD COLUMN document_type TEXT")
        db.execSQL("ALTER TABLE customers ADD COLUMN document_number TEXT")
        db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT")
        db.execSQL("ALTER TABLE customers ADD COLUMN micatalogo_customer_shop_id TEXT")
    }}
}
