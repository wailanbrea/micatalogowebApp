package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.SupplierEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import com.example.bspos.data.repository.InventoryDocumentRepositoryImpl
import com.example.bspos.domain.model.InventoryCount
import com.example.bspos.domain.model.InventoryCountItem
import com.example.bspos.domain.model.InventoryCountStatus
import com.example.bspos.domain.model.Purchase
import com.example.bspos.domain.model.PurchaseItem
import com.example.bspos.domain.model.StockEntry
import com.example.bspos.domain.model.StockEntryItem
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
class InventoryDocumentDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var documents: InventoryDocumentRepositoryImpl
    private val at = Instant.parse("2026-09-20T19:00:00.000Z")
    private val productId = UUID.randomUUID()
    private val supplierId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        documents = InventoryDocumentRepositoryImpl(database.inventoryDocumentDao())
        val categoryId = UUID.randomUUID()
        val unitId = UUID.randomUUID()
        database.categoryDao().insert(CategoryEntity(categoryId, "Cat", createdAt = at, updatedAt = at))
        database.unitOfMeasureDao().insert(UnitOfMeasureEntity(unitId, "Unidad", "ud", createdAt = at, updatedAt = at))
        database.productDao().insert(ProductEntity(productId, "Producto", "DOC-001", categoryId = categoryId, unitId = unitId, salePrice = 1, createdAt = at, updatedAt = at))
        database.supplierDao().insert(SupplierEntity(supplierId, "Proveedor", createdAt = at, updatedAt = at))
    }

    @After fun tearDown() = database.close()

    @Test
    fun purchasePersistsTotalsAndLinesAtomically() = runTest {
        val purchase = Purchase(UUID.randomUUID(), supplierId, "COMP-1", date = at, subtotal = 2200, discount = 200, tax = 300, total = 2300, createdAt = at, updatedAt = at)
        val line = PurchaseItem(UUID.randomUUID(), purchase.id, productId, 2, 1100, discount = 200, tax = 300, subtotal = 2300)
        // Correct financial line: subtotal after its discount and tax is the document total.
        val correctPurchase = purchase.copy(subtotal = 2200, discount = 200, tax = 300, total = 2300)
        val correctLine = line.copy(subtotal = 2300)
        expectFailure<IllegalArgumentException> { documents.insertPurchase(correctPurchase, listOf(correctLine)) }
        val validPurchase = correctPurchase.copy(subtotal = 2300, discount = 0, tax = 0, total = 2300)
        documents.insertPurchase(validPurchase, listOf(correctLine.copy(purchaseId = validPurchase.id)))
        assertEquals(listOf(correctLine.copy(purchaseId = validPurchase.id)), documents.observePurchaseItems(validPurchase.id).first())
        assertEquals(listOf(validPurchase), documents.observePurchasesForSupplier(supplierId).first())
    }

    @Test
    fun invalidDocumentsDoNotCreateHeadersOrLines() = runTest {
        val purchase = Purchase(UUID.randomUUID(), supplierId, "P", date = at, subtotal = 100, total = 100, createdAt = at, updatedAt = at)
        val validLine = PurchaseItem(UUID.randomUUID(), purchase.id, productId, 1, 100, subtotal = 100)
        expectFailure<IllegalArgumentException> { documents.insertPurchase(purchase.copy(total = 99), listOf(validLine)) }
        expectFailure<IllegalArgumentException> { documents.insertPurchase(purchase, emptyList()) }
        expectFailure<IllegalArgumentException> { documents.insertPurchase(purchase, listOf(validLine.copy(quantity = 0))) }
        expectFailure<IllegalArgumentException> { documents.insertPurchase(purchase, listOf(validLine, validLine.copy(id = UUID.randomUUID()))) }
        assertNull(database.inventoryDocumentDao().findPurchase(purchase.id))
        assertTrue(documents.observePurchaseItems(purchase.id).first().isEmpty())
    }

    @Test
    fun supplierAndProductForeignKeysProtectPurchaseButEntrySupplierCanBeNull() = runTest {
        val purchase = Purchase(UUID.randomUUID(), supplierId, "P", date = at, subtotal = 100, total = 100, createdAt = at, updatedAt = at)
        documents.insertPurchase(purchase, listOf(PurchaseItem(UUID.randomUUID(), purchase.id, productId, 1, 100, subtotal = 100)))
        expectFailure<SQLiteConstraintException> { database.openHelper.writableDatabase.execSQL("DELETE FROM suppliers") }
        expectFailure<SQLiteConstraintException> { database.openHelper.writableDatabase.execSQL("DELETE FROM products") }
        val entry = StockEntry(UUID.randomUUID(), "E", supplierId, at, createdAt = at)
        documents.insertStockEntry(entry, listOf(StockEntryItem(UUID.randomUUID(), entry.id, productId, 1, 0, 0)))
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchases")
        database.openHelper.writableDatabase.execSQL("DELETE FROM suppliers")
        assertEquals(null, database.inventoryDocumentDao().findStockEntry(entry.id)!!.supplierId)
    }

    @Test
    fun deletingHeaderCascadesOnlyItsLines() = runTest {
        val entry = StockEntry(UUID.randomUUID(), "E", date = at, createdAt = at)
        val item = StockEntryItem(UUID.randomUUID(), entry.id, productId, 2, 50, 100)
        documents.insertStockEntry(entry, listOf(item))
        database.openHelper.writableDatabase.execSQL("DELETE FROM stock_entries WHERE id = ?", arrayOf(entry.id.toString()))
        assertNull(database.inventoryDocumentDao().findStockEntry(entry.id))
        assertTrue(documents.observeStockEntryItems(entry.id).first().isEmpty())
        assertEquals(productId, database.productDao().findById(productId)!!.id)
    }

    @Test
    fun countLifecycleAndDifferenceAreStrict() = runTest {
        val count = InventoryCount(UUID.randomUUID(), at)
        val item = InventoryCountItem(UUID.randomUUID(), count.id, productId, systemQuantity = 10, physicalQuantity = 7, difference = -3)
        documents.insertCount(count, listOf(item))
        assertEquals(listOf(count), documents.observeCounts().first())
        assertEquals(listOf(item), documents.observeCountItems(count.id).first())
        expectFailure<IllegalArgumentException> { documents.updateCountStatus(count.id, InventoryCountStatus.COMPLETED, at) }
        documents.updateCountStatus(count.id, InventoryCountStatus.IN_PROGRESS, null)
        documents.updateCountStatus(count.id, InventoryCountStatus.COMPLETED, at.plusSeconds(1))
        assertEquals(InventoryCountStatus.COMPLETED, database.inventoryDocumentDao().findCount(count.id)!!.status)
        expectFailure<IllegalArgumentException> { documents.updateCountStatus(count.id, InventoryCountStatus.CANCELLED, null) }
        val invalid = count.copy(id = UUID.randomUUID())
        expectFailure<IllegalArgumentException> { documents.insertCount(invalid, listOf(item.copy(countId = invalid.id, difference = 1))) }
        expectFailure<IllegalArgumentException> { documents.insertCount(invalid, emptyList()) }
        assertNull(database.inventoryDocumentDao().findCount(invalid.id))
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit) {
        try { block() } catch (failure: Throwable) { if (failure is T) return; throw failure }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
