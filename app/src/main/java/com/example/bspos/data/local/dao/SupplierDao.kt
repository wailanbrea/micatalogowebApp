package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.bspos.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE deleted_at IS NULL ORDER BY name, id")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun findById(id: UUID): SupplierEntity?

    @Insert
    suspend fun insert(supplier: SupplierEntity)

    @Update
    suspend fun update(supplier: SupplierEntity): Int

    @Query("UPDATE suppliers SET deleted_at = :at, updated_at = :at WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDelete(id: UUID, at: Instant): Int
}
