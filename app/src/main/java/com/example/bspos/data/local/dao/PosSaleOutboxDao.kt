package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.OnConflictStrategy
import com.example.bspos.data.local.entity.CatalogRefreshEntity
import com.example.bspos.data.local.entity.PosSaleOutboxEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface PosSaleOutboxDao {
    @Insert
    suspend fun insertSequenced(outbox: PosSaleOutboxEntity)

    @Query("SELECT COALESCE(MAX(sequence),0)+1 FROM (SELECT queue_sequence AS sequence FROM operation_outbox UNION ALL SELECT queue_sequence AS sequence FROM pos_sale_outbox)")
    suspend fun nextSequence(): Long

    @Transaction suspend fun insert(outbox: PosSaleOutboxEntity) {
        insertSequenced(outbox.copy(queueSequence = nextSequence()))
    }

    @Query("SELECT * FROM pos_sale_outbox WHERE state IN ('PENDING', 'RETRY') AND next_attempt_at <= :now ORDER BY queue_sequence LIMIT :limit")
    suspend fun findDue(now: Instant, limit: Int): List<PosSaleOutboxEntity>

    @Query("SELECT * FROM pos_sale_outbox WHERE remote_shop_id = :shopId AND state IN ('PENDING', 'RETRY', 'BLOCKED')")
    suspend fun findActiveForShop(shopId: String): List<PosSaleOutboxEntity>
    @Query("SELECT * FROM pos_sale_outbox WHERE remote_shop_id=:shopId AND queue_sequence>:sequence")
    suspend fun eventsAfter(shopId: String, sequence: Long): List<PosSaleOutboxEntity>

    @Query("SELECT DISTINCT remote_shop_id FROM pos_sale_outbox WHERE state IN ('PENDING','RETRY','BLOCKED')")
    suspend fun activeShopIds(): List<String>

    @Query("SELECT * FROM pos_sale_outbox WHERE state IN ('PENDING','RETRY','BLOCKED') ORDER BY created_at")
    fun observeOutstanding(): Flow<List<PosSaleOutboxEntity>>

    @Query("UPDATE pos_sale_outbox SET state='PENDING', next_attempt_at=:at, last_error=NULL, updated_at=:at WHERE sale_id=:id AND state='BLOCKED'")
    suspend fun retryBlocked(id: UUID, at: Instant): Int

    @Query("SELECT state FROM pos_sale_outbox WHERE sale_id = :saleId")
    suspend fun stateForSale(saleId: UUID): String?

    @Query("SELECT remote_shop_id FROM pos_sale_outbox WHERE sale_id = :saleId")
    suspend fun shopForSale(saleId: UUID): String?

    @Query("UPDATE pos_sale_outbox SET payload_json = :payload WHERE sale_id = :saleId AND state IN ('PENDING', 'RETRY')")
    suspend fun freezePayload(saleId: UUID, payload: String): Int

    @Query("UPDATE pos_sale_outbox SET state = 'SENT', attempt_count = attempt_count + 1, last_attempt_at = :at, last_error = NULL, remote_invoice_number = :invoiceNumber, updated_at = :at, sent_at = :at WHERE sale_id = :saleId AND state IN ('PENDING', 'RETRY')")
    suspend fun markSentRow(saleId: UUID, invoiceNumber: String?, at: Instant): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun requestRefresh(row: CatalogRefreshEntity)
    @Transaction suspend fun markSent(saleId: UUID, invoiceNumber: String?, at: Instant): Int {
        val shop = shopForSale(saleId) ?: return 0
        val changed = markSentRow(saleId, invoiceNumber, at)
        if (changed == 1) requestRefresh(CatalogRefreshEntity(shop, UUID.randomUUID().toString()))
        return changed
    }

    @Query("UPDATE pos_sale_outbox SET state = 'RETRY', attempt_count = attempt_count + 1, last_attempt_at = :at, next_attempt_at = :nextAttemptAt, last_error = :error, updated_at = :at WHERE sale_id = :saleId AND state IN ('PENDING', 'RETRY')")
    suspend fun markRetry(saleId: UUID, error: String, at: Instant, nextAttemptAt: Instant): Int

    @Query("UPDATE pos_sale_outbox SET state = 'BLOCKED', attempt_count = attempt_count + 1, last_attempt_at = :at, last_error = :error, updated_at = :at WHERE sale_id = :saleId AND state IN ('PENDING', 'RETRY')")
    suspend fun markBlocked(saleId: UUID, error: String, at: Instant): Int
}
