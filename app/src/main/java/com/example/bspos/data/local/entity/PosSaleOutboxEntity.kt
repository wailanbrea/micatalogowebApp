package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bspos.domain.model.PosSaleOutboxState
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "pos_sale_outbox",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["remote_shop_id"], name = "idx_pos_sale_outbox_remote_shop_id"),
        Index(value = ["state", "next_attempt_at"], name = "idx_pos_sale_outbox_due")
    ]
)
data class PosSaleOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "sale_id") val saleId: UUID,
    @ColumnInfo(name = "remote_shop_id") val remoteShopId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val state: PosSaleOutboxState = PosSaleOutboxState.PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Instant? = null,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Instant,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "remote_invoice_number") val remoteInvoiceNumber: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "sent_at") val sentAt: Instant? = null,
    @ColumnInfo(name = "queue_sequence", defaultValue = "0") val queueSequence: Long = 0
)
