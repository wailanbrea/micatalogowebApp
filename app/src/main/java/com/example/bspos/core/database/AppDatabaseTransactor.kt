package com.example.bspos.core.database

import androidx.room.withTransaction
import com.example.bspos.data.local.AppDatabase
import javax.inject.Inject

interface AppDatabaseTransactor {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

class RoomDatabaseTransactor @Inject constructor(
    private val database: AppDatabase,
    private val shops: ShopScope? = null
) : AppDatabaseTransactor {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        if (shops == null) database.withTransaction(block)
        else shops.mutate { database.withTransaction(block) }
}
