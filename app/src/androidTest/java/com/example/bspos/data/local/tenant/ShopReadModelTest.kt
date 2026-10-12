package com.example.bspos.data.local.tenant

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.micatalogo.*
import com.example.bspos.data.micatalogo.api.*
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant
import java.util.UUID

class ShopReadModelTest {
    @Test fun authoritativeCatalogRestoresCustomUnitsRoutesAssignmentsAndStockWithoutDuplicatingHistory() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val json = Json { ignoreUnknownKeys = true }
        val at = Instant.parse("2026-10-12T10:00:00Z")
        val shop = "qa-read-model"
        val category = UUID.randomUUID(); val unit = UUID.randomUUID(); val route = UUID.randomUUID(); val load = UUID.randomUUID(); val line = UUID.randomUUID()
        val remoteProduct = RemoteMutationRecorder.productId(UUID.randomUUID())
        fun record(kind: String, id: UUID, block: JsonObjectBuilder.() -> Unit) = StoreRecordDto(kind, id.toString(), buildJsonObject(block), at.toString())
        val records = listOf(
            record("unit", unit) { put("name", "Caja"); put("abbreviation", "caj"); put("is_active", true); put("is_deleted", false); put("created_at", at.toString()) },
            record("route", route) { put("name", "Ruta norte"); put("code", "N"); put("is_active", true); put("created_at", at.toString()) },
            record("route_members", route) { put("customers", buildJsonArray { add(buildJsonObject { put("customer_id", "qa-customer"); put("visit_order", 0) }) }) },
            record("route_load", load) { put("route_id", route.toString()); put("date", at.toString()); put("created_at", at.toString()); put("status", "OPEN");
                put("items", buildJsonArray { add(buildJsonObject { put("id", line.toString()); put("product_id", remoteProduct); put("quantity", 4); put("unit_cost_cents", 700) }) }) }
        )
        var snapshot = CatalogSnapshotDto(ShopDto(id = shop, name = "QA"),
            listOf(RemoteCategoryDto("1", "Categoría QA", "qa", 0, "active", at.toString(), clientRecordUuid = category.toString())),
            listOf(RemoteProductDto(id = remoteProduct, categoryId = "1", unitUuid = unit.toString(), name = "Producto QA", price = "10.00", currency = "DOP",
                saleUnit = "unit", availabilityStatus = "available", moderationStatus = "active", inventory = RemoteInventoryDto(true, stockQuantity = 6, costPrice = "7.00"), updatedAt = at.toString())),
            at.toString(), records, listOf(RouteStockDto(route.toString(), remoteProduct, 4, 700)))
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith("/catalog")) json.encodeToString(snapshot)
                else """{"customers":[{"id":"qa-customer","name":"Cliente QA","balance":"0.00","credit_limit":"100.00"}]}"""
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Isolated fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val retrofit = Retrofit.Builder().baseUrl("https://isolated.example.test/").client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        val projection = StoreRecordProjection(db.unitOfMeasureDao(), db.inventoryAdjustmentReasonDao(), db.customerRouteDao(), db.routeLoadDao(), db.productDao(), db.inventoryDao())
        val catalog = MiCatalogoCatalogRepositoryImpl(retrofit.create(MiCatalogoApi::class.java), MiCatalogoImageDownloader(context, "https://isolated.example.test/"),
            RoomDatabaseTransactor(db), db.categoryDao(), db.unitOfMeasureDao(), db.productDao(), db.inventoryDao(), db.posSaleOutboxDao(), json,
            retrofit.create(MiCatalogoCustomerApi::class.java), db.customerRouteDao(), db.paymentSyncDao(), db.operationOutboxDao(), projection)
        try {
            repeat(2) { assertTrue(catalog.syncCatalog(shop) is MiCatalogoResult.Success) }
            val product = db.productDao().findRemote(shop, remoteProduct)!!
            assertEquals(category, product.categoryId); assertEquals(unit, product.unitId)
            assertEquals(1, db.customerRouteDao().assignmentsForSync(route, shop).size)
            assertEquals(6L, db.inventoryDao().findStock(product.id, InventoryLocationType.MAIN_WAREHOUSE, "MAIN", shop)!!.quantity)
            assertEquals(4L, db.inventoryDao().findStock(product.id, InventoryLocationType.ROUTE, route.toString(), shop)!!.quantity)
            assertEquals(700L, db.routeLoadDao().itemsForSync(load, shop).single().unitCostSnapshot)
            val initialLedger = db.inventoryDao().observeMovements(product.id, InventoryLocationType.ROUTE, route.toString(), null, null, shop).first().size
            projection.applyStock(shop, listOf(RouteStockDto(route.toString(), remoteProduct, 0, 700)), emptySet(), at.plusSeconds(1))
            projection.applyStock(shop, snapshot.routeStock, emptySet(), at.plusSeconds(2))
            assertEquals(initialLedger + 2, db.inventoryDao().observeMovements(product.id, InventoryLocationType.ROUTE, route.toString(), null, null, shop).first().size)
            snapshot = snapshot.copy(shop = ShopDto(id = "qa-other"))
            assertTrue(catalog.syncCatalog("qa-other") is MiCatalogoResult.Failure)
            assertEquals(TenantMetadata.assigned(shop), db.productDao().findRemote(shop, remoteProduct)!!.tenant)
            assertTrue(db.productDao().observeAll("qa-other").first().isEmpty())
        } finally { db.close(); http.dispatcher.executorService.shutdown() }
    }

    @Test fun aMatchingEnvelopeUuidWithoutTheExpectedRecordIsNotAnAcknowledgement() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val operation = UUID.randomUUID().toString(); val id = UUID.randomUUID().toString()
            val row = com.example.bspos.data.local.entity.OperationOutboxEntity(operation, "qa", "",
                buildJsonObject { put("type", "unit_upsert"); put("record_id", id); put("client_operation_uuid", operation) }.toString(), Instant.now())
            db.operationOutboxDao().insert(row)
            val api = object : MiCatalogoOperationApi {
                override suspend fun submit(shopId: String, payload: JsonObject) = retrofit2.Response.success(buildJsonObject {
                    put("client_operation_uuid", operation); put("record_id", UUID.randomUUID().toString())
                })
            }
            assertEquals(OperationSubmission.RETRY, OperationSyncRepository(api, db.operationOutboxDao(), Json).submit(row))
            assertEquals("PENDING", db.operationOutboxDao().find(operation)!!.state)
            assertEquals(row.payload, db.operationOutboxDao().find(operation)!!.payload)
        } finally { db.close() }
    }
}
