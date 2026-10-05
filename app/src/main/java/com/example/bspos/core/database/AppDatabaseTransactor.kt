package com.example.bspos.core.database

import androidx.room.withTransaction
import com.example.bspos.data.local.AppDatabase
import javax.inject.Inject

interface AppDatabaseTransactor {
    suspend fun <R> runInTransaction(block: suspend () -> R): R
}

class RoomDatabaseTransactor @Inject constructor(
    private val database: AppDatabase
) : AppDatabaseTransactor {
    override suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction(block)
}
