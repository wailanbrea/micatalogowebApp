package com.example.bspos.data.local

import com.example.bspos.data.local.dao.InventoryAdjustmentReasonDao
import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.entity.InventoryAdjustmentReasonEntity
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.domain.model.InventoryLocation
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class InventoryLocalDataSource @Inject constructor(
    private val inventory: InventoryDao,
    private val reasons: InventoryAdjustmentReasonDao
) {
    fun observeStock(location: InventoryLocation, shopId: String? = null) = inventory.observeStock(location.type, location.id, shopId)
    suspend fun findStock(productId: UUID, location: InventoryLocation, shopId: String? = null) =
        inventory.findStock(productId, location.type, location.id, shopId)

    fun observeMovements(productId: UUID, location: InventoryLocation, fromInclusive: Instant?, toExclusive: Instant?, shopId: String? = null) =
        inventory.observeMovements(productId, location.type, location.id, fromInclusive, toExclusive, shopId)

    fun observeAllMovements(location: InventoryLocation, shopId: String? = null) = inventory.observeAllMovements(location.type, location.id, shopId)

    suspend fun findMovement(id: UUID, shopId: String? = null) = inventory.findMovement(id, shopId)
    suspend fun recordMovements(movements: List<InventoryMovementEntity>, allowNegativeStock: Boolean) =
        inventory.recordMovements(movements, allowNegativeStock)

    fun observeReasons(shopId: String? = null) = reasons.observeAll(shopId)
    suspend fun findReason(id: UUID, shopId: String? = null) = reasons.findById(id, shopId)
    suspend fun insert(reason: InventoryAdjustmentReasonEntity) = reasons.insert(reason)
    suspend fun update(reason: InventoryAdjustmentReasonEntity) = findReason(reason.id, reason.tenant.shopId)!=null && reasons.update(reason) == 1
    suspend fun deleteReason(id: UUID, at: Instant, shopId: String? = null) = reasons.softDelete(id, at, shopId) == 1
}
