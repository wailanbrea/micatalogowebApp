package com.example.bspos.data.local.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.bspos.data.local.entity.PaymentSyncEntity

@Dao interface PaymentSyncDao {
    @Query("SELECT * FROM payment_sync_outbox WHERE state IN ('PENDING','BLOCKED') ORDER BY rowid")
    fun observeOutstanding(): kotlinx.coroutines.flow.Flow<List<PaymentSyncEntity>>
    @Query("UPDATE payment_sync_outbox SET state='PENDING', error=NULL WHERE id=:id AND state='BLOCKED'")
    suspend fun retryBlocked(id:String):Int
    @Insert suspend fun insert(row: PaymentSyncEntity)
    @Query("SELECT * FROM payment_sync_outbox WHERE state = 'PENDING' ORDER BY rowid LIMIT 100")
    suspend fun pending(): List<PaymentSyncEntity>
    @Query("UPDATE payment_sync_outbox SET state = :state, error = :error WHERE id = :id")
    suspend fun mark(id: String, state: String, error: String? = null)
    @Query("SELECT COUNT(*) FROM payment_sync_outbox WHERE shopId = :shopId AND state != 'SENT'")
    suspend fun unsentCount(shopId: String): Int
    @Query("SELECT COUNT(*) FROM payment_sync_outbox WHERE state = 'BLOCKED'")
    suspend fun blockedCount(): Int
}
