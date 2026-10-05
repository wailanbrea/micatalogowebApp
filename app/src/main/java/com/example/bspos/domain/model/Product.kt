package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

/** Amounts are Long cents. Operational stock belongs to inventory, not the product. */
data class Product(
    val id: UUID,
    val name: String,
    val internalCode: String,
    val barcode: String? = null,
    val categoryId: UUID,
    val unitId: UUID,
    val description: String? = null,
    val salePrice: Long,
    val wholesalePrice: Long? = null,
    val averageCost: Long = 0,
    val lastPurchaseCost: Long = 0,
    val minimumStock: Long = 0,
    val imagePath: String? = null,
    val thumbnailPath: String? = null,
    val isActive: Boolean = true,
    val tracksExpiration: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val remoteShopId: String? = null,
    val remoteProductId: String? = null,
    val remoteSaleUnit: String? = null
)
