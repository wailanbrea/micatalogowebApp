package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bspos.domain.model.RouteLoadStatus
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "route_loads",
    foreignKeys = [ForeignKey(entity = RouteEntity::class, parentColumns = ["id"], childColumns = ["route_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["route_id"], name = "idx_route_loads_route_id"), Index(value = ["date"], name = "idx_route_loads_date")]
)
data class RouteLoadEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "route_id") val routeId: UUID,
    val date: Instant,
    @ColumnInfo(defaultValue = "'OPEN'") val status: RouteLoadStatus = RouteLoadStatus.OPEN,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant
)

@Entity(
    tableName = "route_load_items",
    foreignKeys = [
        ForeignKey(entity = RouteLoadEntity::class, parentColumns = ["id"], childColumns = ["route_load_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["route_load_id"], name = "idx_route_load_items_load"), Index(value = ["product_id"], name = "idx_route_load_items_product_id"), Index(value = ["route_load_id", "product_id"], name = "idx_route_load_items_unique", unique = true)]
)
data class RouteLoadItemEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "route_load_id") val routeLoadId: UUID,
    @ColumnInfo(name = "product_id") val productId: UUID,
    val quantity: Long,
    @ColumnInfo(name = "unit_cost_snapshot") val unitCostSnapshot: Long
)
