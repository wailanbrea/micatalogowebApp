package com.example.bspos.data.micatalogo

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/** Stable local identities keep remote resources separate from manually created BSPOS records. */
object MiCatalogoImportMapper {
    fun categoryId(shopId: String, remoteCategoryId: String): UUID = id(shopId, "category", remoteCategoryId)

    fun fallbackCategoryId(shopId: String): UUID = id(shopId, "category", "uncategorized")

    fun productId(shopId: String, remoteProductId: String): UUID = id(shopId, "product", remoteProductId)

    fun unitId(shopId: String, saleUnit: String?): UUID = id(shopId, "unit", normalizedSaleUnit(saleUnit))

    fun inventoryMovementId(shopId: String, remoteProductId: String, updatedAt: String, quantity: Long): UUID =
        id(shopId, "inventory", "$remoteProductId:$updatedAt:$quantity")

    fun internalCode(shopId: String, remoteProductId: String): String =
        "MC-${productId(shopId, remoteProductId)}"

    fun normalizedSaleUnit(saleUnit: String?): String = saleUnit.orEmpty().trim().lowercase(Locale.ROOT).ifBlank { "unit" }

    fun cents(value: String?): Long = value?.trim()?.takeIf { it.isNotEmpty() }?.let {
        BigDecimal(it).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    } ?: 0L

    fun isProductActive(availabilityStatus: String, moderationStatus: String): Boolean {
        val inactiveStatuses = setOf("draft", "suspended")
        return availabilityStatus.trim().lowercase(Locale.ROOT) !in inactiveStatuses &&
            moderationStatus.trim().lowercase(Locale.ROOT) !in inactiveStatuses
    }

    private fun id(shopId: String, resourceType: String, remoteId: String): UUID =
        UUID.nameUUIDFromBytes("micatalogo:$resourceType:$shopId:$remoteId".toByteArray(StandardCharsets.UTF_8))
}
