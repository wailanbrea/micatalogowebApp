package com.example.bspos.domain.repository

import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.RouteCustomer
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

interface CustomerRepository : CatalogRecordRepository<Customer>
interface RouteRepository {
    fun observeAll(): Flow<List<CommercialRoute>>
    suspend fun findById(id: UUID): CommercialRoute?
    suspend fun insert(route: CommercialRoute)
    suspend fun update(route: CommercialRoute): Boolean
    fun observeCustomers(routeId: UUID): Flow<List<RouteCustomer>>
    suspend fun assign(customer: RouteCustomer)
    suspend fun unassign(customerId: UUID): Boolean
    suspend fun reorder(routeId: UUID, customerIds: List<UUID>)
}
