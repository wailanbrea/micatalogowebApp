package com.example.bspos.data.repository

import com.example.bspos.data.local.InventoryLocalDataSource
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.micatalogo.RemoteMutationRecorder
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import com.example.bspos.domain.usecase.LinkedInventoryIncomingRecorder
import com.example.bspos.domain.model.InventoryMovementType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.domain.model.InventoryAdjustmentReason
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryMovement
import com.example.bspos.domain.repository.InventoryAdjustmentReasonRepository
import com.example.bspos.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class InventoryRepositoryImpl @Inject constructor(
    private val local: InventoryLocalDataSource,
    private val products: ProductDao,
    private val inventory: InventoryDao,
    private val remote: RemoteMutationRecorder,
    private val shops: ShopScope
) : InventoryRepository {
    override fun observeStock(location: InventoryLocation) =
        shops.observe { local.observeStock(location, it) }.map { rows -> rows.map { it.toDomain() } }

    override suspend fun findStock(productId: UUID, location: InventoryLocation) =
        local.findStock(productId, location, shops.current())?.toDomain()

    override fun observeMovements(
        productId: UUID, location: InventoryLocation, fromInclusive: Instant?, toExclusive: Instant?
    ): Flow<List<InventoryMovement>> {
        require(fromInclusive == null || toExclusive == null || fromInclusive < toExclusive) { "Invalid date range" }
        return shops.observe { local.observeMovements(productId, location, fromInclusive, toExclusive, it) }
            .map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeAllMovements(location: InventoryLocation): Flow<List<InventoryMovement>> =
        shops.observe { local.observeAllMovements(location, it) }.map { rows -> rows.map { it.toDomain() } }

    override suspend fun findMovement(id: UUID) = local.findMovement(id, shops.current())?.toDomain()
    override suspend fun recordMovements(movements: List<InventoryMovement>, allowNegativeStock: Boolean) {
        shops.mutate { shop -> remote.transaction {
            val mutations = movements.filter { it.type in REMOTE_STOCK_TYPES }
            for (movement in mutations) {
                val product = checkNotNull(products.findById(movement.productId, shop)) { "El producto no pertenece a la tienda" }
                if (product.miCatalogoShopId == null) continue
                require(movement.location == InventoryLocation.MAIN) { "Los cambios remotos se registran en el almacén principal." }
                require(product.miCatalogoSaleUnit != "decant") { "Modifica el inventario de la botella fuente, no el decant." }
                require(movement.newQuantity in 0..1_000_000L) { "La cantidad remota debe estar entre 0 y 1,000,000." }
                val receipt = movement.type in setOf(InventoryMovementType.INITIAL, InventoryMovementType.PURCHASE)
                if (receipt) require(movement.quantity in 1..1_000_000L && movement.unitCost in 0..100_000_000_000L)
                remote.enqueue(product.miCatalogoShopId, listOf(checkNotNull(product.miCatalogoProductId)), buildJsonObject {
                    put("type", if (receipt) "restock" else "adjustment")
                    put("product_id", product.miCatalogoProductId)
                    if (receipt) {
                        put("quantity", movement.quantity)
                        put("unit_cost", MiCatalogoPosSaleOutboxMapper.decimalPrice(movement.unitCost))
                    } else {
                        put("expected_stock", movement.previousQuantity); put("stock", movement.newQuantity)
                        product.miCatalogoAvailableMl?.let { put("expected_available_ml", it) }
                    }
                    put("notes", movement.notes?.takeIf { it.isNotBlank() } ?: "${movement.type}: registro desde Android")
                }, movement.createdAt, movement.id.toString())
            }
            local.recordMovements(movements.map { it.toEntity().copy(tenant=TenantMetadata.assigned(shop)) }, allowNegativeStock)
            for (movement in mutations) {
                val product = checkNotNull(products.findById(movement.productId, shop))
                LinkedInventoryIncomingRecorder.record(product, movement, products, inventory)
            }
        }
        }
        remote.wake()
    }

    private companion object {
        val REMOTE_STOCK_TYPES = setOf(InventoryMovementType.INITIAL, InventoryMovementType.PURCHASE,
            InventoryMovementType.ADJUSTMENT_IN, InventoryMovementType.ADJUSTMENT_OUT,
            InventoryMovementType.PHYSICAL_COUNT_IN, InventoryMovementType.PHYSICAL_COUNT_OUT,
            InventoryMovementType.DAMAGED, InventoryMovementType.LOSS, InventoryMovementType.EXPIRED,
            InventoryMovementType.INTERNAL_USE)
    }
}

class InventoryAdjustmentReasonRepositoryImpl @Inject constructor(
    private val local: InventoryLocalDataSource,
    private val shops: ShopScope
) : InventoryAdjustmentReasonRepository {
    override fun observeAll() = shops.observe(local::observeReasons).map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findReason(id, shops.current())?.toDomain()
    override suspend fun insert(record: InventoryAdjustmentReason) = shops.mutate { local.insert(record.toEntity().copy(tenant=TenantMetadata.assigned(it))) }
    override suspend fun update(record: InventoryAdjustmentReason) = shops.mutate { local.update(record.toEntity().copy(tenant=TenantMetadata.assigned(it))) }
    override suspend fun softDelete(id: UUID, at: Instant) = shops.mutate { local.deleteReason(id, at, it) }
}
