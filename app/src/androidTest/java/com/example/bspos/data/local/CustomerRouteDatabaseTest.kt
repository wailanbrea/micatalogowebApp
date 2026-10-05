package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.repository.CustomerRepositoryImpl
import com.example.bspos.data.repository.RouteRepositoryImpl
import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.RouteCustomer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CustomerRouteDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var customers: CustomerRepositoryImpl
    private lateinit var routes: RouteRepositoryImpl
    private val at = Instant.parse("2026-09-20T20:00:00Z")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        customers = CustomerRepositoryImpl(db.customerRouteDao())
        routes = RouteRepositoryImpl(db.customerRouteDao())
    }
    @After fun tearDown() = db.close()

    @Test fun customersRoundTripMoneyCoordinatesAndSoftDelete() = runTest {
        val customer = Customer(UUID.randomUUID(), "Colmado", "Ana", "8095550000", "8095550001", "Calle 1", "Frente al parque", "123", "LUN,VIE", 500_00, 25_00, 18.45, -69.93, "Paga viernes", createdAt = at, updatedAt = at)
        customers.insert(customer)
        assertEquals(customer, customers.findById(customer.id))
        assertTrue(customers.update(customer.copy(balance = 0, isActive = false, updatedAt = at.plusSeconds(1))))
        assertFalse(customers.observeAll().first().single().isActive)
        assertTrue(customers.softDelete(customer.id, at.plusSeconds(2)))
        assertTrue(customers.observeAll().first().isEmpty())
        assertEquals(at.plusSeconds(2), customers.findById(customer.id)!!.deletedAt)
    }

    @Test fun routeCodeAndCustomerAssignmentAreUnique() = runTest {
        val customer = Customer(UUID.randomUUID(), "Cliente", createdAt = at, updatedAt = at)
        val routeA = CommercialRoute(UUID.randomUUID(), "Norte", "N", createdAt = at, updatedAt = at)
        val routeB = CommercialRoute(UUID.randomUUID(), "Sur", "S", createdAt = at, updatedAt = at)
        customers.insert(customer); routes.insert(routeA); routes.insert(routeB)
        expectFailure<SQLiteConstraintException> { routes.insert(routeB.copy(id = UUID.randomUUID(), name = "Duplicada", code = "N")) }
        routes.assign(RouteCustomer(routeA.id, customer.id, 0))
        assertEquals(listOf(RouteCustomer(routeA.id, customer.id, 0)), routes.observeCustomers(routeA.id).first())
        routes.assign(RouteCustomer(routeB.id, customer.id, 0))
        assertTrue(routes.observeCustomers(routeA.id).first().isEmpty())
        assertEquals(listOf(RouteCustomer(routeB.id, customer.id, 0)), routes.observeCustomers(routeB.id).first())
    }

    @Test fun inactiveOrDeletedCustomerAndInactiveRouteCannotBeAssigned() = runTest {
        val customer = Customer(UUID.randomUUID(), "Cliente", createdAt = at, updatedAt = at)
        val route = CommercialRoute(UUID.randomUUID(), "Ruta", "R", createdAt = at, updatedAt = at)
        customers.insert(customer); routes.insert(route)
        assertTrue(customers.update(customer.copy(isActive = false, updatedAt = at)))
        expectFailure<IllegalStateException> { routes.assign(RouteCustomer(route.id, customer.id)) }
        assertTrue(customers.update(customer.copy(updatedAt = at)))
        assertTrue(routes.update(route.copy(isActive = false, updatedAt = at)))
        expectFailure<IllegalStateException> { routes.assign(RouteCustomer(route.id, customer.id)) }
        assertTrue(routes.update(route.copy(updatedAt = at)))
        assertTrue(customers.softDelete(customer.id, at))
        expectFailure<IllegalStateException> { routes.assign(RouteCustomer(route.id, customer.id)) }
    }

    @Test fun reorderingIsAtomicAndRouteDeletionCascadesAssignments() = runTest {
        val route = CommercialRoute(UUID.randomUUID(), "Ruta", "R", createdAt = at, updatedAt = at)
        val first = Customer(UUID.randomUUID(), "A", createdAt = at, updatedAt = at)
        val second = Customer(UUID.randomUUID(), "B", createdAt = at, updatedAt = at)
        routes.insert(route); customers.insert(first); customers.insert(second)
        routes.assign(RouteCustomer(route.id, first.id, 0)); routes.assign(RouteCustomer(route.id, second.id, 1))
        expectFailure<IllegalArgumentException> { routes.reorder(route.id, listOf(first.id, first.id)) }
        expectFailure<IllegalStateException> { routes.reorder(route.id, listOf(second.id, UUID.randomUUID())) }
        assertEquals(listOf(first.id, second.id), routes.observeCustomers(route.id).first().map { it.customerId })
        routes.reorder(route.id, listOf(second.id, first.id))
        assertEquals(listOf(second.id, first.id), routes.observeCustomers(route.id).first().map { it.customerId })
        db.openHelper.writableDatabase.execSQL("DELETE FROM routes WHERE id = ?", arrayOf(route.id.toString()))
        assertTrue(routes.observeCustomers(route.id).first().isEmpty())
        assertEquals(first, customers.findById(first.id))
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit) {
        try { block() } catch (failure: Throwable) { if (failure is T) return; throw failure }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
