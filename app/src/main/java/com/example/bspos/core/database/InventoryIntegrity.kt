package com.example.bspos.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Installed for both fresh databases and migrated v1 databases. */
object InventoryIntegrity : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = install(db)

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS inventory_movements_no_update
            BEFORE UPDATE ON inventory_movements
            BEGIN SELECT RAISE(ABORT, 'Inventory movements are immutable'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS inventory_movements_no_delete
            BEFORE DELETE ON inventory_movements
            BEGIN SELECT RAISE(ABORT, 'Inventory movements are immutable'); END
        """.trimIndent())
    }
}
