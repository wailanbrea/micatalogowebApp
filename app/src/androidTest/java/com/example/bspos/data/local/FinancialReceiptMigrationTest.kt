package com.example.bspos.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.database.DatabaseMigrations
import org.junit.Assert.*
import org.junit.Test

class FinancialReceiptMigrationTest {
    @Test fun migrationKeepsImmutableOldRowsAndAddsNullableReceipts() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(
            InstrumentationRegistry.getInstrumentation().targetContext).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (table in listOf("payment_sync_outbox", "operation_outbox")) {
                        db.execSQL("CREATE TABLE $table (id TEXT PRIMARY KEY, payload TEXT, state TEXT)")
                        db.execSQL("INSERT INTO $table VALUES ('old-id','immutable','PENDING')")
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            DatabaseMigrations.MIGRATION_20_21.migrate(helper.writableDatabase)
            for (table in listOf("payment_sync_outbox", "operation_outbox")) {
                helper.writableDatabase.query("SELECT id,payload,state,server_response FROM $table").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("old-id", it.getString(0))
                    assertEquals("immutable", it.getString(1))
                    assertEquals("PENDING", it.getString(2))
                    assertTrue(it.isNull(3))
                }
            }
        } finally { helper.close() }
    }
}
