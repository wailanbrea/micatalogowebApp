package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["tax_id"], name = "idx_suppliers_tax_id")]
)
data class SupplierEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    @ColumnInfo(name = "contact_name") val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    @ColumnInfo(name = "tax_id") val taxId: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null
)
