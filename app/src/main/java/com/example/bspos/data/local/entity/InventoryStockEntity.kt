package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bspos.domain.model.InventoryLocationType
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "inventory_stock",
    foreignKeys = [ForeignKey(
        entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"],
        onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
    )],
    indices = [
        Index(value = ["product_id", "location_type", "location_id"], name = "idx_inventory_stock_unique", unique = true),
        Index(value = ["product_id"], name = "idx_inventory_stock_product_id"),
        Index(value = ["location_type", "location_id"], name = "idx_inventory_stock_location")
    ]
)
data class InventoryStockEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "product_id") val productId: UUID,
    @ColumnInfo(name = "location_type") val locationType: InventoryLocationType,
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(defaultValue = "0") val quantity: Long = 0,
    @ColumnInfo(name = "reserved_quantity", defaultValue = "0") val reservedQuantity: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)
