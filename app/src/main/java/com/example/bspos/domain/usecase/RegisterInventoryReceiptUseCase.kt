package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.ProductRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RegisterInventoryReceiptUseCase @Inject constructor(private val inventory: InventoryRepository,
    private val products: ProductRepository) {
    suspend operator fun invoke(productId: UUID, quantity: Long, unitCost: Long, notes: String = "", at: Instant = Instant.now()) {
        require(quantity in 1..1_000_000L && unitCost in 0..100_000_000_000L) { "Cantidad o costo fuera de rango." }
        val product = checkNotNull(products.findById(productId)) { "Producto no encontrado." }
        require(product.isActive && product.deletedAt == null)
        val previous = inventory.findStock(productId, InventoryLocation.MAIN)?.quantity ?: 0L
        inventory.recordMovements(listOf(InventoryMovement(UUID.randomUUID(), productId, InventoryLocation.MAIN,
            InventoryMovementType.PURCHASE, quantity, previous, Math.addExact(previous, quantity), unitCost,
            Math.multiplyExact(quantity, unitCost), notes = notes, createdAt = at)), false)
    }
}
