package com.example.bspos.data.repository

import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.RouteCustomer
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.RouteRepository
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CustomerRepositoryImpl @Inject constructor(private val local: CustomerRouteDao) : CustomerRepository {
    override fun observeAll() = local.observeCustomers().map { it.map { row -> row.toDomain() } }
    override suspend fun findById(id: UUID) = local.findCustomer(id)?.toDomain()
    override suspend fun insert(record: Customer) = local.insertCustomer(record.toEntity())
    override suspend fun update(record: Customer): Boolean {
        val existing = local.findCustomer(record.id)

        return local.updateCustomer(record.toEntity().copy(
            miCatalogoCustomerId = existing?.miCatalogoCustomerId,
            miCatalogoCustomerShopId = existing?.miCatalogoCustomerShopId
        )) == 1
    }
    override suspend fun softDelete(id: UUID, at: Instant) = local.softDeleteCustomer(id, at) == 1
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
