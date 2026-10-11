package com.example.bspos.presentation.pos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.core.database.InventoryIntegrity
import com.example.bspos.core.database.TenantIntegrity
import com.example.bspos.data.local.tenant.tenantTestScope
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.*
import com.example.bspos.data.micatalogo.PosSaleSyncScheduler
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.SettingsRepository
import com.example.bspos.domain.usecase.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MixedCreditSaleTest {
    private val shop="qa-mixed-credit"
    private val at = Instant.parse("2026-10-08T12:00:00Z")

    private suspend fun verify(creditLimit: Long, block: suspend (AppDatabase, CompleteSaleUseCase, UUID, UUID) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope=tenantTestScope(shop)
        val tenant=TenantMetadata.assigned(shop)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).addCallback(InventoryIntegrity).addCallback(TenantIntegrity).build()
        try {
            val category = UUID.randomUUID()
            val unit = UUID.randomUUID()
            val product = UUID.randomUUID()
            val customer = UUID.randomUUID()
            db.categoryDao().insert(CategoryEntity(category, "Prueba", createdAt = at, updatedAt = at,tenant=tenant))
            db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unit, "Unidad", "u", createdAt = at, updatedAt = at,tenant=tenant))
            db.productDao().insert(ProductEntity(product, "Producto local de prueba", "MIXED-TEST", categoryId = category,
                unitId = unit, salePrice = 950000, averageCost = 100000, createdAt = at, updatedAt = at,tenant=tenant))
            db.inventoryDao().recordMovements(listOf(InventoryMovementEntity(UUID.randomUUID(), product,
                InventoryLocationType.MAIN_WAREHOUSE, "MAIN", InventoryMovementType.INITIAL, 5, 0, 5, 100000, 500000, createdAt = at,tenant=tenant)), false)
            db.customerRouteDao().insertCustomer(CustomerEntity(customer, "Cliente local de prueba", creditLimit = creditLimit, createdAt = at, updatedAt = at,tenant=tenant))
            val settings = object : SettingsRepository {
                override fun observe() = flowOf(AppSettings())
                override suspend fun setAllowNegativeStock(enabled: Boolean) {}
                override suspend fun setAutomaticBackupsEnabled(enabled: Boolean) {}
                override suspend fun setRoutesEnabled(enabled: Boolean) {}
            }
            val useCase = CompleteSaleUseCase(RoomDatabaseTransactor(db,scope), db.saleDao(), db.inventoryDao(), db.productDao(),
                db.customerRouteDao(), db.posSaleOutboxDao(), settings, Json { ignoreUnknownKeys = true }, PosSaleSyncScheduler(context),scope)
            block(db, useCase, product, customer)
        } finally { db.close() }
    }

    private fun request(product: UUID, customer: UUID) = CompleteSaleRequest(
        "MIXED-LOCAL", customerId = customer, date = at.plusSeconds(1), lines = listOf(SaleLineInput(product, 1, 950000)),
        paymentType = SalePaymentType.MIXED, paidAmount = 200000, pendingAmount = 750000,
        splitPayments = listOf(PosPaymentSplitInput("cash", 200000), PosPaymentSplitInput("credit", 750000))
    )

    @Test fun mixedCashAndCreditAreRecordedAtomicallyWithoutCashSession() = runTest {
        verify(1000000) { db, complete, product, customer ->
            val sale = complete(request(product, customer))
            assertEquals(950000L, sale.total)
            assertEquals(200000L, sale.paidAmount)
            assertEquals(750000L, sale.pendingAmount)
            assertEquals(750000L, db.customerRouteDao().findCustomer(customer,shop)!!.balance)
            assertEquals(4L, db.inventoryDao().findStock(product, InventoryLocationType.MAIN_WAREHOUSE, "MAIN",shop)!!.quantity)
            assertEquals(1, db.saleDao().observeAll(shop).first().size)
        }
    }

    @Test fun insufficientCreditDoesNotLeaveAPartialSaleOrConsumeStock() = runTest {
        verify(700000) { db, complete, product, customer ->
            try { complete(request(product, customer)); fail("Expected credit limit rejection") } catch (_: IllegalArgumentException) {}
            assertTrue(db.saleDao().observeAll(shop).first().isEmpty())
            assertEquals(0L, db.customerRouteDao().findCustomer(customer,shop)!!.balance)
            assertEquals(5L, db.inventoryDao().findStock(product, InventoryLocationType.MAIN_WAREHOUSE, "MAIN",shop)!!.quantity)
        }
    }
}
