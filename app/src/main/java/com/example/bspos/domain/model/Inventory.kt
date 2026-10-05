package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

enum class InventoryLocationType { MAIN_WAREHOUSE, ROUTE }

data class InventoryLocation(val type: InventoryLocationType, val id: String) {
    init {
        when (type) {
            InventoryLocationType.MAIN_WAREHOUSE -> require(id == "MAIN")
            InventoryLocationType.ROUTE -> require(UUID.fromString(id).toString() == id) {
                "Route location must be a canonical UUID"
            }
        }
    }

    companion object {
        val MAIN = InventoryLocation(InventoryLocationType.MAIN_WAREHOUSE, "MAIN")
        fun route(id: UUID) = InventoryLocation(InventoryLocationType.ROUTE, id.toString())
    }
}

enum class AdjustmentDirection { IN, OUT, BOTH }

enum class InventoryMovementType(val direction: AdjustmentDirection, val requiresReason: Boolean = false) {
    INITIAL(AdjustmentDirection.IN),
    PURCHASE(AdjustmentDirection.IN),
    PURCHASE_RETURN(AdjustmentDirection.OUT),
    SALE(AdjustmentDirection.OUT),
    SALE_RETURN(AdjustmentDirection.IN),
    ADJUSTMENT_IN(AdjustmentDirection.IN, true),
    ADJUSTMENT_OUT(AdjustmentDirection.OUT, true),
    PHYSICAL_COUNT_IN(AdjustmentDirection.IN),
    PHYSICAL_COUNT_OUT(AdjustmentDirection.OUT),
    ROUTE_LOAD_OUT(AdjustmentDirection.OUT),
    ROUTE_LOAD_IN(AdjustmentDirection.IN),
    ROUTE_SALE(AdjustmentDirection.OUT),
    ROUTE_RETURN(AdjustmentDirection.BOTH),
    DAMAGED(AdjustmentDirection.OUT, true),
    LOSS(AdjustmentDirection.OUT, true),
    EXPIRED(AdjustmentDirection.OUT, true),
    INTERNAL_USE(AdjustmentDirection.OUT, true),
    TRANSFER_IN(AdjustmentDirection.IN),
    TRANSFER_OUT(AdjustmentDirection.OUT)
}

enum class InventoryReferenceType { SALE, PURCHASE, STOCK_ENTRY, ADJUSTMENT, ROUTE_LOAD, COUNT, RETURN }

data class InventoryAdjustmentReason(
    val id: UUID,
    val name: String,
    val direction: AdjustmentDirection,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)

data class InventoryStock(
    val id: UUID,
    val productId: UUID,
    val location: InventoryLocation,
    val quantity: Long,
    val reservedQuantity: Long,
    val updatedAt: Instant
)

/** Quantities and costs are signed; totalCost = quantity * unitCost (Long cents). */
data class InventoryMovement(
    val id: UUID,
    val productId: UUID,
    val location: InventoryLocation,
    val type: InventoryMovementType,
    val quantity: Long,
    val previousQuantity: Long,
    val newQuantity: Long,
    val unitCost: Long,
    val totalCost: Long,
    val referenceType: InventoryReferenceType? = null,
    val referenceId: UUID? = null,
    val reasonId: UUID? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val createdBy: String? = null
)
