package com.example.bspos.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.time.Instant

/** Durable immutable request body. Status is mutable; the business payload is not. */
@Entity(tableName = "operation_outbox")
data class OperationOutboxEntity(
    @PrimaryKey val id: String,
    val shopId: String,
    val productIds: String,
    val payload: String,
    val createdAt: Instant,
    val state: String = "PENDING",
    val error: String? = null,
    @ColumnInfo(name = "queue_sequence", defaultValue = "0") val queueSequence: Long = 0,
    @ColumnInfo(name = "server_response", defaultValue = "NULL") val serverResponse: String? = null
)
