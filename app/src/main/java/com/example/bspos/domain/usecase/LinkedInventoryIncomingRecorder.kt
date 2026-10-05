package com.example.bspos.domain.usecase

import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.domain.model.*
import java.math.BigInteger
import java.util.UUID

/** Joins the caller's transaction after the primary stock movement was written. */
internal object LinkedInventoryIncomingRecorder {
    suspend fun record(product: ProductEntity, movement: InventoryMovement, products: ProductDao, inventory: InventoryDao) {
        val receipt = movement.type in setOf(InventoryMovementType.INITIAL, InventoryMovementType.PURCHASE)
        val linked = product.miCatalogoShopId != null && product.miCatalogoSaleUnit in setOf("bottle", "ml")
        val size = if (linked && product.miCatalogoSaleUnit == "bottle") checkNotNull(product.miCatalogoVolumeMl).toLong() else 1L
        require(size > 0)
        val before = if (linked) product.miCatalogoAvailableMl?.toLong() ?: Math.multiplyExact(movement.previousQuantity, size) else movement.previousQuantity
        val after = if (receipt) Math.addExact(before, Math.multiplyExact(movement.quantity, size)) else Math.multiplyExact(movement.newQuantity, size)
        require(!linked || after in 0..Int.MAX_VALUE.toLong())
        val average = if (receipt) {
            val oldCost = BigInteger.valueOf(product.averageCost).multiply(BigInteger.valueOf(before.coerceAtLeast(0)))
            val receivedCost = BigInteger.valueOf(movement.unitCost).multiply(BigInteger.valueOf(Math.multiplyExact(movement.quantity, size)))
            oldCost.add(receivedCost).divide(BigInteger.valueOf(Math.addExact(before.coerceAtLeast(0), Math.multiplyExact(movement.quantity, size)))).longValueExact()
        } else product.averageCost
        check(products.update(product.copy(averageCost = average,
            lastPurchaseCost = if (receipt) movement.unitCost else product.lastPurchaseCost,
            miCatalogoAvailableMl = if (linked) after.toInt() else product.miCatalogoAvailableMl,
            updatedAt = movement.createdAt)) == 1)
        if (!linked) return
        val siblings = products.findForShop(checkNotNull(product.miCatalogoShopId)).filter { it.miCatalogoSourceProductId == product.miCatalogoProductId }
        val ledger = siblings.mapNotNull { sibling ->
            val unit = if (sibling.miCatalogoSaleUnit == "ml") 1L else checkNotNull(sibling.miCatalogoVolumeMl).toLong()
            require(unit > 0)
            val stock = inventory.findStock(sibling.id, movement.location.type, movement.location.id)?.quantity ?: 0L
            val target = after / unit
            val delta = target - stock
            if (delta == 0L) null else InventoryMovementEntity(UUID.randomUUID(), sibling.id, movement.location.type,
                movement.location.id, if (delta > 0) InventoryMovementType.PHYSICAL_COUNT_IN else InventoryMovementType.PHYSICAL_COUNT_OUT,
                delta, stock, target, sibling.averageCost, Math.multiplyExact(delta, sibling.averageCost),
                notes = "Actualización de botella fuente", createdAt = movement.createdAt)
        }
        if (ledger.isNotEmpty()) inventory.recordMovements(ledger, false)
    }
}
