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
    fun observeStock(location: InventoryLocation) = inventory.observeStock(location.type, location.id)
    suspend fun findStock(productId: UUID, location: InventoryLocation) =
        inventory.findStock(productId, location.type, location.id)

    fun observeMovements(productId: UUID, location: InventoryLocation, fromInclusive: Instant?, toExclusive: Instant?) =
        inventory.observeMovements(productId, location.type, location.id, fromInclusive, toExclusive)

    suspend fun findMovement(id: UUID) = inventory.findMovement(id)
    suspend fun recordMovements(movements: List<InventoryMovementEntity>, allowNegativeStock: Boolean) =
        inventory.recordMovements(movements, allowNegativeStock)

    fun observeReasons() = reasons.observeAll()
    suspend fun findReason(id: UUID) = reasons.findById(id)
    suspend fun insert(reason: InventoryAdjustmentReasonEntity) = reasons.insert(reason)
    suspend fun update(reason: InventoryAdjustmentReasonEntity) = reasons.update(reason) == 1
    suspend fun deleteReason(id: UUID, at: Instant) = reasons.softDelete(id, at) == 1
}
