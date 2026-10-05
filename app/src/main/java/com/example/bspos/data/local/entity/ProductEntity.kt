package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UnitOfMeasureEntity::class, parentColumns = ["id"], childColumns = ["unit_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["internal_code"], name = "idx_products_internal_code", unique = true),
        Index(value = ["barcode"], name = "idx_products_barcode"),
        Index(value = ["category_id"], name = "idx_products_category_id"),
        Index(value = ["unit_id"], name = "idx_products_unit_id"),
        Index(value = ["is_active"], name = "idx_products_is_active"),
        Index(value = ["micatalogo_shop_id"], name = "idx_products_micatalogo_shop_id"),
        Index(value = ["micatalogo_product_id"], name = "idx_products_micatalogo_product_id")
    ]
)
data class ProductEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    @ColumnInfo(name = "internal_code") val internalCode: String,
    val barcode: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: UUID,
    @ColumnInfo(name = "unit_id") val unitId: UUID,
    val description: String? = null,
    @ColumnInfo(name = "sale_price") val salePrice: Long,
    @ColumnInfo(name = "wholesale_price") val wholesalePrice: Long? = null,
    @ColumnInfo(name = "average_cost", defaultValue = "0") val averageCost: Long = 0,
    @ColumnInfo(name = "last_purchase_cost", defaultValue = "0") val lastPurchaseCost: Long = 0,
    @ColumnInfo(name = "minimum_stock", defaultValue = "0") val minimumStock: Long = 0,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "micatalogo_shop_id") val miCatalogoShopId: String? = null,
    @ColumnInfo(name = "micatalogo_product_id") val miCatalogoProductId: String? = null,
    @ColumnInfo(name = "micatalogo_internal_code") val miCatalogoInternalCode: String? = null,
    @ColumnInfo(name = "micatalogo_source_product_id") val miCatalogoSourceProductId: String? = null,
    @ColumnInfo(name = "micatalogo_volume_ml") val miCatalogoVolumeMl: Int? = null,
    @ColumnInfo(name = "micatalogo_available_ml") val miCatalogoAvailableMl: Int? = null,
    @ColumnInfo(name = "micatalogo_sale_unit") val miCatalogoSaleUnit: String? = null,
    @ColumnInfo(name = "is_active", defaultValue = "1") val isActive: Boolean = true,
    @ColumnInfo(name = "tracks_expiration", defaultValue = "0") val tracksExpiration: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null
)
