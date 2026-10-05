package com.example.bspos.domain.repository

import com.example.bspos.domain.model.InventoryAdjustmentReason
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryMovement
import com.example.bspos.domain.model.InventoryStock
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

interface InventoryAdjustmentReasonRepository : CatalogRecordRepository<InventoryAdjustmentReason>

interface InventoryRepository {
    fun observeStock(location: InventoryLocation): Flow<List<InventoryStock>>
    suspend fun findStock(productId: UUID, location: InventoryLocation): InventoryStock?
    fun observeMovements(
        productId: UUID, location: InventoryLocation,
        fromInclusive: Instant? = null, toExclusive: Instant? = null
    ): Flow<List<InventoryMovement>>
    suspend fun findMovement(id: UUID): InventoryMovement?

    /** Atomic ledger append; stale previous quantities cause the whole batch to fail. */
    suspend fun recordMovements(movements: List<InventoryMovement>, allowNegativeStock: Boolean = false)
}
