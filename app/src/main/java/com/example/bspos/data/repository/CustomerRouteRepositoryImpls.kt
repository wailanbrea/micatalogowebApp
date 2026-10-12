package com.example.bspos.data.repository

import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.apiErrorMessage
import com.example.bspos.data.micatalogo.toRemoteRequest
import java.io.IOException
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.data.micatalogo.RemoteMutationRecorder
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.RouteCustomer
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.RouteRepository
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CustomerRepositoryImpl @Inject constructor(
    private val local: CustomerRouteDao,
    private val remote: RemoteMutationRecorder? = null,
    private val connection: MiCatalogoConnectionRepository? = null,
    private val shops: ShopScope,
    private val api: MiCatalogoCustomerApi
) : CustomerRepository {
    override fun observeAll() = shops.observe(local::observeCustomers).map { it.map { row -> row.toDomain() } }
    override suspend fun findById(id: UUID) = local.findCustomer(id, shops.current())?.toDomain()
    override suspend fun insert(record: Customer) {
        shops.mutate { shop ->
            require(record.remoteShopId==null || record.remoteShopId==shop) { "El cliente pertenece a otra tienda" }
            val entity = record.toEntity().copy(miCatalogoCustomerShopId=shop, tenant=TenantMetadata.assigned(shop))
            // Connected validation/auth failures do not create a local-only customer.
            // A transport failure retains one idempotent UUID in the offline outbox.
            val response = try { api.createCustomer(shop, entity.toRemoteRequest()) } catch (_: IOException) { null }
            if (response != null) {
                check(response.isSuccessful) { response.apiErrorMessage("No se pudo guardar el cliente en MiCatalogo.") }
                val saved = checkNotNull(response.body()) { "MiCatalogo no devolvió el cliente guardado." }
                require(saved.id.isNotBlank() && (saved.clientCustomerUuid==null || saved.clientCustomerUuid==entity.id.toString()))
                local.insertCustomer(entity.copy(miCatalogoCustomerId=saved.id,
                    businessName=saved.name ?: entity.businessName, isActive=saved.isActive ?: entity.isActive,
                    balance=com.example.bspos.data.micatalogo.MiCatalogoImportMapper.cents(saved.balance),
                    creditLimit=saved.creditLimit?.let(com.example.bspos.data.micatalogo.MiCatalogoImportMapper::cents) ?: entity.creditLimit))
            } else {
                val queue=checkNotNull(remote) { "No está disponible la cola sin conexión." }
                queue.transaction { local.insertCustomer(entity); queue.customer(entity) }
                queue.wake()
            }
        }
    }
    override suspend fun update(record: Customer): Boolean = shops.mutate { shop ->
        val existing = local.findCustomer(record.id, shop) ?: return@mutate false
        val entity = record.toEntity().copy(
            miCatalogoCustomerId = existing?.miCatalogoCustomerId,
            miCatalogoCustomerShopId = shop, tenant=existing.tenant
        )
        val queue = remote ?: return@mutate local.updateCustomer(entity) == 1
        val updated = queue.transaction {
            val changed = local.updateCustomer(entity)
            if (changed == 1) queue.customer(entity)
            changed
        } == 1
        if (updated) queue.wake()
        updated
    }
    override suspend fun softDelete(id: UUID, at: Instant): Boolean = shops.mutate { shop ->
        val existing = local.findCustomer(id, shop) ?: return@mutate false
        val queue = remote ?: return@mutate local.softDeleteCustomer(id, at, shop) == 1
        val deleted = queue.transaction {
            val changed = local.softDeleteCustomer(id, at, shop)
            if (changed == 1) queue.customer(existing.copy(isActive = false, deletedAt = at, updatedAt = at), at)
            changed
        } == 1
        if (deleted) queue.wake()
        deleted
    }
}

class RouteRepositoryImpl @Inject constructor(private val local: CustomerRouteDao, private val shops: ShopScope,
    private val remote: RemoteMutationRecorder) : RouteRepository {
    override fun observeAll() = shops.observe(local::observeRoutes).map { it.map { row -> row.toDomain() } }
    override suspend fun findById(id: UUID) = local.findRoute(id, shops.current())?.toDomain()
    override suspend fun insert(route: CommercialRoute) {
        shops.mutate { shop -> remote.transaction {
            val row = route.toEntity().copy(tenant = TenantMetadata.assigned(shop))
            local.insertRoute(row); remote.route(row)
        } }; remote.wake()
    }
    override suspend fun update(route: CommercialRoute): Boolean = shops.mutate { shop -> remote.transaction {
        val row = route.toEntity().copy(tenant = TenantMetadata.assigned(shop))
        (local.findRoute(route.id, shop) != null && local.updateRoute(row) == 1).also { if (it) remote.route(row) }
    } }.also { if (it) remote.wake() }
    override fun observeCustomers(routeId: UUID) = shops.observe { local.observeRouteCustomers(routeId, it) }.map { it.map { row -> row.toDomain() } }
    override suspend fun assign(customer: RouteCustomer) {
        shops.mutate { shop -> remote.transaction {
            val previous = local.findRouteCustomer(customer.customerId, shop)
            val key = customerKey(customer.customerId, shop)
            local.assignCustomer(customer.toEntity().copy(tenant = TenantMetadata.assigned(shop)))
            remote.enqueue(shop, emptyList(), buildJsonObject {
                put("type", "route_assign"); put("record_id", customer.routeId.toString()); put("customer_id", key)
                put("visit_order", customer.visitOrder)
                put("related_record_ids", buildJsonArray { previous?.routeId?.let { add(JsonPrimitive(it.toString())) } })
            }, Instant.now())
        } }; remote.wake()
    }
    override suspend fun unassign(customerId: UUID): Boolean = shops.mutate { shop -> remote.transaction {
        val previous = local.findRouteCustomer(customerId, shop) ?: return@transaction false
        val key = customerKey(customerId, shop)
        local.unassignCustomer(customerId, shop).also { if (it) remote.enqueue(shop, emptyList(), buildJsonObject {
            put("type", "route_unassign"); put("record_id", previous.routeId.toString()); put("customer_id", key)
        }, Instant.now()) }
    } }.also { if (it) remote.wake() }
    override suspend fun reorder(routeId: UUID, customerIds: List<UUID>) {
        shops.mutate { shop -> remote.transaction {
            val keys = customerIds.map { customerKey(it, shop) }
            local.reorderRoute(routeId, customerIds, shop)
            remote.enqueue(shop, emptyList(), buildJsonObject {
                put("type", "route_reorder"); put("record_id", routeId.toString())
                put("customer_ids", buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } })
            }, Instant.now())
        } }; remote.wake()
    }
    private suspend fun customerKey(id: UUID, shop: String): String = checkNotNull(local.findCustomer(id, shop))
        .let { it.miCatalogoCustomerId ?: it.id.toString() }
}
