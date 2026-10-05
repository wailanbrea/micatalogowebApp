package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.database.DatabaseMigrations
import com.example.bspos.core.database.InventoryIntegrity
import com.example.bspos.data.repository.InventoryRepositoryImpl
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryMovement
import com.example.bspos.domain.model.InventoryMovementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * MigrationTestHelper currently crashes with Room 2.8.5 and AndroidX's strict
 * kotlinx.serialization 1.7.3. This creates the exported v1 schema verbatim,
 * then Room performs the real migration and validates its v2 schema on open.
 */
@RunWith(AndroidJUnit4::class)
class InventoryMigrationTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun publishedV15UpgradePreservesSaleIdsPayloadsAndHistoricalCosts() = runTest {
        val name = "published-v15-upgrade-${UUID.randomUUID()}.db"
        val product = UUID.randomUUID()
        val at = Instant.parse("2026-10-04T00:00:00Z")
        createV1Database(context.getDatabasePath(name), UUID.randomUUID(), UUID.randomUUID(), product, UUID.randomUUID(), at)
        val oldHelper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = error("Fixture already exists")
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        listOf(DatabaseMigrations.MIGRATION_1_2, DatabaseMigrations.MIGRATION_2_3,
                            DatabaseMigrations.MIGRATION_3_4, DatabaseMigrations.MIGRATION_4_5,
                            DatabaseMigrations.MIGRATION_5_6, DatabaseMigrations.MIGRATION_6_7,
                            DatabaseMigrations.MIGRATION_7_8, DatabaseMigrations.MIGRATION_8_9,
                            DatabaseMigrations.MIGRATION_9_10, DatabaseMigrations.MIGRATION_10_11,
                            DatabaseMigrations.MIGRATION_11_12, DatabaseMigrations.MIGRATION_12_13,
                            DatabaseMigrations.MIGRATION_13_14, DatabaseMigrations.MIGRATION_14_15).forEach { it.migrate(db) }
                    }
                }).build())
        val ids = listOf(UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"), UUID.fromString("00000000-0000-4000-8000-000000000001"))
        val bodies = ids.map { "{\"client_sale_uuid\":\"$it\",\"payment_status\":\"paid\",\"items\":[{\"product_id\":\"old-product\",\"quantity\":1,\"unit_price\":\"1.00\"}]}" }
        try {
            val old = oldHelper.writableDatabase
            ids.forEachIndexed { index, id ->
                old.execSQL("INSERT INTO sales (id,invoice_number,date,subtotal,total,payment_type,paid_amount,created_at,updated_at) VALUES (?,?,?,100,100,'CASH',100,?,?)",
                    arrayOf(id.toString(), "LEGACY-$index", at.toEpochMilli(), at.toEpochMilli(), at.toEpochMilli()))
                old.execSQL("INSERT INTO sale_items (id,sale_id,product_id,quantity,unit_price,unit_cost_snapshot,subtotal) VALUES (?,?,?,1,100,33,100)",
                    arrayOf(UUID.randomUUID().toString(), id.toString(), product.toString()))
                old.execSQL("INSERT INTO pos_sale_outbox (sale_id,remote_shop_id,payload_json,state,next_attempt_at,created_at,updated_at) VALUES (?,'legacy-shop',?,'PENDING',?,?,?)",
                    arrayOf(id.toString(), bodies[index], at.toEpochMilli(), at.toEpochMilli(), at.toEpochMilli()))
            }
            oldHelper.close()
            val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(DatabaseMigrations.MIGRATION_15_16, DatabaseMigrations.MIGRATION_16_17,
                    DatabaseMigrations.MIGRATION_17_18, DatabaseMigrations.MIGRATION_18_19).addCallback(InventoryIntegrity).build()
            try {
                val rows = upgraded.posSaleOutboxDao().findDue(Instant.now(), 100)
                assertEquals(ids, rows.map { it.saleId })
                assertEquals(bodies, rows.map { it.payloadJson })
                assertEquals(listOf(1L, 2L), rows.map { it.queueSequence })
                ids.forEach { id ->
                    val items = upgraded.saleDao().itemsInInvoiceOrder(id)
                    assertEquals(33L, items.single().unitCostSnapshot)
                    assertEquals(null, items.single().saleUnitSnapshot)
                }
                assertTrue(upgraded.operationOutboxDao().pendingRefreshes().isEmpty())
                upgraded.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            } finally { upgraded.close() }
        } finally { oldHelper.close(); context.deleteDatabase(name) }
    }

    @Test
    fun migrationPreservesV1DataValidatesV2AndAllowsAuditedInventory() = runTest {
        val name = "inventory-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        val categoryId = UUID.randomUUID()
        val unitId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val supplierId = UUID.randomUUID()
        val at = Instant.parse("2026-09-20T12:00:00.123Z")
        createV1Database(path, categoryId, unitId, productId, supplierId, at)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .addMigrations(DatabaseMigrations.MIGRATION_3_4)
            .addMigrations(DatabaseMigrations.MIGRATION_4_5)
            .addMigrations(DatabaseMigrations.MIGRATION_5_6)
            .addMigrations(DatabaseMigrations.MIGRATION_6_7)
            .addMigrations(DatabaseMigrations.MIGRATION_7_8)
            .addMigrations(DatabaseMigrations.MIGRATION_8_9)
            .addMigrations(DatabaseMigrations.MIGRATION_9_10)
            .addMigrations(DatabaseMigrations.MIGRATION_10_11)
            .addMigrations(DatabaseMigrations.MIGRATION_11_12)
            .addMigrations(DatabaseMigrations.MIGRATION_12_13)
            .addMigrations(DatabaseMigrations.MIGRATION_13_14)
            .addMigrations(DatabaseMigrations.MIGRATION_14_15)
            .addMigrations(DatabaseMigrations.MIGRATION_15_16)
            .addMigrations(DatabaseMigrations.MIGRATION_16_17)
            .addMigrations(DatabaseMigrations.MIGRATION_17_18)
            .addMigrations(DatabaseMigrations.MIGRATION_18_19)
            .addCallback(InventoryIntegrity)
            .build()
        try {
            val category = db.categoryDao().findById(categoryId)!!
            assertEquals("Bebidas", category.name)
            assertEquals("Archivo", category.description)
            assertEquals("drink", category.icon)
            assertEquals(7, category.sortOrder)
            assertFalse(category.isActive)
            assertEquals(at, category.deletedAt)
            assertEquals(at, category.createdAt)
            assertTrue(db.categoryDao().observeAll().first().isEmpty())
            assertEquals("ud", db.unitOfMeasureDao().findById(unitId)!!.abbreviation)
            val product = db.productDao().findById(productId)!!
            assertEquals(categoryId, product.categoryId)
            assertEquals(unitId, product.unitId)
            assertEquals(Long.MAX_VALUE, product.salePrice)
            assertEquals(2500L, product.wholesalePrice)
            assertEquals(2000L, product.averageCost)
            assertEquals(2100L, product.lastPurchaseCost)
            assertEquals(5L, product.minimumStock)
            assertEquals("products/water.jpg", product.imagePath)
            assertEquals("products/water-thumb.jpg", product.thumbnailPath)
            assertTrue(product.tracksExpiration)
            assertEquals(at, product.updatedAt)
            val supplier = db.supplierDao().findById(supplierId)!!
            assertEquals("Ana", supplier.contactName)
            assertEquals("123456789", supplier.taxId)
            assertEquals("Conservar", supplier.notes)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            val inventory = InventoryRepositoryImpl(InventoryLocalDataSource(db.inventoryDao(), db.inventoryAdjustmentReasonDao()),
                db.productDao(), db.inventoryDao(), com.example.bspos.data.micatalogo.RemoteMutationRecorder(
                    com.example.bspos.core.database.RoomDatabaseTransactor(db), db.categoryDao(), db.operationOutboxDao(),
                    com.example.bspos.data.micatalogo.PosSaleSyncScheduler(androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>())))
            assertTrue(inventory.observeStock(InventoryLocation.MAIN).first().isEmpty())
            val initial = InventoryMovement(
                id = UUID.randomUUID(), productId = productId, location = InventoryLocation.MAIN,
                type = InventoryMovementType.INITIAL, quantity = 5, previousQuantity = 0, newQuantity = 5,
                unitCost = 2000, totalCost = 10000, createdAt = at
            )
            inventory.recordMovements(listOf(initial))
            assertEquals(5L, inventory.findStock(productId, InventoryLocation.MAIN)!!.quantity)
            expectFailure<SQLiteConstraintException> {
                db.openHelper.writableDatabase.execSQL("UPDATE inventory_movements SET quantity = 9")
            }
            assertEquals(initial, inventory.findMovement(initial.id))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun createV1Database(
        file: File, categoryId: UUID, unitId: UUID, productId: UUID, supplierId: UUID, at: Instant
    ) {
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("""CREATE TABLE categories (id TEXT NOT NULL, name TEXT NOT NULL, description TEXT, icon TEXT NOT NULL DEFAULT 'category_default', sort_order INTEGER NOT NULL DEFAULT 0, is_active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER, PRIMARY KEY(id))""")
            db.execSQL("CREATE INDEX idx_categories_is_active ON categories(is_active)")
            db.execSQL("""CREATE TABLE units_of_measure (id TEXT NOT NULL, name TEXT NOT NULL, abbreviation TEXT NOT NULL, is_active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER, PRIMARY KEY(id))""")
            db.execSQL("""CREATE TABLE products (id TEXT NOT NULL, name TEXT NOT NULL, internal_code TEXT NOT NULL, barcode TEXT, category_id TEXT NOT NULL, unit_id TEXT NOT NULL, description TEXT, sale_price INTEGER NOT NULL, wholesale_price INTEGER, average_cost INTEGER NOT NULL DEFAULT 0, last_purchase_cost INTEGER NOT NULL DEFAULT 0, minimum_stock INTEGER NOT NULL DEFAULT 0, image_path TEXT, thumbnail_path TEXT, is_active INTEGER NOT NULL DEFAULT 1, tracks_expiration INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER, PRIMARY KEY(id), FOREIGN KEY(category_id) REFERENCES categories(id) ON UPDATE CASCADE ON DELETE RESTRICT, FOREIGN KEY(unit_id) REFERENCES units_of_measure(id) ON UPDATE CASCADE ON DELETE RESTRICT)""")
            db.execSQL("CREATE UNIQUE INDEX idx_products_internal_code ON products(internal_code)")
            db.execSQL("CREATE INDEX idx_products_barcode ON products(barcode)")
            db.execSQL("CREATE INDEX idx_products_category_id ON products(category_id)")
            db.execSQL("CREATE INDEX idx_products_unit_id ON products(unit_id)")
            db.execSQL("CREATE INDEX idx_products_is_active ON products(is_active)")
            db.execSQL("""CREATE TABLE suppliers (id TEXT NOT NULL, name TEXT NOT NULL, contact_name TEXT, phone TEXT, email TEXT, address TEXT, tax_id TEXT, notes TEXT, is_active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER, PRIMARY KEY(id))""")
            db.execSQL("CREATE INDEX idx_suppliers_tax_id ON suppliers(tax_id)")
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES(42, '7b06b9209bdb2e0e8168b6fffeeca617')")
            db.version = 1
            db.execSQL("INSERT INTO categories (id, name, description, icon, sort_order, is_active, created_at, updated_at, deleted_at) VALUES (?, 'Bebidas', 'Archivo', 'drink', 7, 0, ?, ?, ?)", arrayOf<Any>(categoryId.toString(), at.toEpochMilli(), at.toEpochMilli(), at.toEpochMilli()))
            db.execSQL("INSERT INTO units_of_measure (id, name, abbreviation, is_active, created_at, updated_at) VALUES (?, 'Unidad', 'ud', 1, ?, ?)", arrayOf<Any>(unitId.toString(), at.toEpochMilli(), at.toEpochMilli()))
            db.execSQL("""INSERT INTO products (id, name, internal_code, barcode, category_id, unit_id, sale_price, wholesale_price, average_cost, last_purchase_cost, minimum_stock, image_path, thumbnail_path, is_active, tracks_expiration, created_at, updated_at) VALUES (?, 'Agua', 'AG-001', '123', ?, ?, ?, 2500, 2000, 2100, 5, 'products/water.jpg', 'products/water-thumb.jpg', 1, 1, ?, ?)""", arrayOf<Any>(productId.toString(), categoryId.toString(), unitId.toString(), Long.MAX_VALUE, at.toEpochMilli(), at.toEpochMilli()))
            db.execSQL("INSERT INTO suppliers (id, name, contact_name, phone, email, address, tax_id, notes, created_at, updated_at) VALUES (?, 'Proveedor', 'Ana', '8095550100', 'ana@example.test', 'SD', '123456789', 'Conservar', ?, ?)", arrayOf<Any>(supplierId.toString(), at.toEpochMilli(), at.toEpochMilli()))
        }
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try { block() } catch (failure: Throwable) {
            if (failure is T) return
            throw failure
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
