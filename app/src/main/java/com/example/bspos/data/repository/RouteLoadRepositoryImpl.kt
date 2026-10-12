package com.example.bspos.data.repository

import com.example.bspos.data.local.dao.RouteLoadDao
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.domain.model.RouteLoad
import com.example.bspos.domain.model.RouteLoadItem
import com.example.bspos.domain.model.RouteLoadStatus
import com.example.bspos.domain.repository.RouteLoadRepository
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RouteLoadRepositoryImpl @Inject constructor(private val local: RouteLoadDao, private val shops: ShopScope,
    private val remote: com.example.bspos.data.micatalogo.RemoteMutationRecorder,
    private val products: com.example.bspos.data.local.dao.ProductDao,
    private val inventory: com.example.bspos.data.local.dao.InventoryDao) : RouteLoadRepository {
    override suspend fun insert(load: RouteLoad, items: List<RouteLoadItem>) {
        shops.mutate { shop -> remote.transaction {
            val payloadItems = items.map { item ->
                val product = checkNotNull(products.findById(item.productId, shop)) { "El producto no pertenece a la tienda" }
                require((product.miCatalogoSaleUnit ?: "unit") == "unit" && !product.miCatalogoIsCombo) {
                    "Las cargas admiten productos físicos por unidad; las presentaciones por ml conservan su flujo de Decants."
                }
                buildJsonObject {
                    put("id", item.id.toString()); put("product_id", product.miCatalogoProductId ?: com.example.bspos.data.micatalogo.RemoteMutationRecorder.productId(product.id))
                    put("quantity", item.quantity); put("unit_cost_cents", item.unitCostSnapshot)
                    put("expected_main_quantity", inventory.findStock(product.id, com.example.bspos.domain.model.InventoryLocationType.MAIN_WAREHOUSE, "MAIN", shop)?.quantity ?: 0)
                }
            }
            local.insertWithItems(load.toEntity().copy(tenant = TenantMetadata.assigned(shop)), items.map { it.toEntity() })
            remote.enqueue(shop, payloadItems.map { it.getValue("product_id").jsonPrimitive.content }, buildJsonObject {
                put("type", "route_load_create"); put("record_id", load.id.toString()); put("route_id", load.routeId.toString())
                put("date", load.date.toString()); put("created_at", load.createdAt.toString()); put("notes", load.notes?.let(::JsonPrimitive) ?: JsonNull)
                put("items", JsonArray(payloadItems))
            }, load.createdAt)
        } }; remote.wake()
    }
    override fun observeForRoute(routeId: UUID) = shops.observe { local.observeForRoute(routeId, it) }.map { it.map { row -> row.toDomain() } }
    override fun observeItems(loadId: UUID) = shops.observe { local.observeItems(loadId, it) }.map { it.map { row -> row.toDomain() } }
    override suspend fun updateStatus(id: UUID, status: RouteLoadStatus) {
        shops.mutate { shop -> remote.transaction {
            val ids = local.itemsForSync(id, shop).map { checkNotNull(products.findById(it.productId, shop)).miCatalogoProductId }.filterNotNull()
            local.updateStatus(id, status, shop)
            remote.enqueue(shop, ids, buildJsonObject {
                put("type", "route_load_status"); put("record_id", id.toString()); put("status", status.name)
            }, Instant.now())
        } }; remote.wake()
    }
}
