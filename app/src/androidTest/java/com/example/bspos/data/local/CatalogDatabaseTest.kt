package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.repository.CategoryRepositoryImpl
import com.example.bspos.data.repository.ProductRepositoryImpl
import com.example.bspos.data.repository.SupplierRepositoryImpl
import com.example.bspos.data.repository.UnitOfMeasureRepositoryImpl
import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.Supplier
import com.example.bspos.domain.model.UnitOfMeasure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
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

/** Every test uses a separate in-memory database; never opens the business database. */
@RunWith(AndroidJUnit4::class)
class CatalogDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var categories: CategoryRepositoryImpl
    private lateinit var units: UnitOfMeasureRepositoryImpl
    private lateinit var products: ProductRepositoryImpl
    private lateinit var suppliers: SupplierRepositoryImpl
    private val at = Instant.parse("2026-09-20T12:30:45.123Z")
    private val category = Category(
        id = UUID.randomUUID(), name = "Bebidas", description = "Frías", icon = "drink",
        sortOrder = 7, createdAt = at, updatedAt = at
    )
    private val unit = UnitOfMeasure(
        id = UUID.randomUUID(), name = "Unidad", abbreviation = "ud", createdAt = at, updatedAt = at
    )
    private val product = Product(
        id = UUID.randomUUID(), name = "Agua", internalCode = "AG-001", barcode = "123456789",
        categoryId = category.id, unitId = unit.id, description = "Botella",
        salePrice = 3500, wholesalePrice = 3000, averageCost = 2000, lastPurchaseCost = 2100,
        minimumStock = 5, imagePath = "products/water.jpg", thumbnailPath = "products/water-thumb.jpg",
        tracksExpiration = true, createdAt = at, updatedAt = at
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val local = CatalogLocalDataSource(
            database.categoryDao(), database.unitOfMeasureDao(), database.productDao(), database.supplierDao()
        )
        categories = CategoryRepositoryImpl(local)
        units = UnitOfMeasureRepositoryImpl(local)
        products = ProductRepositoryImpl(local, com.example.bspos.data.micatalogo.RemoteMutationRecorder(
            RoomDatabaseTransactor(database), database.categoryDao(), database.operationOutboxDao(),
            com.example.bspos.data.micatalogo.PosSaleSyncScheduler(context)))
        suppliers = SupplierRepositoryImpl(local)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun databaseStartsEmptyWithoutHardcodedCatalogs() = runTest {
        assertTrue(categories.observeAll().first().isEmpty())
        assertTrue(units.observeAll().first().isEmpty())
        assertTrue(products.observeAll().first().isEmpty())
        assertTrue(suppliers.observeAll().first().isEmpty())
    }

    @Test
    fun repositoriesRoundTripEveryFieldAndStoreUuidAsText() = runTest {
        insertProduct()
        val supplier = Supplier(
            id = UUID.randomUUID(), name = "Distribuidora", contactName = "Ana", phone = "8095550100",
            email = "ana@example.test", address = "Santo Domingo", taxId = "123456789",
            notes = "Entrega semanal", isActive = false, createdAt = at, updatedAt = at
        )
        suppliers.insert(supplier)
        assertEquals(category, categories.findById(category.id))
        assertEquals(unit, units.findById(unit.id))
        assertEquals(product, products.findById(product.id))
        assertEquals(supplier, suppliers.findById(supplier.id))
        database.openHelper.readableDatabase.query(
            "SELECT id, typeof(id), typeof(sale_price), created_at FROM products"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(product.id.toString(), cursor.getString(0))
            assertEquals("text", cursor.getString(1))
            assertEquals("integer", cursor.getString(2))
            assertEquals(at.toEpochMilli(), cursor.getLong(3))
        }
    }

    @Test
    fun productRequiresBothCategoryAndUnit() = runTest {
        units.insert(unit)
        expectFailure<SQLiteConstraintException> { products.insert(product) }
        categories.insert(category)
        expectFailure<SQLiteConstraintException> { products.insert(product.copy(unitId = UUID.randomUUID())) }
        assertTrue(products.observeAll().first().isEmpty())
    }

    @Test
    fun duplicateInternalCodeDoesNotReplaceExistingProduct() = runTest {
        insertProduct()
        expectFailure<SQLiteConstraintException> {
            products.insert(product.copy(id = UUID.randomUUID(), name = "Duplicado"))
        }
        assertEquals(listOf(product), products.observeAll().first())
    }

    @Test
    fun duplicatePrimaryKeyDoesNotReplaceCategoryOrItsReferences() = runTest {
        insertProduct()
        expectFailure<SQLiteConstraintException> { categories.insert(category.copy(name = "Reemplazo")) }
        assertEquals(category, categories.findById(category.id))
        assertEquals(product, products.findById(product.id))
    }

    @Test
    fun referencedParentsCannotBePhysicallyDeleted() = runTest {
        insertProduct()
        expectFailure<SQLiteConstraintException> {
            database.openHelper.writableDatabase.execSQL("DELETE FROM categories")
        }
        expectFailure<SQLiteConstraintException> {
            database.openHelper.writableDatabase.execSQL("DELETE FROM units_of_measure")
        }
        assertEquals(product, products.findById(product.id))
    }

    @Test
    fun softDeletePreservesHistoricalReferencesAndInactiveRowsStayVisible() = runTest {
        insertProduct()
        assertTrue(categories.update(category.copy(isActive = false)))
        assertFalse(categories.observeAll().first().single().isActive)
        val deletedAt = at.plusSeconds(60)
        assertTrue(categories.softDelete(category.id, deletedAt))
        assertTrue(categories.observeAll().first().isEmpty())
        val historical = categories.findById(category.id)!!
        assertEquals(deletedAt, historical.deletedAt)
        assertEquals(deletedAt, historical.updatedAt)
        assertEquals(at, historical.createdAt)
        assertEquals(product, products.findById(product.id))
        assertFalse(categories.softDelete(category.id, deletedAt.plusSeconds(10)))
    }

    @Test
    fun unitProductAndSupplierSupportUpdateAndSoftDelete() = runTest {
        insertProduct()
        val supplier = Supplier(id = UUID.randomUUID(), name = "Proveedor", createdAt = at, updatedAt = at)
        suppliers.insert(supplier)
        val changedAt = at.plusSeconds(1)
        val updatedUnit = unit.copy(abbreviation = "UND", isActive = false, updatedAt = changedAt)
        val updatedProduct = product.copy(salePrice = Long.MAX_VALUE, isActive = false, updatedAt = changedAt)
        val updatedSupplier = supplier.copy(notes = "Actualizado", isActive = false, updatedAt = changedAt)
        assertTrue(units.update(updatedUnit))
        assertTrue(products.update(updatedProduct))
        assertTrue(suppliers.update(updatedSupplier))
        assertEquals(updatedUnit, units.observeAll().first().single())
        assertEquals(updatedProduct, products.observeAll().first().single())
        assertEquals(updatedSupplier, suppliers.observeAll().first().single())
        assertTrue(units.softDelete(unit.id, changedAt))
        assertTrue(products.softDelete(product.id, changedAt))
        assertTrue(suppliers.softDelete(supplier.id, changedAt))
        assertTrue(units.observeAll().first().isEmpty())
        assertTrue(products.observeAll().first().isEmpty())
        assertTrue(suppliers.observeAll().first().isEmpty())
        assertEquals(updatedUnit.copy(deletedAt = changedAt), units.findById(unit.id))
        assertEquals(updatedProduct.copy(deletedAt = changedAt), products.findById(product.id))
        assertEquals(updatedSupplier.copy(deletedAt = changedAt), suppliers.findById(supplier.id))
    }

    @Test
    fun missingRecordUpdatesReportFailureWithoutCreatingRows() = runTest {
        assertFalse(categories.update(category))
        assertFalse(units.update(unit))
        assertFalse(products.update(product))
        assertFalse(suppliers.update(Supplier(id = UUID.randomUUID(), name = "Ausente", createdAt = at, updatedAt = at)))
        assertFalse(categories.softDelete(category.id, at))
        assertFalse(units.softDelete(unit.id, at))
        assertFalse(products.softDelete(product.id, at))
        assertFalse(suppliers.softDelete(UUID.randomUUID(), at))
        assertNull(products.findById(product.id))
    }

    @Test
    fun reorderIsAtomicAndRejectsMissingOrDuplicateIds() = runTest {
        val other = category.copy(id = UUID.randomUUID(), name = "Alimentos", sortOrder = 9)
        categories.insert(category)
        categories.insert(other)
        expectFailure<IllegalStateException> { categories.reorder(listOf(other.id, UUID.randomUUID()), at) }
        assertEquals(9, categories.findById(other.id)!!.sortOrder)
        expectFailure<IllegalArgumentException> { categories.reorder(listOf(other.id, other.id), at) }
        categories.reorder(listOf(other.id, category.id), at.plusSeconds(1))
        val ordered = categories.observeAll().first()
        assertEquals(listOf(other.id, category.id), ordered.map { it.id })
        assertEquals(listOf(0, 1), ordered.map { it.sortOrder })
        assertTrue(ordered.all { it.updatedAt == at.plusSeconds(1) })
    }

    @Test
    fun transactorRollsBackAllTablesOnConstraintFailure() = runTest {
        val transactor = RoomDatabaseTransactor(database)
        expectFailure<SQLiteConstraintException> {
            transactor.runInTransaction {
                insertProduct()
                products.insert(product.copy(id = UUID.randomUUID()))
            }
        }
        assertNull(categories.findById(category.id))
        assertNull(units.findById(unit.id))
        assertNull(products.findById(product.id))
        transactor.runInTransaction { insertProduct() }
        assertNotNull(products.findById(product.id))
    }

    @Test
    fun flowPublishesCommittedChangesToAnExistingCollector() = runTest {
        val subscribed = CompletableDeferred<Unit>()
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            categories.observeAll().onEach { subscribed.complete(Unit) }.first { it.isNotEmpty() }
        }
        subscribed.await()
        categories.insert(category)
        assertEquals(listOf(category), observed.await())
    }

    private suspend fun insertProduct() {
        categories.insert(category)
        units.insert(unit)
        products.insert(product)
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return
            throw failure
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
