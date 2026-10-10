package com.example.bspos.data.repository

import com.example.bspos.data.local.dao.CustomerRouteDao
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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CustomerRepositoryImpl @Inject constructor(
    private val local: CustomerRouteDao,
    private val remote: RemoteMutationRecorder? = null,
    private val connection: MiCatalogoConnectionRepository? = null
) : CustomerRepository {
    override fun observeAll() = local.observeCustomers().map { it.map { row -> row.toDomain() } }
    override suspend fun findById(id: UUID) = local.findCustomer(id)?.toDomain()
    override suspend fun insert(record: Customer) {
        val entity = record.toEntity().copy(miCatalogoCustomerShopId = connection?.activeShopId())
        val queue = remote ?: run {
            local.insertCustomer(entity)
            return
        }
        queue.transaction {
            local.insertCustomer(entity)
            queue.customer(entity)
        }
        queue.wake()
    }
    override suspend fun update(record: Customer): Boolean {
        val existing = local.findCustomer(record.id)
        val entity = record.toEntity().copy(
            miCatalogoCustomerId = existing?.miCatalogoCustomerId,
            miCatalogoCustomerShopId = existing?.miCatalogoCustomerShopId ?: connection?.activeShopId()
        )
        val queue = remote ?: return local.updateCustomer(entity) == 1
        val updated = queue.transaction {
            val changed = local.updateCustomer(entity)
            if (changed == 1) queue.customer(entity)
            changed
        } == 1
        if (updated) queue.wake()
        return updated
    }
    override suspend fun softDelete(id: UUID, at: Instant): Boolean {
        val existing = local.findCustomer(id) ?: return false
        val queue = remote ?: return local.softDeleteCustomer(id, at) == 1
        val deleted = queue.transaction {
            val changed = local.softDeleteCustomer(id, at)
            if (changed == 1) queue.customer(existing.copy(isActive = false, deletedAt = at, updatedAt = at), at)
            changed
        } == 1
        if (deleted) queue.wake()
        return deleted
    }
}

class RouteRepositoryImpl @Inject constructor(private val local: CustomerRouteDao) : RouteRepository {
    override fun observeAll() = local.observeRoutes().map { it.map { row -> row.toDomain() } }
    override suspend fun findById(id: UUID) = local.findRoute(id)?.toDomain()
    override suspend fun insert(route: CommercialRoute) = local.insertRoute(route.toEntity())
    override suspend fun update(route: CommercialRoute) = local.updateRoute(route.toEntity()) == 1
    override fun observeCustomers(routeId: UUID) = local.observeRouteCustomers(routeId).map { it.map { row -> row.toDomain() } }
    override suspend fun assign(customer: RouteCustomer) = local.assignCustomer(customer.toEntity())
    override suspend fun unassign(customerId: UUID) = local.unassignCustomer(customerId)
    override suspend fun reorder(routeId: UUID, customerIds: List<UUID>) = local.reorderRoute(routeId, customerIds)
}
