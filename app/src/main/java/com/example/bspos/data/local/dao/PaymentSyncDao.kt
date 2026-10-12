package com.example.bspos.data.local.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.OnConflictStrategy
import com.example.bspos.data.local.entity.PaymentSyncEntity

@Dao interface PaymentSyncDao {
    @Query("SELECT * FROM payment_sync_outbox WHERE state IN ('PENDING','BLOCKED') ORDER BY rowid")
    fun observeOutstanding(): kotlinx.coroutines.flow.Flow<List<PaymentSyncEntity>>
    @Query("UPDATE payment_sync_outbox SET state='PENDING', error=NULL WHERE id=:id AND state='BLOCKED'")
    suspend fun retryBlocked(id:String):Int
    @Insert suspend fun insert(row: PaymentSyncEntity)
    @Query("SELECT * FROM payment_sync_outbox WHERE state = 'PENDING' ORDER BY rowid LIMIT 100")
    suspend fun pending(): List<PaymentSyncEntity>
    @Query("UPDATE payment_sync_outbox SET state = :state, error = :error WHERE id = :id AND state='PENDING'")
    suspend fun mark(id: String, state: String, error: String? = null): Int
    @Query("SELECT * FROM payment_sync_outbox WHERE id=:id") suspend fun find(id: String): PaymentSyncEntity?
    @Query("SELECT * FROM payment_sync_outbox WHERE shopId=:shop AND customerId=:customer AND state IN ('PENDING','BLOCKED') ORDER BY rowid")
    suspend fun activeForCustomer(shop: String, customer: String): List<PaymentSyncEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun requestRefresh(row: com.example.bspos.data.local.entity.CatalogRefreshEntity)
    @Query("UPDATE payment_sync_outbox SET state='SENT',error=NULL,server_response=:receipt WHERE id=:id AND state='PENDING'")
    suspend fun markConfirmed(id: String, receipt: String): Int
    @Transaction suspend fun confirmSent(row: PaymentSyncEntity, receipt: String): Int {
        val changed = markConfirmed(row.id, receipt)
        if (changed == 1) requestRefresh(com.example.bspos.data.local.entity.CatalogRefreshEntity(row.shopId, java.util.UUID.randomUUID().toString()))
        return changed
    }
    @Query("SELECT COUNT(*) FROM payment_sync_outbox WHERE shopId = :shopId AND state != 'SENT'")
    suspend fun unsentCount(shopId: String): Int
    @Query("SELECT COUNT(*) FROM payment_sync_outbox WHERE state = 'BLOCKED'")
    suspend fun blockedCount(): Int
}
