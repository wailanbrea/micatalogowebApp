package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.ProductRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RegisterPhysicalInventoryCountUseCase @Inject constructor(private val inventory: InventoryRepository,
    private val products: ProductRepository) {
    suspend operator fun invoke(productId: UUID, counted: Long, notes: String, at: Instant = Instant.now()) {
        require(counted in 0..1_000_000L && notes.isNotBlank()) { "Indica un conteo válido y una nota." }
        val product = checkNotNull(products.findById(productId))
        require(product.isActive && product.deletedAt == null)
        val previous = inventory.findStock(productId, InventoryLocation.MAIN)?.quantity ?: 0L
        val delta = Math.subtractExact(counted, previous)
        if (delta == 0L) return
        inventory.recordMovements(listOf(InventoryMovement(UUID.randomUUID(), productId, InventoryLocation.MAIN,
            if (delta > 0) InventoryMovementType.PHYSICAL_COUNT_IN else InventoryMovementType.PHYSICAL_COUNT_OUT,
            delta, previous, counted, product.averageCost, Math.multiplyExact(delta, product.averageCost),
            notes = notes.trim(), createdAt = at)), false)
    }
}
