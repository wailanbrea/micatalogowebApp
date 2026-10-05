package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bspos.domain.model.InventoryLocationType
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.InventoryReferenceType
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "inventory_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InventoryAdjustmentReasonEntity::class, parentColumns = ["id"], childColumns = ["reason_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id", "created_at"], orders = [Index.Order.ASC, Index.Order.DESC], name = "idx_movements_product_created"),
        Index(value = ["reference_type", "reference_id"], name = "idx_movements_reference"),
        Index(value = ["location_type", "location_id"], name = "idx_movements_location"),
        Index(value = ["reason_id"], name = "idx_movements_reason_id")
    ]
)
data class InventoryMovementEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "product_id") val productId: UUID,
    @ColumnInfo(name = "location_type") val locationType: InventoryLocationType,
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "movement_type") val movementType: InventoryMovementType,
    val quantity: Long,
    @ColumnInfo(name = "previous_quantity") val previousQuantity: Long,
    @ColumnInfo(name = "new_quantity") val newQuantity: Long,
    @ColumnInfo(name = "unit_cost", defaultValue = "0") val unitCost: Long = 0,
    @ColumnInfo(name = "total_cost", defaultValue = "0") val totalCost: Long = 0,
    @ColumnInfo(name = "reference_type") val referenceType: InventoryReferenceType? = null,
    @ColumnInfo(name = "reference_id") val referenceId: UUID? = null,
    @ColumnInfo(name = "reason_id") val reasonId: UUID? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "created_by") val createdBy: String? = null
)
