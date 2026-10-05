package com.example.bspos.data.local.entity
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_sync_outbox")
data class PaymentSyncEntity(@PrimaryKey val id: String, val shopId: String,
    val customerId: String, val payload: String, val dependencies: String,
    val state: String = "PENDING", val error: String? = null)
