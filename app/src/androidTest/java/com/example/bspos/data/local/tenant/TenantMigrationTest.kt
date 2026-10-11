package com.example.bspos.data.local.tenant

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.database.DatabaseMigrations
import com.example.bspos.core.database.InventoryIntegrity
import com.example.bspos.core.database.TenantIntegrity
import com.example.bspos.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses the shipped schemas, real SQLite and Room's full schema validator.
 * All files are unique test fixtures; the operational AppDatabase.NAME is never opened.
 */
@RunWith(AndroidJUnit4::class)
class TenantMigrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val a = "qa-shop-a"
    private val b = "qa-shop-b"
    private val categoryA = UUID.randomUUID().toString()
    private val categoryB = UUID.randomUUID().toString()
    private val unitA = UUID.randomUUID().toString()
    private val unitB = UUID.randomUUID().toString()
    private val productA = UUID.randomUUID().toString()
    private val productB = UUID.randomUUID().toString()
    private val customerA = UUID.randomUUID().toString()
    private val customerB = UUID.randomUUID().toString()
    private val saleA = UUID.randomUUID().toString()
    private val saleB = UUID.randomUUID().toString()
    private val mixedSale = UUID.randomUUID().toString()
    private val legacySale = UUID.randomUUID().toString()

    @Test
    fun migrationPreservesEveryOldColumnAndSeparatesTwoShops() = runTest {
        val fixture = createFixture(26)
        val original = fingerprint(fixture)
        fixture.close()
        val db = openMigrated(fixture.name, DatabaseMigrations.MIGRATION_26_27)
        try {
            val sql = db.openHelper.writableDatabase // Room validates the complete v27 schema here.
            assertEquals(original, fingerprint(sql, schema(26)))
            assertEquals(listOf(productA), db.productDao().observeForShop(a).first().map { it.id.toString() })
            assertEquals(listOf(productB), db.productDao().observeForShop(b).first().map { it.id.toString() })
            assertNull(db.productDao().findById(UUID.fromString(productB), a))
            assertTrue(db.productDao().observeAll().first().isEmpty())
            val inactive = db.customerRouteDao().observeCustomers(a).first().single()
            assertEquals(customerA, inactive.id.toString())
            assertFalse(inactive.isActive)
            assertTrue(db.customerRouteDao().findActiveCustomers(a).isEmpty())
            assertEquals(listOf(saleA), db.saleDao().observeAll(a).first().map { it.id.toString() })
            assertEquals(listOf(saleB), db.saleDao().observeAll(b).first().map { it.id.toString() })
            assertNull(db.saleDao().findById(UUID.fromString(mixedSale), a))
            assertEquals("legacy_ambiguous", status(sql, "sales", mixedSale))
            assertEquals("legacy_ambiguous", status(sql, "sales", legacySale))
            assertEquals("legacy_ambiguous", status(sql, "sale_items", "mixed-line"))
            assertNull(db.cashSessionDao().findOpenId(a)) // A historical local box isn't a remote open box.
            sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }

    @Test
    fun contradictoryOutboxAndSharedParentsStayQuarantined() = runTest {
        val fixture = createFixture(26)
        val conflict = UUID.randomUUID().toString()
        fixture.insert("customers", mapOf("id" to conflict, "business_name" to "Conflicto", "micatalogo_customer_shop_id" to a))
        fixture.insert("operation_outbox", mapOf("id" to "customer-conflict", "shopId" to b,
            "payload" to """{"type":"customer_upsert","client_customer_uuid":"$conflict"}"""))
        val duplicate = UUID.randomUUID().toString()
        fixture.insert("products", productValues(duplicate, a, categoryA, unitA, "duplicate-code", "remote-a"))
        val sharedCategory = UUID.randomUUID().toString()
        fixture.insert("categories", mapOf("id" to sharedCategory))
        fixture.db.execSQL("UPDATE products SET category_id=? WHERE id IN (?,?)", arrayOf(sharedCategory, productA, productB))
        val before = fingerprint(fixture)
        fixture.close()
        val db = openMigrated(fixture.name, DatabaseMigrations.MIGRATION_26_27)
        try {
            val sql = db.openHelper.writableDatabase
            assertEquals(before, fingerprint(sql, schema(26)))
            assertEquals("legacy_ambiguous", status(sql, "customers", conflict))
            assertEquals("legacy_ambiguous", status(sql, "products", productA))
            assertEquals("legacy_ambiguous", status(sql, "products", duplicate))
            assertEquals("legacy_ambiguous", status(sql, "categories", sharedCategory))
            assertEquals("legacy_ambiguous", status(sql, "products", productB))
            assertTrue(db.productDao().observeForShop(a).first().isEmpty())
            assertTrue(db.productDao().observeForShop(b).first().isEmpty())
        } finally { db.close() }
    }

    @Test
    fun tenantTriggersRejectCrossShopRelationsAndPreserveInventoryImmutability() = runTest {
        val fixture = createFixture(26)
        fixture.close()
        val db = openMigrated(fixture.name, DatabaseMigrations.MIGRATION_26_27)
        try {
            val sql = db.openHelper.writableDatabase
            reject {
                sql.execSQL("UPDATE products SET remote_shop_id=? WHERE id=?", arrayOf(b, productA))
            }
            reject {
                sql.execSQL("UPDATE products SET category_id=? WHERE id=?", arrayOf(categoryB, productA))
            }
            reject {
                sql.execSQL("INSERT INTO sale_items(id,sale_id,product_id,quantity,unit_price,unit_cost_snapshot,subtotal,remote_shop_id,tenancy_status) VALUES ('cross-line',?,?,1,100,33,100,?,'assigned')", arrayOf(saleA, productB, a))
            }
            reject { sql.execSQL("UPDATE inventory_movements SET quantity=99 WHERE id='movement-a'") }
            reject { sql.execSQL("DELETE FROM inventory_movements WHERE id='movement-a'") }
            assertEquals("assigned", status(sql, "products", productA))
            assertEquals(1L, number(sql, "SELECT COUNT(*) FROM inventory_movements WHERE id='movement-a'"))
            assertEquals(0L, number(sql, "SELECT COUNT(*) FROM sale_items WHERE id='cross-line'"))
        } finally { db.close() }
    }

    @Test
    fun publishedV15AndEarliestSchemaMigrateWithoutChangingHistoricalValues() = runTest {
        for (version in listOf(1, 15)) {
            val fixture = createFixture(version, populate = false)
            fixture.insert("categories", mapOf("id" to categoryA, "name" to "Histórico", "is_active" to 0))
            fixture.insert("units_of_measure", mapOf("id" to unitA))
            val values = mutableMapOf<String, Any?>("id" to productA, "name" to "Conservar", "internal_code" to "old-code",
                "category_id" to categoryA, "unit_id" to unitA, "sale_price" to Long.MAX_VALUE,
                "average_cost" to 4321L, "created_at" to 123456789L, "updated_at" to 987654321L)
            fixture.insert("products", values)
            val before = fingerprint(fixture)
            fixture.close()
            val migrations = DatabaseMigrations.all().filter { it.startVersion >= version }.toTypedArray()
            val db = openMigrated(fixture.name, *migrations)
            try {
                val sql = db.openHelper.writableDatabase
                assertEquals(before, fingerprint(sql, schema(version)))
                assertEquals("legacy_ambiguous", status(sql, "products", productA))
                assertNull(db.productDao().findById(UUID.fromString(productA), a))
                sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            } finally { db.close() }
        }
    }

    private fun schema(version: Int): JSONObject = JSONObject(instrumentation.context.assets
        .open("com.example.bspos.data.local.AppDatabase/$version.json").bufferedReader().use { it.readText() }).getJSONObject("database")

    private inner class Fixture(val name: String, val schema: JSONObject, val db: SQLiteDatabase) {
        fun insert(table: String, values: Map<String, Any?>) {
            val entities = schema.getJSONArray("entities")
            val entity = (0 until entities.length()).map { entities.getJSONObject(it) }.single { it.getString("tableName") == table }
            val fields = entity.getJSONArray("fields")
            val row = linkedMapOf<String, Any?>()
            for (i in 0 until fields.length()) {
                val f = fields.getJSONObject(i)
                val column = f.getString("columnName")
                if (column in values) row[column] = values[column]
                else if (f.optBoolean("notNull") && !f.has("defaultValue")) {
                    row[column] = if (f.getString("affinity") == "TEXT") "fixture-$column" else 0L
                }
            }
            db.execSQL("INSERT INTO `$table` (${row.keys.joinToString { "`$it`" }}) VALUES (${row.keys.joinToString { "?" }})", row.values.toTypedArray())
        }
        fun close() = db.close()
    }

    private fun createFixture(version: Int, populate: Boolean = true): Fixture {
        val schema = schema(version)
        val name = "tenant-migration-test-v$version-${UUID.randomUUID()}.db"
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        if (version>=2) {
            db.execSQL("CREATE TRIGGER inventory_movements_no_update BEFORE UPDATE ON inventory_movements BEGIN SELECT RAISE(ABORT,'Inventory movements are immutable'); END")
            db.execSQL("CREATE TRIGGER inventory_movements_no_delete BEFORE DELETE ON inventory_movements BEGIN SELECT RAISE(ABORT,'Inventory movements are immutable'); END")
        }
        val fixture = Fixture(name, schema, db)
        if (!populate) return fixture
        fixture.insert("categories", mapOf("id" to categoryA))
        fixture.insert("categories", mapOf("id" to categoryB))
        fixture.insert("units_of_measure", mapOf("id" to unitA))
        fixture.insert("units_of_measure", mapOf("id" to unitB))
        fixture.insert("products", productValues(productA, a, categoryA, unitA, "code-a", "remote-a"))
        fixture.insert("products", productValues(productB, b, categoryB, unitB, "code-b", "remote-b"))
        fixture.insert("customers", mapOf("id" to customerA, "business_name" to "Cliente A", "is_active" to 0, "micatalogo_customer_shop_id" to a))
        fixture.insert("customers", mapOf("id" to customerB, "business_name" to "Cliente B", "micatalogo_customer_shop_id" to b))
        for ((sale, customer) in listOf(saleA to customerA, saleB to customerB, mixedSale to customerA, legacySale to null)) {
            fixture.insert("sales", mapOf("id" to sale, "invoice_number" to "invoice-$sale", "customer_id" to customer,
                "payment_type" to "CASH", "subtotal" to 100L, "total" to 100L, "paid_amount" to 100L))
        }
        for ((id, sale, product) in listOf(Triple("line-a", saleA, productA), Triple("line-b", saleB, productB), Triple("mixed-line", mixedSale, productB))) {
            fixture.insert("sale_items", mapOf("id" to id, "sale_id" to sale, "product_id" to product,
                "quantity" to 1L, "unit_price" to 100L, "unit_cost_snapshot" to 33L, "subtotal" to 100L))
        }
        fixture.insert("pos_sale_outbox", mapOf("sale_id" to mixedSale, "remote_shop_id" to a, "payload_json" to "{\"immutable\":true}"))
        fixture.insert("operation_outbox", mapOf("id" to "malformed-json-types", "shopId" to a,
            "payload" to """{"type":[],"local_cash_session_uuid":{}}"""))
        fixture.insert("cash_sessions", mapOf("id" to UUID.randomUUID().toString()))
        fixture.insert("inventory_movements", mapOf("id" to "movement-a", "product_id" to productA, "location_type" to "MAIN",
            "location_id" to "MAIN", "movement_type" to "INITIAL", "quantity" to 1L, "previous_quantity" to 0L,
            "new_quantity" to 1L, "unit_cost" to 33L, "total_cost" to 33L))
        return fixture
    }

    private fun productValues(id: String, shop: String, category: String, unit: String, code: String, remote: String) =
        mapOf("id" to id, "name" to "Producto $code", "internal_code" to code, "category_id" to category, "unit_id" to unit,
            "sale_price" to 100L, "micatalogo_shop_id" to shop, "micatalogo_product_id" to remote)

    private fun openMigrated(name: String, vararg migrations: androidx.room.migration.Migration) =
        Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(*migrations)
            .addCallback(InventoryIntegrity).addCallback(TenantIntegrity).build()

    private fun fingerprint(fixture: Fixture) = fingerprint(fixture.schema) { query -> fixture.db.rawQuery(query, null) }
    private fun fingerprint(db: SupportSQLiteDatabase, schema: JSONObject) = fingerprint(schema) { db.query(it) }
    private fun fingerprint(schema: JSONObject, query: (String) -> android.database.Cursor): Map<String, List<List<String?>>> {
        val entities = schema.getJSONArray("entities")
        return (0 until entities.length()).associate { i ->
            val e = entities.getJSONObject(i)
            val fields = e.getJSONArray("fields")
            val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
            e.getString("tableName") to query("SELECT ${columns.joinToString { "`$it`" }} FROM `${e.getString("tableName")}` ORDER BY rowid").use { c ->
                buildList { while (c.moveToNext()) add(columns.indices.map { if (c.isNull(it)) null else c.getString(it) }) }
            }
        }
    }

    private fun status(db: SupportSQLiteDatabase, table: String, id: String) = db.query("SELECT tenancy_status FROM `$table` WHERE id=?", arrayOf(id)).use { c ->
        check(c.moveToFirst()); c.getString(0)
    }
    private fun number(db: SupportSQLiteDatabase, query: String) = db.query(query).use { c -> c.moveToFirst(); c.getLong(0) }
    private fun reject(block: () -> Unit) {
        try { block(); fail("Expected SQLite to reject a cross-shop/immutable write") }
        catch (_: SQLiteConstraintException) { /* Expected: the rejected write is rolled back. */ }
    }
}
