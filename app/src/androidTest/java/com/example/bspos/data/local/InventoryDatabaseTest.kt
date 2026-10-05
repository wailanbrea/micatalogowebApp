package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.database.InventoryIntegrity
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.data.repository.InventoryAdjustmentReasonRepositoryImpl
import com.example.bspos.data.repository.InventoryRepositoryImpl
import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.InventoryAdjustmentReason
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryMovement
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.InventoryReferenceType
import com.example.bspos.domain.repository.InventoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InventoryDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var inventory: InventoryRepository
    private lateinit var reasons: InventoryAdjustmentReasonRepositoryImpl
    private val at = Instant.parse("2026-09-20T14:00:00.123Z")
    private val productId = UUID.randomUUID()
    private val main = InventoryLocation.MAIN

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).addCallback(InventoryIntegrity).build()
        val local = InventoryLocalDataSource(database.inventoryDao(), database.inventoryAdjustmentReasonDao())
        inventory = InventoryRepositoryImpl(local, database.productDao(), database.inventoryDao(),
            com.example.bspos.data.micatalogo.RemoteMutationRecorder(RoomDatabaseTransactor(database),
                database.categoryDao(), database.operationOutboxDao(), com.example.bspos.data.micatalogo.PosSaleSyncScheduler(ApplicationProvider.getApplicationContext<Context>())))
        reasons = InventoryAdjustmentReasonRepositoryImpl(local)
        runBlocking {
            val categoryId = UUID.randomUUID()
            val unitId = UUID.randomUUID()
            database.categoryDao().insert(CategoryEntity(id = categoryId, name = "Test", createdAt = at, updatedAt = at))
            database.unitOfMeasureDao().insert(UnitOfMeasureEntity(id = unitId, name = "Unidad", abbreviation = "ud", createdAt = at, updatedAt = at))
            database.productDao().insert(ProductEntity(
                id = productId, name = "Producto", internalCode = "INV-001", categoryId = categoryId,
                unitId = unitId, salePrice = 3500, averageCost = 2000, createdAt = at, updatedAt = at
            ))
        }
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun newDatabaseHasNoInventedStockOrReasons() = runTest {
        assertNull(inventory.findStock(productId, main))
        assertTrue(inventory.observeStock(main).first().isEmpty())
        assertTrue(reasons.observeAll().first().isEmpty())
    }

    @Test
    fun stockEqualsLedgerSumAndAllAuditFieldsRoundTrip() = runTest {
        val initial = movement(50, 0, InventoryMovementType.INITIAL)
        val purchase = movement(20, 50, InventoryMovementType.PURCHASE).copy(
            referenceType = InventoryReferenceType.PURCHASE, referenceId = UUID.randomUUID(),
            notes = "Recepción", createdBy = "operador", createdAt = at.plusMillis(1)
        )
        val sale = movement(-5, 70, InventoryMovementType.SALE).copy(createdAt = at.plusMillis(2))
        inventory.recordMovements(listOf(initial, purchase, sale))
        val stock = inventory.findStock(productId, main)!!
        val ledger = inventory.observeMovements(productId, main).first()
        assertEquals(65L, stock.quantity)
        assertEquals(0L, stock.reservedQuantity)
        assertEquals(sale.createdAt, stock.updatedAt)
        assertEquals(listOf(sale, purchase, initial), ledger)
        assertEquals(stock.quantity, ledger.sumOf { it.quantity })
        assertEquals(-10000L, inventory.findMovement(sale.id)!!.totalCost)
        assertEquals(stock, stock.toEntity().toDomain())
    }

    @Test
    fun warehouseAndRoutesHaveIndependentBalancesAndStableStockIds() = runTest {
        val route = InventoryLocation.route(UUID.randomUUID())
        inventory.recordMovements(listOf(movement(50, 0, InventoryMovementType.INITIAL)))
        val originalId = inventory.findStock(productId, main)!!.id
        inventory.recordMovements(listOf(
            movement(-10, 50, InventoryMovementType.ROUTE_LOAD_OUT),
            movement(10, 0, InventoryMovementType.ROUTE_LOAD_IN, route)
        ))
        assertEquals(40L, inventory.findStock(productId, main)!!.quantity)
        assertEquals(10L, inventory.findStock(productId, route)!!.quantity)
        assertEquals(originalId, inventory.findStock(productId, main)!!.id)
        assertEquals(2000L, database.productDao().findById(productId)!!.averageCost)
        assertEquals(1, inventory.observeMovements(productId, route).first().size)
    }

    @Test
    fun duplicateMovementRollsBackStockWriteRatherThanReplacingHistory() = runTest {
        val original = movement(5, 0, InventoryMovementType.PURCHASE)
        inventory.recordMovements(listOf(original))
        expectFailure<SQLiteConstraintException> {
            inventory.recordMovements(listOf(movement(2, 5, InventoryMovementType.PURCHASE).copy(id = original.id)))
        }
        assertEquals(5L, inventory.findStock(productId, main)!!.quantity)
        assertEquals(listOf(original), inventory.observeMovements(productId, main).first())
    }

    @Test
    fun failureInSecondMovementRollsBackTheWholeBatch() = runTest {
        inventory.recordMovements(listOf(movement(10, 0, InventoryMovementType.INITIAL)))
        val missingProduct = UUID.randomUUID()
        expectFailure<SQLiteConstraintException> {
            inventory.recordMovements(listOf(
                movement(-3, 10, InventoryMovementType.TRANSFER_OUT),
                movement(3, 0, InventoryMovementType.TRANSFER_IN).copy(productId = missingProduct)
            ))
        }
        assertEquals(10L, inventory.findStock(productId, main)!!.quantity)
        assertNull(inventory.findStock(missingProduct, main))
        assertEquals(1, inventory.observeMovements(productId, main).first().size)
    }

    @Test
    fun concurrentStaleWritersCannotLoseAnInventoryChange() = runTest {
        inventory.recordMovements(listOf(movement(10, 0, InventoryMovementType.INITIAL)))
        val results = List(2) {
            async(Dispatchers.IO) {
                runCatching { inventory.recordMovements(listOf(movement(-3, 10, InventoryMovementType.SALE))) }
            }
        }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is IllegalStateException)
        val ledger = inventory.observeMovements(productId, main).first()
        assertEquals(7L, inventory.findStock(productId, main)!!.quantity)
        assertEquals(7L, ledger.sumOf { it.quantity })
        assertEquals(2, ledger.size)
    }

    @Test
    fun negativeStockRequiresExplicitOptIn() = runTest {
        val sale = movement(-2, 0, InventoryMovementType.SALE)
        expectFailure<IllegalStateException> { inventory.recordMovements(listOf(sale)) }
        assertNull(inventory.findStock(productId, main))
        inventory.recordMovements(listOf(sale), allowNegativeStock = true)
        assertEquals(-2L, inventory.findStock(productId, main)!!.quantity)
        assertEquals(listOf(sale), inventory.observeMovements(productId, main).first())
    }

    @Test
    fun invalidArithmeticSignsAndReferencesNeverCreateStock() = runTest {
        val valid = movement(5, 0, InventoryMovementType.PURCHASE)
        val invalid = listOf(
            valid.copy(quantity = 0, newQuantity = 0, totalCost = 0),
            valid.copy(newQuantity = 99), valid.copy(totalCost = 1),
            valid.copy(unitCost = -1), valid.copy(type = InventoryMovementType.SALE),
            valid.copy(referenceType = InventoryReferenceType.PURCHASE),
            valid.copy(referenceId = UUID.randomUUID()),
            valid.copy(type = InventoryMovementType.ADJUSTMENT_IN)
        )
        for (entry in invalid) expectFailure<IllegalArgumentException> { inventory.recordMovements(listOf(entry)) }
        expectFailure<ArithmeticException> {
            inventory.recordMovements(listOf(valid.copy(previousQuantity = Long.MAX_VALUE)))
        }
        expectFailure<ArithmeticException> {
            inventory.recordMovements(listOf(valid.copy(unitCost = Long.MAX_VALUE)))
        }
        expectFailure<IllegalArgumentException> { inventory.recordMovements(emptyList()) }
        expectFailure<IllegalArgumentException> { inventory.recordMovements(listOf(valid, valid)) }
        assertNull(inventory.findStock(productId, main))
        assertTrue(inventory.observeMovements(productId, main).first().isEmpty())
    }

    @Test
    fun adjustmentsRequireExistingActiveCompatibleReasonAndNotes() = runTest {
        val reason = InventoryAdjustmentReason(
            id = UUID.randomUUID(), name = "Sobrante", direction = AdjustmentDirection.IN,
            createdAt = at, updatedAt = at
        )
        val adjustment = movement(3, 0, InventoryMovementType.ADJUSTMENT_IN).copy(reasonId = reason.id, notes = "Conteo revisado")
        expectFailure<IllegalStateException> { inventory.recordMovements(listOf(adjustment)) }
        reasons.insert(reason.copy(isActive = false))
        expectFailure<IllegalStateException> { inventory.recordMovements(listOf(adjustment)) }
        assertTrue(reasons.update(reason.copy(direction = AdjustmentDirection.OUT)))
        expectFailure<IllegalArgumentException> { inventory.recordMovements(listOf(adjustment)) }
        assertTrue(reasons.update(reason))
        expectFailure<IllegalArgumentException> { inventory.recordMovements(listOf(adjustment.copy(notes = " "))) }
        inventory.recordMovements(listOf(adjustment))
        assertEquals(reason, reasons.findById(reason.id))
        assertTrue(reasons.softDelete(reason.id, at.plusSeconds(1)))
        assertTrue(reasons.observeAll().first().isEmpty())
        assertEquals(at.plusSeconds(1), reasons.findById(reason.id)!!.deletedAt)
        assertEquals(adjustment, inventory.findMovement(adjustment.id))
        expectFailure<IllegalStateException> {
            inventory.recordMovements(listOf(adjustment.copy(id = UUID.randomUUID(), previousQuantity = 3, newQuantity = 6)))
        }
        assertEquals(3L, inventory.findStock(productId, main)!!.quantity)
    }

    @Test
    fun existingLedgerCannotBeReinitializedOrBackdated() = runTest {
        inventory.recordMovements(listOf(movement(5, 0, InventoryMovementType.INITIAL)))
        expectFailure<IllegalStateException> {
            inventory.recordMovements(listOf(movement(1, 5, InventoryMovementType.INITIAL)))
        }
        expectFailure<IllegalStateException> {
            inventory.recordMovements(listOf(movement(1, 5, InventoryMovementType.PURCHASE).copy(createdAt = at.minusMillis(1))))
        }
        assertEquals(5L, inventory.findStock(productId, main)!!.quantity)
    }

    @Test
    fun sqlCannotUpdateOrDeleteLedgerOrItsReferencedProduct() = runTest {
        val original = movement(5, 0, InventoryMovementType.INITIAL)
        inventory.recordMovements(listOf(original))
        val sql = database.openHelper.writableDatabase
        expectFailure<SQLiteConstraintException> { sql.execSQL("UPDATE inventory_movements SET notes = 'changed'") }
        expectFailure<SQLiteConstraintException> { sql.execSQL("DELETE FROM inventory_movements") }
        expectFailure<SQLiteConstraintException> { sql.execSQL("DELETE FROM products") }
        assertEquals(original, inventory.findMovement(original.id))
    }

    @Test
    fun referencedReasonHasForeignKeyProtection() = runTest {
        val reason = InventoryAdjustmentReason(id = UUID.randomUUID(), name = "Sobrante", direction = AdjustmentDirection.BOTH, createdAt = at, updatedAt = at)
        reasons.insert(reason)
        val adjustment = movement(1, 0, InventoryMovementType.ADJUSTMENT_IN).copy(reasonId = reason.id, notes = "Revisión")
        inventory.recordMovements(listOf(adjustment))
        expectFailure<SQLiteConstraintException> {
            database.openHelper.writableDatabase.execSQL("DELETE FROM inventory_adjustment_reasons")
        }
        assertNotNull(reasons.findById(reason.id))
    }

    @Test
    fun stockUniqueIndexRejectsSecondRowForSameProductAndLocation() = runTest {
        inventory.recordMovements(listOf(movement(1, 0, InventoryMovementType.INITIAL)))
        expectFailure<SQLiteConstraintException> {
            database.openHelper.writableDatabase.execSQL("""
                INSERT INTO inventory_stock (id, product_id, location_type, location_id, quantity, reserved_quantity, updated_at)
                SELECT ?, product_id, location_type, location_id, quantity, reserved_quantity, updated_at FROM inventory_stock
            """.trimIndent(), arrayOf(UUID.randomUUID().toString()))
        }
        assertEquals(1, inventory.observeStock(main).first().size)
    }

    @Test
    fun kardexDateBoundsAreHalfOpenAndTimestampTiesKeepAppendOrder() = runTest {
        val first = movement(5, 0, InventoryMovementType.INITIAL)
        val second = movement(2, 5, InventoryMovementType.PURCHASE)
        val third = movement(-1, 7, InventoryMovementType.SALE).copy(createdAt = at.plusMillis(1))
        inventory.recordMovements(listOf(first, second, third))
        assertEquals(listOf(second, first), inventory.observeMovements(productId, main, at, at.plusMillis(1)).first())
        assertEquals(listOf(third), inventory.observeMovements(productId, main, at.plusMillis(1)).first())
        expectFailure<IllegalArgumentException> { inventory.observeMovements(productId, main, at, at) }
    }

    @Test
    fun stockFlowNotifiesAnExistingCollectorAfterCommit() = runTest {
        val subscribed = CompletableDeferred<Unit>()
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            inventory.observeStock(main).onEach { subscribed.complete(Unit) }.first { it.isNotEmpty() }
        }
        subscribed.await()
        inventory.recordMovements(listOf(movement(4, 0, InventoryMovementType.INITIAL)))
        assertEquals(4L, observed.await().single().quantity)
        assertEquals(1, inventory.observeMovements(productId, main).first().size)
    }

    @Test
    fun outerTransactionCanRollbackCatalogAndInventoryTogether() = runTest {
        val transactor = RoomDatabaseTransactor(database)
        val reason = InventoryAdjustmentReason(id = UUID.randomUUID(), name = "Sobrante", direction = AdjustmentDirection.IN, createdAt = at, updatedAt = at)
        expectFailure<IllegalStateException> {
            transactor.runInTransaction {
                reasons.insert(reason)
                inventory.recordMovements(listOf(movement(4, 0, InventoryMovementType.ADJUSTMENT_IN).copy(reasonId = reason.id, notes = "Documento")))
                error("Document could not be completed")
            }
        }
        assertNull(reasons.findById(reason.id))
        assertNull(inventory.findStock(productId, main))
        assertFalse(inventory.observeMovements(productId, main).first().isNotEmpty())
    }

    private fun movement(quantity: Long, previous: Long, type: InventoryMovementType, location: InventoryLocation = main) = InventoryMovement(
        id = UUID.randomUUID(), productId = productId, location = location, type = type,
        quantity = quantity, previousQuantity = previous, newQuantity = Math.addExact(previous, quantity),
        unitCost = 2000, totalCost = Math.multiplyExact(quantity, 2000), createdAt = at
    )

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit) {
        try { block() } catch (failure: Throwable) {
            if (failure is T) return
            throw failure
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
