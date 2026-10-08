package com.example.bspos.data.micatalogo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.PosSaleOutboxEntity
import com.example.bspos.data.local.entity.SaleEntity
import com.example.bspos.data.local.entity.SaleItemEntity
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CatalogSyncIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var api: SnapshotApi
    private lateinit var repository: MiCatalogoCatalogRepositoryImpl
    private val shopId = "test-shop"
    private val at = Instant.parse("2026-10-03T12:00:00Z")
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        api = SnapshotApi(CatalogSnapshotDto(
            shop = ShopDto(id = shopId, name = "Tienda de prueba"),
            categories = listOf(RemoteCategoryDto("category", "Perfumes", "perfumes", 0, "active", at.toString())),
            products = listOf(product("product-a"), product("product-b")),
            generatedAt = at.toString()
        ))
        repository = MiCatalogoCatalogRepositoryImpl(
            api, MiCatalogoImageDownloader(context, "https://example.test/"),
            RoomDatabaseTransactor(db), db.categoryDao(), db.unitOfMeasureDao(),
            db.productDao(), db.inventoryDao(), db.posSaleOutboxDao(), json,
            EmptyCustomerApi(), db.customerRouteDao(), db.paymentSyncDao(), db.operationOutboxDao()
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun repeatedSyncKeepsIdsPricesAndStockWithoutDuplicateMovements() = runTest {
        val first = repository.syncCatalog(shopId) as MiCatalogoResult.Success<MiCatalogoCatalogSyncResult>
        assertEquals(2, first.value.productsApplied)
        assertEquals(2, first.value.inventoryMovementsRecorded)
        val before = db.productDao().observeAll().first()
        val second = repository.syncCatalog(shopId) as MiCatalogoResult.Success<MiCatalogoCatalogSyncResult>
        assertEquals(0, second.value.inventoryMovementsRecorded)
        assertEquals(before, db.productDao().observeAll().first())
        assertEquals(2, db.productDao().observeAll().first().size)
        val id = MiCatalogoImportMapper.productId(shopId, "product-a")
        assertEquals(280000L, db.productDao().findById(id)!!.salePrice)
        assertEquals(10L, db.inventoryDao().findStock(id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN")!!.quantity)
        assertEquals(1, db.inventoryDao().observeMovements(id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN", null, null).first().size)
    }

    @Test
    fun wrongShopSnapshotIsRejectedWithoutImportingAnything() = runTest {
        api.snapshot = api.snapshot.copy(shop = ShopDto(id = "other-shop"))
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Failure)
        assertTrue(db.productDao().observeAll().first().isEmpty())
    }

    @Test
    fun catalogRefreshDoesNotUndoStockOfAPendingLocalSale() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val id = MiCatalogoImportMapper.productId(shopId, "product-a")
        val saleAt = at.plusSeconds(1)
        db.inventoryDao().recordMovements(listOf(InventoryMovementEntity(
            UUID.randomUUID(), id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN",
            InventoryMovementType.SALE, -2, 10, 8, 100000, -200000, createdAt = saleAt
        )), false)
        val saleId = UUID.randomUUID()
        db.saleDao().insertWithItems(SaleEntity(
            saleId, "TEST-PENDING", date = saleAt, subtotal = 560000, total = 560000,
            paymentType = SalePaymentType.CASH, paidAmount = 560000, createdAt = saleAt, updatedAt = saleAt
        ), listOf(SaleItemEntity(UUID.randomUUID(), saleId, id, 2, 280000, 100000, subtotal = 560000)))
        val request = PosSaleUploadRequestDto(
            clientSaleUuid = saleId.toString(),
            paymentStatus = "paid",
            items = listOf(PosSaleUploadItemDto("product-a", 2, "2800.00"))
        )
        db.posSaleOutboxDao().insert(PosSaleOutboxEntity(
            saleId, shopId, json.encodeToString(request), nextAttemptAt = saleAt, createdAt = saleAt, updatedAt = saleAt
        ))
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        assertEquals(8L, db.inventoryDao().findStock(id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN")!!.quantity)
        assertEquals(2, db.inventoryDao().observeMovements(id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN", null, null).first().size)
    }

    @Test
    fun pendingMutationSurvivesStaleMetadataAndRetirementSnapshot() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val id = MiCatalogoImportMapper.productId(shopId, "product-a")
        val edited = db.productDao().findById(id)!!.copy(name = "Edición sin conexión", salePrice = 310000L)
        db.productDao().update(edited)
        db.operationOutboxDao().insert(com.example.bspos.data.local.entity.OperationOutboxEntity(
            UUID.randomUUID().toString(), shopId, "product-a", "{}", at.plusSeconds(1)
        ))
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        assertEquals(edited, db.productDao().findById(id))
        api.snapshot = api.snapshot.copy(products = api.snapshot.products.filter { it.id != "product-a" })
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        assertEquals(edited, db.productDao().findById(id))
    }

    @Test
    fun selectedPhotoIsCopiedIntoBoundedImmutableQueuePayload() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = java.io.File.createTempFile("test-product-image-", ".png", context.cacheDir)
        val bitmap = android.graphics.Bitmap.createBitmap(2000, 1200, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            val id = MiCatalogoImportMapper.productId(shopId, "product-a")
            val original = db.productDao().findById(id)!!
            val edited = original.copy(imagePath = file.absolutePath, updatedAt = at.plusSeconds(1))
            val recorder = RemoteMutationRecorder(RoomDatabaseTransactor(db), db.categoryDao(), db.operationOutboxDao(),
                PosSaleSyncScheduler(context), ProductImageSnapshot(context))
            recorder.transaction { db.productDao().update(edited); recorder.product(edited, original) }
            val queued = db.operationOutboxDao().oldestActive(shopId)!!
            val photo = json.parseToJsonElement(queued.payload) as kotlinx.serialization.json.JsonObject
            val encoded = (photo["image_base64"] as kotlinx.serialization.json.JsonPrimitive).content
            val copied = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            assertTrue(copied.size <= 512 * 1024)
            val copiedBitmap = android.graphics.BitmapFactory.decodeByteArray(copied, 0, copied.size)
            assertTrue(maxOf(copiedBitmap.width, copiedBitmap.height) <= 1600)
            copiedBitmap.recycle()
            file.writeBytes(byteArrayOf(1, 2, 3))
            assertEquals(queued.payload, db.operationOutboxDao().oldestActive(shopId)!!.payload)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(copied).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(hash, (photo["image_sha256"] as kotlinx.serialization.json.JsonPrimitive).content)
            val invalid = edited.copy(imagePath = file.absolutePath + ".missing")
            assertTrue(runCatching { recorder.transaction { db.productDao().update(invalid); recorder.product(invalid, edited) } }.isFailure)
            assertEquals(edited, db.productDao().findById(id))
            assertEquals(1, db.operationOutboxDao().activeForShop(shopId).size)
        } finally { bitmap.recycle(); file.delete() }
    }

    @Test
    fun acknowledgedReceiptRefreshesPriceAndCostAndFailedDownloadRemainsDurable() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        db.operationOutboxDao().insert(com.example.bspos.data.local.entity.OperationOutboxEntity(
            "receipt", shopId, "product-a", "{}", at))
        assertEquals(1, db.operationOutboxDao().confirmSent("receipt", shopId))
        assertEquals(0, db.operationOutboxDao().confirmSent("receipt", shopId))
        api.catalogStatus = 503
        val refresh = CatalogRefreshSyncRepository(db.operationOutboxDao(), repository)
        assertTrue(refresh.sync())
        assertEquals(1, db.operationOutboxDao().pendingRefreshes().size)
        assertNotNull(db.operationOutboxDao().pendingRefreshes().single().error)
        api.catalogStatus = 200
        api.snapshot = api.snapshot.copy(products = listOf(product("product-a").copy(price = "3500.00",
            inventory = RemoteInventoryDto(true, stockQuantity = 12, costPrice = "2000.00"))))
        assertFalse(refresh.sync())
        val id = MiCatalogoImportMapper.productId(shopId, "product-a")
        assertEquals(350000L, db.productDao().findById(id)!!.salePrice)
        assertEquals(200000L, db.productDao().findById(id)!!.lastPurchaseCost)
        assertEquals(12L, db.inventoryDao().findStock(id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN")!!.quantity)
        assertTrue(db.operationOutboxDao().pendingRefreshes().isEmpty())
        val sale = queueSale(at, "product-a")
        db.posSaleOutboxDao().markSent(sale, "ACK", at)
        assertEquals(1, db.operationOutboxDao().pendingRefreshes().size)
    }

    @Test
    fun acknowledgedEventDuringDownloadDoesNotRestoreStaleDataOrClearNewerRefresh() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val id = MiCatalogoImportMapper.productId(shopId, "product-a")
        db.operationOutboxDao().requestRefresh(com.example.bspos.data.local.entity.CatalogRefreshEntity(shopId, "old"))
        api.beforeCatalog = {
            db.productDao().update(db.productDao().findById(id)!!.copy(name = "Cambio durante descarga", salePrice = 350000L))
            db.operationOutboxDao().insert(com.example.bspos.data.local.entity.OperationOutboxEntity(
                "edit", shopId, "product-a", "{}", at))
            db.operationOutboxDao().confirmSent("edit", shopId)
        }
        val refresh = CatalogRefreshSyncRepository(db.operationOutboxDao(), repository)
        assertTrue(refresh.sync())
        assertEquals("Cambio durante descarga", db.productDao().findById(id)!!.name)
        assertEquals(350000L, db.productDao().findById(id)!!.salePrice)
        assertNotEquals("old", db.operationOutboxDao().pendingRefreshes().single().revision)
        api.beforeCatalog = null
        api.snapshot = api.snapshot.copy(products = api.snapshot.products.map {
            if (it.id == "product-a") it.copy(name = "Cambio durante descarga", price = "3500.00") else it
        })
        assertFalse(refresh.sync())
        assertEquals(350000L, db.productDao().findById(id)!!.salePrice)
    }

    @Test
    fun mutationsAndSalesAreUploadedInCreationOrder() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val calls = mutableListOf<String>()
        api.saleCalls = calls
        val operationApi = object : com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi {
            override suspend fun submit(shopId: String, payload: kotlinx.serialization.json.JsonObject): Response<kotlinx.serialization.json.JsonObject> {
                calls.add("operation")
                return Response.success(payload)
            }
        }
        val first = queueSale(at, "first")
        db.operationOutboxDao().insert(com.example.bspos.data.local.entity.OperationOutboxEntity(
            "operation-1", shopId, "product-a", "{\"client_operation_uuid\":\"operation-1\"}", at.plusSeconds(1)
        ))
        queueSale(at.plusSeconds(2), "second")
        val salesRepository = MiCatalogoPosSaleRepositoryImpl(api, EmptyCustomerApi(), db.posSaleOutboxDao(),
            db.customerRouteDao(), db.saleDao(), json, db.operationOutboxDao(),
            OperationSyncRepository(operationApi, db.operationOutboxDao(), json))
        assertTrue(salesRepository.syncDueSales() is MiCatalogoResult.Success)
        assertEquals(listOf("first", "operation", "second"), calls)
        assertEquals("SENT", db.posSaleOutboxDao().stateForSale(first))
    }

    @Test
    fun blockedOriginalSaleStopsLaterMutationAndSale() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val calls = mutableListOf<String>()
        api.saleCalls = calls
        val first = queueSale(at, "first")
        db.posSaleOutboxDao().markBlocked(first, "Conflicto de precio", at)
        db.operationOutboxDao().insert(com.example.bspos.data.local.entity.OperationOutboxEntity(
            "operation-1", shopId, "product-a", "{\"client_operation_uuid\":\"operation-1\"}", at.plusSeconds(1)
        ))
        queueSale(at.plusSeconds(2), "second")
        val operationApi = object : com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi {
            override suspend fun submit(shopId: String, payload: kotlinx.serialization.json.JsonObject): Response<kotlinx.serialization.json.JsonObject> {
                calls.add("operation")
                return Response.success(payload)
            }
        }
        val salesRepository = MiCatalogoPosSaleRepositoryImpl(api, EmptyCustomerApi(), db.posSaleOutboxDao(),
            db.customerRouteDao(), db.saleDao(), json, db.operationOutboxDao(),
            OperationSyncRepository(operationApi, db.operationOutboxDao(), json))
        assertTrue(salesRepository.syncDueSales() is MiCatalogoResult.Success)
        assertTrue(calls.isEmpty())
        assertEquals("BLOCKED", db.posSaleOutboxDao().stateForSale(first))
        assertEquals("PENDING", db.operationOutboxDao().oldestActive(shopId)!!.state)
    }

    @Test
    fun equalOrBackdatedTimesCannotReorderCommittedEvents() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val calls = mutableListOf<String>()
        api.saleCalls = calls
        val first = queueSale(at, "first")
        db.operationOutboxDao().insert(com.example.bspos.data.local.entity.OperationOutboxEntity(
            "operation-1", shopId, "product-a", "{\"client_operation_uuid\":\"operation-1\"}", at))
        val second = queueSale(at.minusSeconds(60), "backdated")
        val active = db.posSaleOutboxDao().findActiveForShop(shopId).associateBy { it.saleId }
        assertEquals(1L, active[first]!!.queueSequence)
        assertEquals(2L, db.operationOutboxDao().oldestActive(shopId)!!.queueSequence)
        assertEquals(3L, active[second]!!.queueSequence)
        val operationApi = object : com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi {
            override suspend fun submit(shopId: String, payload: kotlinx.serialization.json.JsonObject): Response<kotlinx.serialization.json.JsonObject> {
                calls.add("operation")
                return Response.success(payload)
            }
        }
        val sync = MiCatalogoPosSaleRepositoryImpl(api, EmptyCustomerApi(), db.posSaleOutboxDao(),
            db.customerRouteDao(), db.saleDao(), json, db.operationOutboxDao(),
            OperationSyncRepository(operationApi, db.operationOutboxDao(), json))
        assertTrue(sync.syncDueSales() is MiCatalogoResult.Success)
        assertEquals(listOf("first", "operation", "backdated"), calls)
    }

    @Test
    fun futureRetryKeepsWorkerAliveButBlockedHeadDoesNotSpin() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val calls = mutableListOf<String>()
        api.saleCalls = calls
        val first = queueSale(at, "future")
        db.posSaleOutboxDao().markRetry(first, "Sin conexión", at, Instant.now().plusSeconds(3600))
        val operationApi = object : com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi {
            override suspend fun submit(shopId: String, payload: kotlinx.serialization.json.JsonObject): Response<kotlinx.serialization.json.JsonObject> = error("No operation expected")
        }
        val sync = MiCatalogoPosSaleRepositoryImpl(api, EmptyCustomerApi(), db.posSaleOutboxDao(),
            db.customerRouteDao(), db.saleDao(), json, db.operationOutboxDao(),
            OperationSyncRepository(operationApi, db.operationOutboxDao(), json))
        val pending = sync.syncDueSales() as MiCatalogoResult.Success<MiCatalogoPosSaleSyncResult>
        assertTrue(pending.value.retried > 0)
        assertTrue(calls.isEmpty())
        db.posSaleOutboxDao().markBlocked(first, "Revisión necesaria", at)
        val blocked = sync.syncDueSales() as MiCatalogoResult.Success<MiCatalogoPosSaleSyncResult>
        assertEquals(0, blocked.value.retried)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun receiptAndCountCommitOutboxWithLedgerOrRollbackTogether() = runTest {
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val id = MiCatalogoImportMapper.productId(shopId, "product-a")
        val recorder = RemoteMutationRecorder(RoomDatabaseTransactor(db), db.categoryDao(), db.operationOutboxDao(),
            PosSaleSyncScheduler(ApplicationProvider.getApplicationContext<Context>()))
        val inventoryRepository = com.example.bspos.data.repository.InventoryRepositoryImpl(
            com.example.bspos.data.local.InventoryLocalDataSource(db.inventoryDao(), db.inventoryAdjustmentReasonDao()),
            db.productDao(), db.inventoryDao(), recorder)
        val movement = InventoryMovement(UUID.randomUUID(), id, InventoryLocation.MAIN, InventoryMovementType.PURCHASE,
            2, 10, 12, 200000, 400000, createdAt = at.plusSeconds(1))
        inventoryRepository.recordMovements(listOf(movement))
        val payload = json.parseToJsonElement(db.operationOutboxDao().oldestActive(shopId)!!.payload).toString()
        assertTrue(payload.contains("2000.00"))
        assertEquals(200000L, db.productDao().findById(id)!!.lastPurchaseCost)
        assertEquals(116666L, db.productDao().findById(id)!!.averageCost)
        assertTrue(runCatching { inventoryRepository.recordMovements(listOf(movement.copy(id = UUID.randomUUID()))) }.isFailure)
        assertEquals(1, db.operationOutboxDao().activeForShop(shopId).size)
        assertEquals(12L, db.inventoryDao().findStock(id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN")!!.quantity)
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        assertEquals(12L, db.inventoryDao().findStock(id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN")!!.quantity)
        inventoryRepository.recordMovements(listOf(InventoryMovement(UUID.randomUUID(), id, InventoryLocation.MAIN,
            InventoryMovementType.PHYSICAL_COUNT_OUT, -3, 12, 9, 116666, -349998, notes = "Conteo físico", createdAt = at.plusSeconds(2))))
        assertEquals(2, db.operationOutboxDao().activeForShop(shopId).size)
        assertEquals(200000L, db.productDao().findById(id)!!.lastPurchaseCost)
    }

    @Test
    fun bottleReceiptUpdatesSharedMlAndDecantsWithoutDoubleUploading() = runTest {
        api.snapshot = api.snapshot.copy(products = listOf(
            product("bottle").copy(saleUnit = "bottle", volumeMl = 100,
                inventory = RemoteInventoryDto(true, stockQuantity = 0, availableMl = 95, costPrice = "1000.00")),
            product("decant").copy(saleUnit = "decant", volumeMl = 5, sourceProductId = "bottle",
                inventory = RemoteInventoryDto(true, stockQuantity = 19, costPrice = "50.00"))
        ))
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        val id = MiCatalogoImportMapper.productId(shopId, "bottle")
        val decantId = MiCatalogoImportMapper.productId(shopId, "decant")
        val recorder = RemoteMutationRecorder(RoomDatabaseTransactor(db), db.categoryDao(), db.operationOutboxDao(),
            PosSaleSyncScheduler(ApplicationProvider.getApplicationContext<Context>()))
        val inventoryRepository = com.example.bspos.data.repository.InventoryRepositoryImpl(
            com.example.bspos.data.local.InventoryLocalDataSource(db.inventoryDao(), db.inventoryAdjustmentReasonDao()),
            db.productDao(), db.inventoryDao(), recorder)
        inventoryRepository.recordMovements(listOf(InventoryMovement(UUID.randomUUID(), id, InventoryLocation.MAIN,
            InventoryMovementType.PURCHASE, 1, 0, 1, 200000, 200000, createdAt = at.plusSeconds(1))))
        assertEquals(195, db.productDao().findById(id)!!.miCatalogoAvailableMl)
        assertEquals(39L, db.inventoryDao().findStock(decantId, InventoryLocationType.MAIN_WAREHOUSE, "MAIN")!!.quantity)
        assertEquals(1, db.operationOutboxDao().activeForShop(shopId).size)
        assertTrue(repository.syncCatalog(shopId) is MiCatalogoResult.Success)
        assertEquals(195, db.productDao().findById(id)!!.miCatalogoAvailableMl)
        assertEquals(39L, db.inventoryDao().findStock(decantId, InventoryLocationType.MAIN_WAREHOUSE, "MAIN")!!.quantity)
    }

    private suspend fun queueSale(timestamp: Instant, label: String): UUID {
        val id = UUID.randomUUID()
        db.saleDao().insertWithItems(SaleEntity(id, label, date = timestamp, subtotal = 100,
            total = 100, paymentType = SalePaymentType.CASH, paidAmount = 100,
            createdAt = timestamp, updatedAt = timestamp), listOf(SaleItemEntity(UUID.randomUUID(), id,
                MiCatalogoImportMapper.productId(shopId, "product-a"), 1, 100, 50, subtotal = 100)))
        val body = PosSaleUploadRequestDto(
            clientSaleUuid = id.toString(),
            paymentStatus = "paid",
            items = listOf(PosSaleUploadItemDto(label, 1, "1.00"))
        )
        db.posSaleOutboxDao().insert(PosSaleOutboxEntity(id, shopId, json.encodeToString(body),
            nextAttemptAt = timestamp, createdAt = timestamp, updatedAt = timestamp))
        return id
    }

    private fun product(id: String) = RemoteProductDto(
        id = id, categoryId = "category", name = "Perfume $id", price = "2800.00", currency = "DOP",
        saleUnit = "unit", availabilityStatus = "available", moderationStatus = "active",
        inventory = RemoteInventoryDto(true, stockQuantity = 10, costPrice = "1000.00", lowStockThreshold = 3),
        updatedAt = at.toString()
    )

    private class EmptyCustomerApi : com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi {
        override suspend fun customers(shopId: String) = Response.success(com.example.bspos.data.micatalogo.api.CustomerListDto(emptyList()))
        override suspend fun createCustomer(shopId: String, request: CustomerUploadRequestDto): Response<RemoteCustomerDto> = error("Unused")
        override suspend fun payment(shopId: String, customerId: String, body: com.example.bspos.data.micatalogo.api.CustomerPaymentDto): Response<com.example.bspos.data.micatalogo.api.CustomerPaymentResponseDto> = error("Unused")
    }
    private class SnapshotApi(var snapshot: CatalogSnapshotDto) : MiCatalogoApi {
        var saleCalls: MutableList<String>? = null
        var catalogStatus = 200
        var beforeCatalog: (suspend () -> Unit)? = null
        override suspend fun catalog(shopId: String): Response<CatalogSnapshotDto> {
            val downloaded = snapshot
            beforeCatalog?.invoke()
            return if (catalogStatus == 200) Response.success(downloaded) else Response.error(catalogStatus, "{}".toResponseBody())
        }
        override suspend fun androidUpdate(): Response<AndroidUpdateDto> = error("Unused")
        override suspend fun login(request: LoginRequestDto): Response<LoginResponseDto> = error("Unused")
        override suspend fun me(): Response<MeDto> = error("Unused")
        override suspend fun updateMe(request: ProfileUpdateDto): Response<MeDto> = error("Unused")
        override suspend fun shops(): Response<List<ShopDto>> = error("Unused")
        override suspend fun previewInventoryImport(shopId: String, file: okhttp3.MultipartBody.Part, mapping: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>): Response<InventoryImportPreviewDto> = error("Unused")
        override suspend fun importInventory(shopId: String, request: InventoryImportRequestDto): Response<InventoryImportResponseDto> = error("Unused")
        override suspend fun adminShops(): Response<List<AdminShopDto>> = error("Unused")
        override suspend fun updateAdminShop(shopId: String, request: AdminShopUpdateDto): Response<AdminShopDto> = error("Unused")
        override suspend fun updateSellerMenus(shopId: String, sellerId: String, request: MenuPermissionsUpdateDto): Response<Map<String, List<String>>> = error("Unused")
        override suspend fun createSeller(shopId: String, request: SellerCreateRequestDto): Response<SellerCreateResponseDto> = error("Unused")
        override suspend fun financeSummary(shopId: String, from: String?, to: String?, sort: String?, direction: String?): Response<FinanceSummaryDto> = error("Unused")
        override suspend fun incomeStatement(shopId: String, from: String?, to: String?): Response<FinanceIncomeStatementDto> = error("Unused")
        override suspend fun cashFlow(shopId: String, from: String?, to: String?): Response<FinanceCashFlowDto> = error("Unused")
        override suspend fun dailyClose(shopId: String, date: String): Response<DailyCloseDto> = error("Unused")
        override suspend fun closeDay(shopId: String, request: DailyCloseRequestDto): Response<DailyCloseDto> = error("Unused")
        override suspend fun currentCashSession(shopId: String): Response<CashCurrentSessionResponseDto> = error("Unused")
        override suspend fun openCashSession(shopId: String, request: CashSessionOpenRequestDto): Response<CashSessionActionResponseDto> = error("Unused")
        override suspend fun closeCashSession(shopId: String, sessionId: String, request: CashSessionCloseRequestDto): Response<CashSessionActionResponseDto> = error("Unused")
        override suspend fun recordCashMovement(shopId: String, sessionId: String, request: CashMovementRequestDto): Response<CashMovementActionResponseDto> = error("Unused")
        override suspend fun expenses(shopId: String, page: Int): Response<ExpensePaginatedResponseDto> = error("Unused")
        override suspend fun expenseCategories(shopId: String): Response<List<ExpenseCategoryDto>> = error("Unused")
        override suspend fun createExpense(shopId: String, request: ExpenseCreateRequestDto): Response<ExpenseActionResponseDto> = error("Unused")
        override suspend fun uploadPosSale(shopId: String, request: PosSaleUploadRequestDto): Response<PosSaleUploadResponseDto> {
            saleCalls?.add(request.items.first().productId)
            return Response.success(PosSaleUploadResponseDto(clientSaleUuid = request.clientSaleUuid))
        }
    }
}
