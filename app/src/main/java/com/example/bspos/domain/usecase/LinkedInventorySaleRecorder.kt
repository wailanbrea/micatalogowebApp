package com.example.bspos.domain.usecase

import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.SaleItemEntity
import com.example.bspos.domain.model.*
import java.time.Instant
import java.util.UUID

/** Called inside the sale transaction; source and sibling presentations share ml. */
internal object LinkedInventorySaleRecorder {
    suspend fun record(items: List<SaleItemEntity>, saleId: UUID, at: Instant,
        location: InventoryLocation, products: ProductDao, inventory: InventoryDao,
        allowNegative: Boolean, restoring: Boolean = false) {
        val resolved = items.associateWith { checkNotNull(products.findById(it.productId)) }
        val linked = resolved.filterValues { it.miCatalogoShopId != null && it.miCatalogoSaleUnit in setOf("bottle", "ml", "decant") }
        // Services are billable catalog offers, not stock. They must never create
        // a local inventory movement or be blocked by an empty stock row.
        val services = resolved.filterValues { it.miCatalogoSaleUnit == "service" }
        val ordinary = resolved.keys - linked.keys - services.keys
        val movements = mutableListOf<InventoryMovementEntity>()
        suspend fun add(productId: UUID, target: Long, cost: Long, sold: Boolean) {
            val previous = inventory.findStock(productId, location.type, location.id)?.quantity ?: 0L
            val delta = target - previous
            if (delta == 0L) return
            val type = if (restoring && sold && delta > 0) InventoryMovementType.SALE_RETURN else if (sold && delta < 0) {
                if (location == InventoryLocation.MAIN) InventoryMovementType.SALE else InventoryMovementType.ROUTE_SALE
            } else if (delta > 0) InventoryMovementType.PHYSICAL_COUNT_IN else InventoryMovementType.PHYSICAL_COUNT_OUT
            movements += InventoryMovementEntity(UUID.randomUUID(), productId, location.type, location.id,
                type, delta, previous, target, cost, Math.multiplyExact(delta, cost),
                if (restoring) InventoryReferenceType.RETURN else InventoryReferenceType.SALE, saleId,
                notes = if (restoring) "Devolución y stock compartido" else "Venta y stock compartido", createdAt = at)
        }
        for (item in ordinary) {
            val previous = inventory.findStock(item.productId, location.type, location.id)?.quantity ?: 0L
            add(item.productId, if (restoring) Math.addExact(previous,item.quantity) else Math.subtractExact(previous,item.quantity), item.unitCostSnapshot, true)
        }
        if (linked.isNotEmpty()) require(location == InventoryLocation.MAIN) { "Las presentaciones vinculadas se venden desde el almacén principal." }
        for ((key, lines) in linked.entries.groupBy { it.value.miCatalogoShopId!! to (it.value.miCatalogoSourceProductId ?: it.value.miCatalogoProductId!!) }) {
            val source = checkNotNull(products.findRemote(key.first,key.second)) { "Sincroniza la botella fuente antes de operar." }
            if (!restoring) require(source.deletedAt == null) { "La botella fuente está archivada." }
            val shopProducts = (products.findForShop(key.first) + source + lines.map { it.value }).distinctBy { it.id }
            val available = checkNotNull(source.miCatalogoAvailableMl) { "Sincroniza los mililitros disponibles antes de vender." }.toLong()
            val consumed = lines.fold(0L) { total, entry ->
                val size = if (entry.value.miCatalogoSaleUnit == "ml") 1L else checkNotNull(entry.value.miCatalogoVolumeMl).toLong()
                require(size > 0)
                Math.addExact(total, Math.multiplyExact(entry.key.quantity, size))
            }
            val remaining = if (restoring) Math.addExact(available, consumed) else Math.subtractExact(available, consumed)
            require(remaining >= 0 && remaining <= Int.MAX_VALUE) { "Mililitros insuficientes en la botella fuente." }
            check(products.update(source.copy(miCatalogoAvailableMl = remaining.toInt(), updatedAt = at)) == 1)
            val presentations = shopProducts.filter { it.id == source.id || it.miCatalogoSourceProductId == key.second }
            for (product in presentations) {
                val size = if (product.miCatalogoSaleUnit == "ml") 1L else checkNotNull(product.miCatalogoVolumeMl).toLong()
                require(size > 0)
                val movementCost = if (restoring) items.find { it.productId == product.id }?.unitCostSnapshot ?: product.averageCost else product.averageCost
                add(product.id, remaining / size, movementCost, items.any { it.productId == product.id })
            }
        }
        if (movements.isNotEmpty()) inventory.recordMovements(movements, allowNegative)
    }
}
