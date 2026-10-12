package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import com.example.bspos.data.repository.RouteLoadRepositoryImpl
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.domain.model.RouteLoad
import com.example.bspos.domain.model.RouteLoadItem
import com.example.bspos.domain.model.RouteLoadStatus
import com.example.bspos.domain.model.CommercialRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RouteLoadDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var loads: RouteLoadRepositoryImpl
    private val at = Instant.parse("2026-09-20T21:00:00Z")
    private val routeId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        val tenant = com.example.bspos.data.local.entity.TenantMetadata.assigned("loads-qa")
        loads = RouteLoadRepositoryImpl(db.routeLoadDao(), com.example.bspos.data.local.tenant.tenantTestScope("loads-qa"),
            com.example.bspos.data.micatalogo.RemoteMutationRecorder(com.example.bspos.core.database.RoomDatabaseTransactor(db),
                db.categoryDao(), db.operationOutboxDao(), com.example.bspos.data.micatalogo.PosSaleSyncScheduler(ApplicationProvider.getApplicationContext<Context>())),
            db.productDao(), db.inventoryDao())
        db.customerRouteDao().insertRoute(CommercialRoute(routeId, "Ruta", "R", createdAt = at, updatedAt = at).toEntity().copy(tenant = tenant))
        val categoryId = UUID.randomUUID(); val unitId = UUID.randomUUID()
        db.categoryDao().insert(CategoryEntity(categoryId, "Cat", createdAt = at, updatedAt = at, tenant = tenant))
        db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unitId, "Unidad", "ud", createdAt = at, updatedAt = at, tenant = tenant))
        db.productDao().insert(ProductEntity(productId, "Producto", "LOAD-001", categoryId = categoryId, unitId = unitId, salePrice = 100, averageCost = 50, createdAt = at, updatedAt = at, tenant = tenant))
    }
    @After fun tearDown() = db.close()

    @Test fun loadCapturesCostAndFollowsLifecycle() = runTest {
        val load = RouteLoad(UUID.randomUUID(), routeId, at, notes = "Salida", createdAt = at)
        val item = RouteLoadItem(UUID.randomUUID(), load.id, productId, 7, 50)
        loads.insert(load, listOf(item))
        assertEquals(listOf(load), loads.observeForRoute(routeId).drop(1).first())
        assertEquals(listOf(item), loads.observeItems(load.id).drop(1).first())
        loads.updateStatus(load.id, RouteLoadStatus.SETTLED)
        assertEquals(RouteLoadStatus.SETTLED, db.routeLoadDao().findById(load.id, "loads-qa")!!.status)
        expectFailure<IllegalArgumentException> { loads.updateStatus(load.id, RouteLoadStatus.CANCELLED) }
    }

    @Test fun invalidDocumentsRollbackAndForeignKeysProtectReferences() = runTest {
        val load = RouteLoad(UUID.randomUUID(), routeId, at, createdAt = at)
        val item = RouteLoadItem(UUID.randomUUID(), load.id, productId, 1, 50)
        expectFailure<IllegalArgumentException> { loads.insert(load.copy(status = RouteLoadStatus.SETTLED), listOf(item)) }
        expectFailure<IllegalArgumentException> { loads.insert(load, emptyList()) }
        expectFailure<IllegalArgumentException> { loads.insert(load, listOf(item.copy(quantity = 0))) }
        expectFailure<IllegalArgumentException> { loads.insert(load, listOf(item, item.copy(id = UUID.randomUUID()))) }
        assertTrue(loads.observeForRoute(routeId).first().isEmpty())
        loads.insert(load, listOf(item))
        expectFailure<SQLiteConstraintException> { db.openHelper.writableDatabase.execSQL("DELETE FROM routes") }
        expectFailure<SQLiteConstraintException> { db.openHelper.writableDatabase.execSQL("DELETE FROM products") }
        db.openHelper.writableDatabase.execSQL("DELETE FROM route_loads WHERE id = ?", arrayOf(load.id.toString()))
        assertNull(db.routeLoadDao().findById(load.id))
        assertTrue(loads.observeItems(load.id).first().isEmpty())
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit) {
        try { block() } catch (failure: Throwable) { if (failure is T) return; throw failure }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
