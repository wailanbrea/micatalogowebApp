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
    @Query("SELECT * FROM customers WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL ORDER BY business_name, id")
    abstract fun observeCustomers(shopId: String? = null): Flow<List<CustomerEntity>>
    @Query("SELECT * FROM customers WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL AND is_active = 1 ORDER BY business_name, id")
    abstract suspend fun findActiveCustomers(shopId: String? = null): List<CustomerEntity>
    @Query("SELECT * FROM customers WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL")
    abstract suspend fun findCustomersForShop(shopId: String): List<CustomerEntity>
    @Query("SELECT * FROM customers WHERE micatalogo_customer_id = :remoteId AND remote_shop_id=:shopId AND tenancy_status='assigned'")
    abstract suspend fun findRemoteCustomer(remoteId: String, shopId: String? = null): CustomerEntity?
    @Query("SELECT * FROM customers WHERE id=:id AND (remote_shop_id=:shopId OR remote_shop_id IS NULL) AND (micatalogo_customer_shop_id=:shopId OR micatalogo_customer_shop_id IS NULL)")
    abstract suspend fun findCustomerForReconciliation(id: UUID, shopId: String): CustomerEntity?
    @Query("SELECT * FROM customers WHERE micatalogo_customer_id=:remoteId AND micatalogo_customer_shop_id=:shopId AND (remote_shop_id=:shopId OR remote_shop_id IS NULL)")
    abstract suspend fun findRemoteCustomerForReconciliation(remoteId: String, shopId: String): CustomerEntity?
    @Query("SELECT * FROM customers WHERE id = :id AND remote_shop_id=:shopId AND tenancy_status='assigned'") abstract suspend fun findCustomer(id: UUID, shopId: String? = null): CustomerEntity?
    @Insert abstract suspend fun insertCustomer(customer: CustomerEntity)
    @Update abstract suspend fun updateCustomer(customer: CustomerEntity): Int
    @Query("UPDATE customers SET deleted_at = :at, updated_at = :at WHERE id = :id AND remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL") abstract suspend fun softDeleteCustomer(id: UUID, at: Instant, shopId: String? = null): Int

    @Query("SELECT * FROM routes WHERE remote_shop_id=:shopId AND tenancy_status='assigned' ORDER BY name, id") abstract fun observeRoutes(shopId: String? = null): Flow<List<RouteEntity>>
    @Query("SELECT * FROM routes WHERE id = :id AND remote_shop_id=:shopId AND tenancy_status='assigned'") abstract suspend fun findRoute(id: UUID, shopId: String? = null): RouteEntity?
    @Insert abstract suspend fun insertRoute(route: RouteEntity)
    @Update abstract suspend fun updateRoute(route: RouteEntity): Int

    @Query("SELECT * FROM route_customers WHERE route_id = :routeId AND remote_shop_id=:shopId AND tenancy_status='assigned' AND active_slot=1 ORDER BY visit_order, customer_id") abstract fun observeRouteCustomers(routeId: UUID, shopId: String? = null): Flow<List<RouteCustomerEntity>>
    @Query("SELECT * FROM route_customers WHERE route_id=:routeId AND remote_shop_id=:shop AND tenancy_status='assigned' AND active_slot=1 ORDER BY visit_order,customer_id") abstract suspend fun assignmentsForSync(routeId: UUID, shop: String): List<RouteCustomerEntity>
    @Query("SELECT * FROM route_customers WHERE customer_id = :customerId AND remote_shop_id=:shopId AND tenancy_status='assigned' AND active_slot=1") abstract suspend fun findRouteCustomer(customerId: UUID, shopId: String? = null): RouteCustomerEntity?
    @Query("SELECT * FROM route_customers WHERE route_id=:routeId AND customer_id=:customerId AND remote_shop_id=:shopId AND tenancy_status='assigned'") protected abstract suspend fun findAssignment(routeId: UUID, customerId: UUID, shopId: String?): RouteCustomerEntity?
    @Insert protected abstract suspend fun insertRouteCustomer(assignment: RouteCustomerEntity)
    @Update protected abstract suspend fun updateAssignment(assignment: RouteCustomerEntity): Int
    @Query("UPDATE route_customers SET active_slot=NULL WHERE customer_id = :customerId AND remote_shop_id=:shopId AND tenancy_status='assigned' AND active_slot=1") protected abstract suspend fun removeRouteCustomer(customerId: UUID, shopId: String?): Int
    @Query("UPDATE route_customers SET visit_order = :visitOrder WHERE route_id = :routeId AND customer_id = :customerId AND remote_shop_id=:shopId AND tenancy_status='assigned' AND active_slot=1") protected abstract suspend fun updateVisitOrder(routeId: UUID, customerId: UUID, visitOrder: Int, shopId: String?): Int

    @Transaction
    open suspend fun applyRemoteAssignments(routeId: UUID, rows: List<RouteCustomerEntity>, shop: String) {
        checkNotNull(findRoute(routeId, shop))
        require(rows.map { it.customerId }.distinct().size == rows.size)
        require(rows.map { it.visitOrder }.distinct().size == rows.size)
        val current = assignmentsForSync(routeId, shop)
        current.forEach { removeRouteCustomer(it.customerId, shop) }
        rows.forEach { row ->
            require(row.routeId == routeId && row.tenant.shopId == shop)
            checkNotNull(findCustomer(row.customerId, shop))
            removeRouteCustomer(row.customerId, shop)
            if (findAssignment(routeId, row.customerId, shop) == null) insertRouteCustomer(row.copy(activeSlot = 1))
            else updateAssignment(row.copy(activeSlot = 1))
        }
    }

    @Transaction
    open suspend fun assignCustomer(assignment: RouteCustomerEntity) {
        require(assignment.visitOrder >= 0) { "Visit order cannot be negative" }
        val shop = checkNotNull(assignment.tenant.shopId) { "Selecciona una tienda" }
        checkNotNull(findCustomer(assignment.customerId, shop)) { "Customer not found in shop" }.also { check(it.deletedAt == null && it.isActive) { "Customer is inactive" } }
        checkNotNull(findRoute(assignment.routeId, shop)) { "Route not found in shop" }.also { check(it.isActive) { "Route is inactive" } }
        removeRouteCustomer(assignment.customerId, shop)
        val historical = findAssignment(assignment.routeId, assignment.customerId, shop)
        if (historical == null) insertRouteCustomer(assignment.copy(activeSlot = 1)) else updateAssignment(assignment.copy(activeSlot = 1))
    }

    @Transaction
    open suspend fun unassignCustomer(customerId: UUID, shopId: String? = null): Boolean = removeRouteCustomer(customerId, shopId) == 1

    @Transaction
    open suspend fun reorderRoute(routeId: UUID, customerIds: List<UUID>, shopId: String? = null) {
        require(customerIds.distinct().size == customerIds.size) { "Duplicate customer IDs" }
        require(customerIds.size < Int.MAX_VALUE) { "Too many route customers" }
        // Vacate unique (route_id, visit_order) slots before assigning the final order.
        customerIds.forEachIndexed { index, customerId ->
            check(updateVisitOrder(routeId, customerId, Int.MIN_VALUE + index, shopId) == 1) { "Customer is not assigned to this route" }
        }
        customerIds.forEachIndexed { order, customerId ->
            check(updateVisitOrder(routeId, customerId, order, shopId) == 1) { "Customer is not assigned to this route" }
        }
    }
}
