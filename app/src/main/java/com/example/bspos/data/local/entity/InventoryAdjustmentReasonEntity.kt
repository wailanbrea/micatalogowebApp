package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.bspos.domain.model.AdjustmentDirection
import java.time.Instant
import java.util.UUID

@Entity(tableName = "inventory_adjustment_reasons")
data class InventoryAdjustmentReasonEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val direction: AdjustmentDirection,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null
)
