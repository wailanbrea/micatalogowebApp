package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface UnitOfMeasureDao {
    @Query("SELECT * FROM units_of_measure WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL ORDER BY name, id")
    fun observeAll(shopId: String? = null): Flow<List<UnitOfMeasureEntity>>

    @Query("SELECT * FROM units_of_measure WHERE id = :id AND remote_shop_id=:shopId AND tenancy_status='assigned'")
    suspend fun findById(id: UUID, shopId: String? = null): UnitOfMeasureEntity?

    @Query("SELECT * FROM units_of_measure WHERE id=:id AND (remote_shop_id=:shopId OR remote_shop_id IS NULL)")
    suspend fun findForReconciliation(id: UUID, shopId: String): UnitOfMeasureEntity?

    @Query("SELECT * FROM units_of_measure WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL")
    suspend fun findForShop(shopId: String): List<UnitOfMeasureEntity>

    @Insert
    suspend fun insert(unit: UnitOfMeasureEntity)

    @Update
    suspend fun update(unit: UnitOfMeasureEntity): Int

    @Query("UPDATE units_of_measure SET deleted_at = :at, updated_at = :at WHERE id = :id AND remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL")
    suspend fun softDelete(id: UUID, at: Instant, shopId: String? = null): Int
}
