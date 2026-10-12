package com.example.bspos.data.micatalogo

import com.example.bspos.data.local.dao.UnitOfMeasureDao
import com.example.bspos.data.local.dao.InventoryAdjustmentReasonDao
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.RouteLoadDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.entity.*
import com.example.bspos.data.micatalogo.dto.StoreRecordDto
import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Joins the catalog's transaction; never invokes mutation repositories during read-back. */
class StoreRecordProjection @Inject constructor(
    private val units: UnitOfMeasureDao,
    private val reasons: InventoryAdjustmentReasonDao,
    private val customers: CustomerRouteDao,
    private val loads: RouteLoadDao,
    private val products: ProductDao,
    private val inventory: InventoryDao
) {
    suspend fun apply(shop: String, records: List<StoreRecordDto>, pending: Set<String>) {
        val tenant = TenantMetadata.assigned(shop)
        for (record in records) {
            if (record.kind !in setOf("unit", "adjustment_reason", "route")) continue
            if (record.recordId in pending) continue
            val id = UUID.fromString(record.recordId)
            val payload = record.payload
            val updated = Instant.parse(record.updatedAt)
            val created = Instant.parse(checkNotNull(payload["created_at"]?.jsonPrimitive?.content))
            val name = checkNotNull(payload["name"]?.jsonPrimitive?.content).also { require(it.isNotBlank()) }
            val active = checkNotNull(payload["is_active"]?.jsonPrimitive?.booleanOrNull)
            val deleted = if (payload["is_deleted"]?.jsonPrimitive?.booleanOrNull == true) updated else null
            when (record.kind) {
                "route" -> {
                    val old = customers.findRoute(id, shop)
                    val row = RouteEntity(id, name, checkNotNull(payload["code"]?.jsonPrimitive?.content),
                        payload["description"]?.jsonPrimitive?.contentOrNull, active, old?.createdAt ?: created, updated, tenant)
                    if (old == null) customers.insertRoute(row) else customers.updateRoute(row)
                }
                "unit" -> {
                    val old = units.findForReconciliation(id, shop)
                    require(old == null || old.tenant == tenant) { "Concilia la unidad histórica antes de vincularla a esta tienda." }
                    val row = UnitOfMeasureEntity(id, name, checkNotNull(payload["abbreviation"]?.jsonPrimitive?.content),
                        active, old?.createdAt ?: created, updated, deleted, tenant)
                    if (old == null) units.insert(row) else units.update(row)
                }
                "adjustment_reason" -> {
                    val old = reasons.findById(id, shop)
                    val row = InventoryAdjustmentReasonEntity(id, name,
                        AdjustmentDirection.valueOf(checkNotNull(payload["direction"]?.jsonPrimitive?.content)),
                        active, old?.createdAt ?: created, updated, deleted, tenant)
                    if (old == null) reasons.insert(row) else reasons.update(row)
                }
            }
        }
    }

    suspend fun applyRelations(shop: String, records: List<StoreRecordDto>, pending: Set<String>) {
        val tenant = TenantMetadata.assigned(shop)
        for (record in records) {
            if (record.recordId in pending) continue
            val id = UUID.fromString(record.recordId)
            val payload = record.payload
            when (record.kind) {
                "route_members" -> {
                    val rows = payload.getValue("customers").jsonArray.map { item ->
                        val member = item.jsonObject
                        val customer = checkNotNull(customers.findRemoteCustomer(member.getValue("customer_id").jsonPrimitive.content, shop)) {
                            "Falta sincronizar un cliente asignado a la ruta."
                        }
                        RouteCustomerEntity(id, customer.id, member.getValue("visit_order").jsonPrimitive.int, 1, tenant)
                    }
                    customers.applyRemoteAssignments(id, rows, shop)
                }
                "route_load" -> {
                    val route = UUID.fromString(payload.getValue("route_id").jsonPrimitive.content)
                    checkNotNull(customers.findRoute(route, shop))
                    val created = Instant.parse(payload.getValue("created_at").jsonPrimitive.content)
                    val row = RouteLoadEntity(id, route, Instant.parse(payload.getValue("date").jsonPrimitive.content),
                        RouteLoadStatus.valueOf(payload.getValue("status").jsonPrimitive.content), payload["notes"]?.jsonPrimitive?.contentOrNull, created, tenant)
                    val lines = payload.getValue("items").jsonArray.map { element ->
                        val item = element.jsonObject
                        val product = checkNotNull(products.findRemote(shop, item.getValue("product_id").jsonPrimitive.content))
                        RouteLoadItemEntity(UUID.fromString(item.getValue("id").jsonPrimitive.content), id, product.id,
                            item.getValue("quantity").jsonPrimitive.long, item.getValue("unit_cost_cents").jsonPrimitive.long, tenant)
                    }
                    loads.applyRemote(row, lines)
                }
            }
        }
    }

    suspend fun applyStock(shop: String, rows: List<com.example.bspos.data.micatalogo.dto.RouteStockDto>, pendingProducts: Set<String>, at: Instant) {
        for (row in rows) {
            if (row.productId in pendingProducts) continue
            require(row.quantity >= 0 && row.unitCostCents >= 0)
            val routeId = UUID.fromString(row.routeId)
            checkNotNull(customers.findRoute(routeId, shop))
            val product = checkNotNull(products.findRemote(shop, row.productId))
            val location = InventoryLocation.route(routeId)
            val stock = inventory.findStock(product.id, location.type, location.id, shop)
            val previous = stock?.quantity ?: 0
            val delta = row.quantity - previous
            if (delta == 0L) continue
            val timestamp = stock?.updatedAt?.takeIf { it > at } ?: at
            inventory.recordMovements(listOf(InventoryMovementEntity(UUID.randomUUID(), product.id, location.type, location.id,
                if (delta > 0) InventoryMovementType.PHYSICAL_COUNT_IN else InventoryMovementType.PHYSICAL_COUNT_OUT,
                delta, previous, row.quantity, row.unitCostCents, Math.multiplyExact(delta, row.unitCostCents),
                notes = "Confirmación de inventario de ruta desde MiCatalogo", createdAt = timestamp, tenant = TenantMetadata.assigned(shop))), false)
        }
    }
}
