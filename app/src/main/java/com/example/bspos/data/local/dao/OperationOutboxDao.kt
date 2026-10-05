package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.OnConflictStrategy
import com.example.bspos.data.local.entity.CatalogRefreshEntity
import com.example.bspos.data.local.entity.OperationOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun requestRefresh(row: CatalogRefreshEntity)
    @Query("SELECT * FROM catalog_refresh ORDER BY shopId") suspend fun pendingRefreshes(): List<CatalogRefreshEntity>
    @Query("SELECT * FROM catalog_refresh ORDER BY shopId") fun observeRefreshes(): Flow<List<CatalogRefreshEntity>>
    @Query("DELETE FROM catalog_refresh WHERE shopId=:shop AND revision=:revision") suspend fun clearRefresh(shop: String, revision: String): Int
    @Query("UPDATE catalog_refresh SET error=:error WHERE shopId=:shop AND revision=:revision") suspend fun refreshError(shop: String, revision: String, error: String): Int
    @Transaction suspend fun confirmSent(id: String, shop: String): Int {
        val changed = mark(id, "SENT")
        if (changed == 1) requestRefresh(CatalogRefreshEntity(shop, java.util.UUID.randomUUID().toString()))
        return changed
    }
    @Insert suspend fun insertSequenced(value: OperationOutboxEntity)
    @Query("SELECT COALESCE(MAX(sequence),0)+1 FROM (SELECT queue_sequence AS sequence FROM operation_outbox UNION ALL SELECT queue_sequence AS sequence FROM pos_sale_outbox)")
    suspend fun nextSequence(): Long
    @Transaction suspend fun insert(value: OperationOutboxEntity) {
        insertSequenced(value.copy(queueSequence = nextSequence()))
    }
    @Query("SELECT * FROM operation_outbox WHERE shopId = :shopId AND state IN ('PENDING','BLOCKED') ORDER BY queue_sequence LIMIT 1")
    suspend fun oldestActive(shopId: String): OperationOutboxEntity?
    @Query("SELECT DISTINCT shopId FROM operation_outbox WHERE state IN ('PENDING','BLOCKED')")
    suspend fun activeShopIds(): List<String>
    @Query("SELECT * FROM operation_outbox WHERE shopId = :shopId AND state IN ('PENDING','BLOCKED') ORDER BY queue_sequence")
    suspend fun activeForShop(shopId: String): List<OperationOutboxEntity>
    @Query("SELECT * FROM operation_outbox WHERE shopId=:shopId AND queue_sequence>:sequence")
    suspend fun eventsAfter(shopId: String, sequence: Long): List<OperationOutboxEntity>
    @Query("SELECT * FROM operation_outbox WHERE state IN ('PENDING','BLOCKED') ORDER BY queue_sequence")
    fun observeOutstanding(): Flow<List<OperationOutboxEntity>>
    @Query("UPDATE operation_outbox SET state = :state, error = :error WHERE id = :id AND state = 'PENDING'")
    suspend fun mark(id: String, state: String, error: String? = null): Int
    @Query("UPDATE operation_outbox SET state = 'PENDING', error = NULL WHERE id = :id AND state = 'BLOCKED'")
    suspend fun retryBlocked(id: String): Int
}
