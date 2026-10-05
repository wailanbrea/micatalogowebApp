package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.bspos.data.local.entity.InventoryAdjustmentReasonEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface InventoryAdjustmentReasonDao {
    @Query("SELECT * FROM inventory_adjustment_reasons WHERE deleted_at IS NULL ORDER BY name, id")
    fun observeAll(): Flow<List<InventoryAdjustmentReasonEntity>>

    @Query("SELECT * FROM inventory_adjustment_reasons WHERE id = :id")
    suspend fun findById(id: UUID): InventoryAdjustmentReasonEntity?

    @Insert
    suspend fun insert(reason: InventoryAdjustmentReasonEntity)

    @Update
    suspend fun update(reason: InventoryAdjustmentReasonEntity): Int

    @Query("UPDATE inventory_adjustment_reasons SET deleted_at = :at, updated_at = :at WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDelete(id: UUID, at: Instant): Int
}
