package com.example.bspos.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.database.DatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerMigrationTest {
    @Test
    fun migrationPreservesCustomerAndEnforcesUniqueRemoteId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE customers (id TEXT NOT NULL PRIMARY KEY, business_name TEXT NOT NULL, balance INTEGER NOT NULL DEFAULT 0)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO customers (id, business_name, balance) VALUES ('local-uuid', 'Cliente existente', 2500)")
            DatabaseMigrations.MIGRATION_11_12.migrate(db)
            DatabaseMigrations.MIGRATION_12_13.migrate(db)
            db.query("SELECT id, business_name, balance, micatalogo_customer_id FROM customers").use {
                assertTrue(it.moveToFirst())
                assertEquals("local-uuid", it.getString(0))
                assertEquals("Cliente existente", it.getString(1))
                assertEquals(2500L, it.getLong(2))
                assertTrue(it.isNull(3))
            }
            db.execSQL("UPDATE customers SET micatalogo_customer_id = 'remote-ulid' WHERE id = 'local-uuid'")
            var rejected = false
            try {
                db.execSQL("INSERT INTO customers (id, business_name, micatalogo_customer_id) VALUES ('another-uuid', 'Duplicado', 'remote-ulid')")
            } catch (_: SQLiteConstraintException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            helper.close()
        }
    }
}
