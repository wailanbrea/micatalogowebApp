package com.example.bspos.data.micatalogo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.BuildConfig
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi
import com.example.bspos.domain.model.AppSettings
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.repository.SettingsRepository
import com.example.bspos.domain.usecase.CompleteSaleRequest
import com.example.bspos.domain.usecase.CompleteSaleUseCase
import com.example.bspos.domain.usecase.RecordPaymentRequest
import com.example.bspos.domain.usecase.RecordPaymentUseCase
import com.example.bspos.domain.usecase.SaleLineInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Requires the isolated Laravel fixture server and adb reverse tcp:8893 tcp:8893. */
@RunWith(AndroidJUnit4::class)
class PosSaleLiveIntegrationTest {
    @Test
    fun offlineQueuedSalesSurviveDatabaseRestartAndSyncExactlyOnce() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val shopId = arguments.getString("posSaleShop")
        val productId = arguments.getString("posSaleProduct")
        val token = arguments.getString("posSaleToken")
        assumeTrue("Run with the local isolated POS fixture server", !shopId.isNullOrBlank() && !productId.isNullOrBlank() && !token.isNullOrBlank())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "qa-pos-offline-${UUID.randomUUID()}.db"
        var db: AppDatabase? = null
        try {
            val categoryId = UUID.randomUUID()
            val unitId = UUID.randomUUID()
            val localProductId = UUID.randomUUID()
            val localCustomerId = UUID.randomUUID()
            val saleAt = Instant.parse("2026-10-08T00:00:00Z")

            db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            db.categoryDao().insert(CategoryEntity(categoryId, "QA", createdAt = saleAt, updatedAt = saleAt))
            db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unitId, "Unidad", "un", createdAt = saleAt, updatedAt = saleAt))
            db.productDao().insert(
                ProductEntity(
                    id = localProductId,
                    name = "Producto offline QA",
                    internalCode = "QA-OFFLINE-${UUID.randomUUID()}",
                    categoryId = categoryId,
                    unitId = unitId,
                    salePrice = 250050,
                    averageCost = 150000,
                    miCatalogoShopId = shopId,
                    miCatalogoProductId = productId,
                    miCatalogoSaleUnit = "unit",
                    createdAt = saleAt,
                    updatedAt = saleAt
                )
            )
            db.customerRouteDao().insertCustomer(
                CustomerEntity(
                    id = localCustomerId,
                    businessName = "Cliente Crédito QA",
                    phone = "8095550199",
                    address = "Calle QA 1",
                    creditLimit = 1000000,
                    firstName = "Cliente",
                    lastName = "Crédito QA",
                    documentType = "cedula",
                    documentNumber = "001-0000000-9",
                    email = "cliente-credito@example.invalid",
                    createdAt = saleAt,
                    updatedAt = saleAt
                )
            )
            db.inventoryDao().recordMovements(
                listOf(
                    InventoryMovementEntity(
                        id = UUID.randomUUID(),
                        productId = localProductId,
                        locationType = InventoryLocation.MAIN.type,
                        locationId = InventoryLocation.MAIN.id,
                        movementType = InventoryMovementType.INITIAL,
                        quantity = 3,
                        previousQuantity = 0,
                        newQuantity = 3,
                        unitCost = 150000,
                        totalCost = 450000,
                        createdAt = saleAt
                    )
                ),
                allowNegativeStock = false
            )

            val completeSale = CompleteSaleUseCase(
                RoomDatabaseTransactor(db),
                db.saleDao(),
                db.inventoryDao(),
                db.productDao(),
                db.customerRouteDao(),
                db.posSaleOutboxDao(),
                QaSettings,
                Json { ignoreUnknownKeys = true },
                PosSaleSyncScheduler(context)
            )
            val invoicePrefix = "QA-OFFLINE-${UUID.randomUUID()}"
            val firstSale = completeSale(
                CompleteSaleRequest(
                    invoiceNumber = "$invoicePrefix-001",
                    date = saleAt,
                    lines = listOf(SaleLineInput(localProductId, quantity = 1, unitPrice = 250050)),
                    paymentType = SalePaymentType.CASH,
                    paidAmount = 250050,
                    pendingAmount = 0
                )
            )
            val secondSale = completeSale(
                CompleteSaleRequest(
                    invoiceNumber = "$invoicePrefix-002",
                    date = saleAt.plusSeconds(1),
                    lines = listOf(SaleLineInput(localProductId, quantity = 1, unitPrice = 250050)),
                    paymentType = SalePaymentType.CASH,
                    paidAmount = 250050,
                    pendingAmount = 0
                )
            )
            val creditSale = completeSale(
                CompleteSaleRequest(
                    invoiceNumber = "$invoicePrefix-003",
                    customerId = localCustomerId,
                    date = saleAt.plusSeconds(2),
                    lines = listOf(SaleLineInput(localProductId, quantity = 1, unitPrice = 250050)),
                    paymentType = SalePaymentType.CREDIT,
                    paidAmount = 0,
                    pendingAmount = 250050
                )
            )
            assertEquals("PENDING", db.posSaleOutboxDao().stateForSale(firstSale.id))
            assertEquals("PENDING", db.posSaleOutboxDao().stateForSale(secondSale.id))
            assertEquals("PENDING", db.posSaleOutboxDao().stateForSale(creditSale.id))
            assertEquals(3, db.posSaleOutboxDao().observeOutstanding().first().size)
            assertEquals(250050L, db.customerRouteDao().findCustomer(localCustomerId)?.balance)

            // Simulate a process/device restart while the network is unavailable:
            // the sale and immutable outbox payload must remain on disk.
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            assertEquals("PENDING", db.posSaleOutboxDao().stateForSale(firstSale.id))
            assertEquals("PENDING", db.posSaleOutboxDao().stateForSale(secondSale.id))
            assertEquals("PENDING", db.posSaleOutboxDao().stateForSale(creditSale.id))

            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $token")
                            .header("Accept", "application/json")
                            .header("X-MiCatalogo-Version-Code", BuildConfig.VERSION_CODE.toString())
                            .build()
                    )
                }
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("http://127.0.0.1:8893/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val api = retrofit.create(MiCatalogoApi::class.java)
            val customerApi = retrofit.create(MiCatalogoCustomerApi::class.java)
            val operationApi = retrofit.create(MiCatalogoOperationApi::class.java)
            val repository = MiCatalogoPosSaleRepositoryImpl(
                api,
                customerApi,
                db.posSaleOutboxDao(),
                db.customerRouteDao(),
                db.saleDao(),
                json,
                db.operationOutboxDao(),
                OperationSyncRepository(operationApi, db.operationOutboxDao(), json)
            )

            val synced = repository.syncDueSales() as MiCatalogoResult.Success
            assertEquals(3, synced.value.sent)
            assertEquals(0, synced.value.blocked)
            assertEquals("SENT", db.posSaleOutboxDao().stateForSale(firstSale.id))
            assertEquals("SENT", db.posSaleOutboxDao().stateForSale(secondSale.id))
            assertEquals("SENT", db.posSaleOutboxDao().stateForSale(creditSale.id))
            assertEquals(250050L, db.customerRouteDao().findCustomer(localCustomerId)?.balance)

            val paymentUseCase = RecordPaymentUseCase(
                RoomDatabaseTransactor(db),
                db.paymentDao(),
                db.saleDao(),
                db.customerRouteDao(),
                db.cashSessionDao(),
                db.paymentSyncDao(),
                db.posSaleOutboxDao(),
                PosSaleSyncScheduler(context),
                json
            )
            paymentUseCase(
                RecordPaymentRequest(
                    receiptNumber = "QA-CREDIT-PAY-${UUID.randomUUID()}",
                    customerId = localCustomerId,
                    date = saleAt.plusSeconds(3),
                    method = com.example.bspos.domain.model.PaymentMethod.TRANSFER,
                    allocations = listOf(com.example.bspos.domain.usecase.PaymentAllocationInput(creditSale.id, 250050))
                )
            )
            assertEquals(0L, db.customerRouteDao().findCustomer(localCustomerId)?.balance)
            assertEquals(1, db.paymentSyncDao().pending().size)

            val paymentSync = PaymentSyncRepository(
                db.paymentSyncDao(),
                db.posSaleOutboxDao(),
                db.customerRouteDao(),
                customerApi,
                json
            )
            assertEquals(false, paymentSync.sync())
            assertTrue(db.paymentSyncDao().pending().isEmpty())

            val replay = repository.syncDueSales() as MiCatalogoResult.Success
            assertEquals(0, replay.value.sent)
            assertEquals("SENT", db.posSaleOutboxDao().stateForSale(firstSale.id))
            assertEquals("SENT", db.posSaleOutboxDao().stateForSale(secondSale.id))
            assertEquals("SENT", db.posSaleOutboxDao().stateForSale(creditSale.id))
            assertTrue(db.posSaleOutboxDao().observeOutstanding().first().isEmpty())
        } finally {
            db?.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun offlineQueuedDecantConsumesSourceMillilitersAndSyncsExactlyOnce() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val shopId = arguments.getString("posSaleShop")
        val sourceProductId = arguments.getString("posSaleSourceProduct")
        val decantProductId = arguments.getString("posSaleDecantProduct")
        val token = arguments.getString("posSaleToken")
        assumeTrue(
            "Run with the isolated Laravel decant fixture server",
            !shopId.isNullOrBlank() && !sourceProductId.isNullOrBlank() &&
                !decantProductId.isNullOrBlank() && !token.isNullOrBlank()
        )
        val requiredShopId = checkNotNull(shopId)
        val requiredSourceProductId = checkNotNull(sourceProductId)
        val requiredDecantProductId = checkNotNull(decantProductId)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "qa-pos-decant-${UUID.randomUUID()}.db"
        var db: AppDatabase? = null
        try {
            val categoryId = UUID.randomUUID()
            val unitId = UUID.randomUUID()
            val sourceLocalId = UUID.randomUUID()
            val decantLocalId = UUID.randomUUID()
            val saleAt = Instant.parse("2026-10-08T00:10:00Z")

            db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
            db.categoryDao().insert(CategoryEntity(categoryId, "Perfumes QA", createdAt = saleAt, updatedAt = saleAt))
            db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unitId, "Unidad", "un", createdAt = saleAt, updatedAt = saleAt))
            db.productDao().insert(
                ProductEntity(
                    id = sourceLocalId,
                    name = "Botella fuente POS E2E QA",
                    internalCode = "QA-SOURCE-${UUID.randomUUID()}",
                    categoryId = categoryId,
                    unitId = unitId,
                    salePrice = 120000,
                    averageCost = 30000,
                    miCatalogoShopId = shopId,
                    miCatalogoProductId = sourceProductId,
                    miCatalogoSaleUnit = "bottle",
                    miCatalogoVolumeMl = 100,
                    miCatalogoAvailableMl = 100,
                    miCatalogoOpenedBottles = 1,
                    createdAt = saleAt,
                    updatedAt = saleAt
                )
            )
            db.productDao().insert(
                ProductEntity(
                    id = decantLocalId,
                    name = "Decant POS E2E QA 10 ml",
                    internalCode = "QA-DECANT-${UUID.randomUUID()}",
                    categoryId = categoryId,
                    unitId = unitId,
                    salePrice = 5000,
                    averageCost = 3000,
                    miCatalogoShopId = shopId,
                    miCatalogoProductId = decantProductId,
                    miCatalogoSourceProductId = sourceProductId,
                    miCatalogoVolumeMl = 10,
                    miCatalogoAvailableMl = 100,
                    miCatalogoSaleUnit = "decant",
                    createdAt = saleAt,
                    updatedAt = saleAt
                )
            )
            db.inventoryDao().recordMovements(
                listOf(
                    InventoryMovementEntity(
                        id = UUID.randomUUID(),
                        productId = sourceLocalId,
                        locationType = InventoryLocation.MAIN.type,
                        locationId = InventoryLocation.MAIN.id,
                        movementType = InventoryMovementType.INITIAL,
                        quantity = 1,
                        previousQuantity = 0,
                        newQuantity = 1,
                        unitCost = 30000,
                        totalCost = 30000,
                        createdAt = saleAt
                    ),
                    InventoryMovementEntity(
                        id = UUID.randomUUID(),
                        productId = decantLocalId,
                        locationType = InventoryLocation.MAIN.type,
                        locationId = InventoryLocation.MAIN.id,
                        movementType = InventoryMovementType.INITIAL,
                        quantity = 10,
                        previousQuantity = 0,
                        newQuantity = 10,
                        unitCost = 3000,
                        totalCost = 30000,
                        createdAt = saleAt
                    )
                ),
                allowNegativeStock = false
            )

            val completeSale = CompleteSaleUseCase(
                RoomDatabaseTransactor(db),
                db.saleDao(),
                db.inventoryDao(),
                db.productDao(),
                db.customerRouteDao(),
                db.posSaleOutboxDao(),
                QaSettings,
                Json { ignoreUnknownKeys = true },
                PosSaleSyncScheduler(context)
            )
            val sale = completeSale(
                CompleteSaleRequest(
                    invoiceNumber = "QA-DECANT-${UUID.randomUUID()}",
                    date = saleAt,
                    lines = listOf(SaleLineInput(decantLocalId, quantity = 1, unitPrice = 5000)),
                    paymentType = SalePaymentType.CASH,
                    paidAmount = 5000,
                    pendingAmount = 0
                )
            )
            assertEquals(0L, db.inventoryDao().findStock(sourceLocalId, InventoryLocation.MAIN.type, InventoryLocation.MAIN.id)?.quantity)
            assertEquals(9L, db.inventoryDao().findStock(decantLocalId, InventoryLocation.MAIN.type, InventoryLocation.MAIN.id)?.quantity)
            assertEquals("PENDING", db.posSaleOutboxDao().stateForSale(sale.id))

            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $token")
                            .header("Accept", "application/json")
                            .header("X-MiCatalogo-Version-Code", BuildConfig.VERSION_CODE.toString())
                            .build()
                    )
                }
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("http://127.0.0.1:8893/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val api = retrofit.create(MiCatalogoApi::class.java)
            val repository = MiCatalogoPosSaleRepositoryImpl(
                api,
                retrofit.create(MiCatalogoCustomerApi::class.java),
                db.posSaleOutboxDao(),
                db.customerRouteDao(),
                db.saleDao(),
                json,
                db.operationOutboxDao(),
                OperationSyncRepository(retrofit.create(MiCatalogoOperationApi::class.java), db.operationOutboxDao(), json)
            )

            val synced = repository.syncDueSales() as MiCatalogoResult.Success
            assertEquals(1, synced.value.sent)
            assertEquals(0, synced.value.blocked)
            assertEquals("SENT", db.posSaleOutboxDao().stateForSale(sale.id))
            assertTrue(db.posSaleOutboxDao().observeOutstanding().first().isEmpty())

            val remoteProducts = api.catalog(requiredShopId).body()!!.products.associateBy { it.id }
            assertEquals(90, remoteProducts[requiredSourceProductId]!!.inventory.availableMl)
            assertEquals(9, remoteProducts[requiredDecantProductId]!!.inventory.stockQuantity)
        } finally {
            db?.close()
            context.deleteDatabase(databaseName)
        }
    }

    private object QaSettings : SettingsRepository {
        override fun observe(): Flow<AppSettings> = flowOf(AppSettings())
        override suspend fun setAllowNegativeStock(enabled: Boolean) = Unit
        override suspend fun setAutomaticBackupsEnabled(enabled: Boolean) = Unit
        override suspend fun setRoutesEnabled(enabled: Boolean) = Unit
    }
}
