package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.local.entity.RouteCustomerEntity
import com.example.bspos.data.local.entity.RouteEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
abstract class CustomerRouteDao {
    @Query("SELECT * FROM customers WHERE deleted_at IS NULL ORDER BY business_name, id")
    abstract fun observeCustomers(): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE micatalogo_customer_id = :remoteId")
    abstract suspend fun findRemoteCustomer(remoteId: String): CustomerEntity?
    @Query("SELECT * FROM customers WHERE id = :id") abstract suspend fun findCustomer(id: UUID): CustomerEntity?
    @Insert abstract suspend fun insertCustomer(customer: CustomerEntity)
    @Update abstract suspend fun updateCustomer(customer: CustomerEntity): Int
    @Query("UPDATE customers SET deleted_at = :at, updated_at = :at WHERE id = :id AND deleted_at IS NULL") abstract suspend fun softDeleteCustomer(id: UUID, at: Instant): Int

    @Query("SELECT * FROM routes ORDER BY name, id") abstract fun observeRoutes(): Flow<List<RouteEntity>>
    @Query("SELECT * FROM routes WHERE id = :id") abstract suspend fun findRoute(id: UUID): RouteEntity?
    @Insert abstract suspend fun insertRoute(route: RouteEntity)
    @Update abstract suspend fun updateRoute(route: RouteEntity): Int

    @Query("SELECT * FROM route_customers WHERE route_id = :routeId ORDER BY visit_order, customer_id") abstract fun observeRouteCustomers(routeId: UUID): Flow<List<RouteCustomerEntity>>
    @Query("SELECT * FROM route_customers WHERE customer_id = :customerId") abstract suspend fun findRouteCustomer(customerId: UUID): RouteCustomerEntity?
    @Insert protected abstract suspend fun insertRouteCustomer(assignment: RouteCustomerEntity)
    @Query("DELETE FROM route_customers WHERE customer_id = :customerId") protected abstract suspend fun removeRouteCustomer(customerId: UUID): Int
    @Query("UPDATE route_customers SET visit_order = :visitOrder WHERE route_id = :routeId AND customer_id = :customerId") protected abstract suspend fun updateVisitOrder(routeId: UUID, customerId: UUID, visitOrder: Int): Int

    @Transaction
    open suspend fun assignCustomer(assignment: RouteCustomerEntity) {
        require(assignment.visitOrder >= 0) { "Visit order cannot be negative" }
        checkNotNull(findCustomer(assignment.customerId)) { "Customer not found" }.also { check(it.deletedAt == null && it.isActive) { "Customer is inactive" } }
        checkNotNull(findRoute(assignment.routeId)) { "Route not found" }.also { check(it.isActive) { "Route is inactive" } }
        val current = findRouteCustomer(assignment.customerId)
        if (current != null) removeRouteCustomer(assignment.customerId)
        insertRouteCustomer(assignment)
    }

    @Transaction
    open suspend fun unassignCustomer(customerId: UUID): Boolean = removeRouteCustomer(customerId) == 1

    @Transaction
    open suspend fun reorderRoute(routeId: UUID, customerIds: List<UUID>) {
        require(customerIds.distinct().size == customerIds.size) { "Duplicate customer IDs" }
        require(customerIds.size < Int.MAX_VALUE) { "Too many route customers" }
        // Vacate unique (route_id, visit_order) slots before assigning the final order.
        customerIds.forEachIndexed { index, customerId ->
            check(updateVisitOrder(routeId, customerId, Int.MIN_VALUE + index) == 1) { "Customer is not assigned to this route" }
        }
        customerIds.forEachIndexed { order, customerId ->
            check(updateVisitOrder(routeId, customerId, order) == 1) { "Customer is not assigned to this route" }
        }
    }
}
