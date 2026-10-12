package com.example.bspos.data.local.tenant

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.*
import com.example.bspos.data.local.entity.*
import com.example.bspos.data.micatalogo.*
import com.example.bspos.data.micatalogo.dto.StoreRecordDto
import com.example.bspos.data.repository.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class StoreRecordSyncTest {
    private val at = Instant.parse("2026-10-12T10:00:00Z")

    @Test fun catalogDefinitionsCommitWithOutboxAndStaleReadBackNeverOverwritesPendingEdits() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val shops = tenantTestScope("qa-records-a")
            val recorder = RemoteMutationRecorder(RoomDatabaseTransactor(db), db.categoryDao(), db.operationOutboxDao(), PosSaleSyncScheduler(context))
            val local = CatalogLocalDataSource(db.categoryDao(), db.unitOfMeasureDao(), db.productDao(), db.supplierDao())
            val categories = CategoryRepositoryImpl(local, shops, recorder)
            val units = UnitOfMeasureRepositoryImpl(local, shops, recorder)
            val category = Category(UUID.randomUUID(), "QA categoría", createdAt = at, updatedAt = at)
            val unit = UnitOfMeasure(UUID.randomUUID(), "QA caja", "caj", createdAt = at, updatedAt = at)
            categories.insert(category); units.insert(unit)
            assertEquals(2, db.operationOutboxDao().activeForShop("qa-records-a").size)
            assertTrue(db.operationOutboxDao().activeForShop("qa-records-b").isEmpty())
            units.update(unit.copy(name = "Caja editada", updatedAt = at.plusSeconds(1)))
            val pending = db.operationOutboxDao().activeForShop("qa-records-a").map { Json.parseToJsonElement(it.payload).jsonObject.getValue("record_id").jsonPrimitive.content }.toSet()
            val projection = StoreRecordProjection(db.unitOfMeasureDao(), db.inventoryAdjustmentReasonDao(), db.customerRouteDao(), db.routeLoadDao(), db.productDao(), db.inventoryDao())
            val payload = buildJsonObject { put("name", "Vieja caja"); put("abbreviation", "cj"); put("is_active", true); put("is_deleted", false); put("created_at", at.toString()) }
            val snapshot = listOf(StoreRecordDto("unit", unit.id.toString(), payload, at.toString()))
            projection.apply("qa-records-a", snapshot, pending)
            assertEquals("Caja editada", units.findById(unit.id)!!.name)
            db.operationOutboxDao().activeForShop("qa-records-a").forEach { db.operationOutboxDao().confirmSent(it.id, it.shopId) }
            projection.apply("qa-records-a", snapshot, emptySet())
            projection.apply("qa-records-a", snapshot, emptySet())
            assertEquals("Vieja caja", units.findById(unit.id)!!.name)
            assertEquals(1, db.unitOfMeasureDao().observeAll("qa-records-a").first().size)
            assertNull(db.unitOfMeasureDao().findById(unit.id, "qa-records-b"))
        } finally { db.close() }
    }

    @Test fun failedReorderRollsBackBothPositionsAndEveryEnqueuedMutation() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val recorder = RemoteMutationRecorder(RoomDatabaseTransactor(db), db.categoryDao(), db.operationOutboxDao(), PosSaleSyncScheduler(context))
            val categories = CategoryRepositoryImpl(CatalogLocalDataSource(db.categoryDao(), db.unitOfMeasureDao(), db.productDao(), db.supplierDao()), tenantTestScope("qa-order"), recorder)
            val category = Category(UUID.randomUUID(), "Una", sortOrder = 9, createdAt = at, updatedAt = at)
            categories.insert(category)
            val before = db.operationOutboxDao().activeForShop("qa-order").map { it.payload }
            assertTrue(runCatching { categories.reorder(listOf(category.id, UUID.randomUUID()), at.plusSeconds(1)) }.isFailure)
            assertEquals(9, categories.findById(category.id)!!.sortOrder)
            assertEquals(before, db.operationOutboxDao().activeForShop("qa-order").map { it.payload })
        } finally { db.close() }
    }
}
