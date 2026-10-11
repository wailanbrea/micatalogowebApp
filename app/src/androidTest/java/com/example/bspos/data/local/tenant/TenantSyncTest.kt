package com.example.bspos.data.local.tenant

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.database.*
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.*
import com.example.bspos.data.micatalogo.*
import com.example.bspos.data.micatalogo.api.*
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.data.repository.CustomerRepositoryImpl
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.MiCatalogoResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TenantSyncTest {
    private val a = "qa-shop-a"
    private val b = "qa-shop-b"
    private val at = Instant.parse("2026-10-10T12:00:00Z")
    private lateinit var db: AppDatabase
    private val preferences = MemoryPreferences()
    private val lock = ShopSwitchLock()
    private val shops = ShopScope(preferences, lock)
    private val remoteCustomers = mutableMapOf<String, List<RemoteCustomerDto>>()
    private var createCalls = 0
    private var createStatus = 200
    private var beforeDownload: (suspend () -> Unit)? = null
    private var beforeCreate: (suspend (String, CustomerUploadRequestDto) -> Unit)? = null
    private lateinit var catalog: MiCatalogoCatalogRepositoryImpl
    private lateinit var customerApi: MiCatalogoCustomerApi

    @Before
    fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(InventoryIntegrity).addCallback(TenantIntegrity).build()
        customerApi = object : MiCatalogoCustomerApi {
            override suspend fun customers(shopId: String): Response<CustomerListDto> {
                val snapshot = remoteCustomers[shopId].orEmpty().toList()
                beforeDownload?.invoke()
                return Response.success(CustomerListDto(snapshot))
            }
            override suspend fun createCustomer(shopId: String, request: CustomerUploadRequestDto): Response<RemoteCustomerDto> {
                createCalls++
                beforeCreate?.invoke(shopId, request)
                return if (createStatus==200) Response.success(RemoteCustomerDto("remote-${request.clientCustomerUuid}",
                    clientCustomerUuid=request.clientCustomerUuid, name=request.name ?: request.firstName, isActive=true))
                else Response.error(createStatus, "{\"message\":\"Solicitud rechazada\"}".toResponseBody())
            }
            override suspend fun payment(shopId: String, customerId: String, body: CustomerPaymentDto): Response<CustomerPaymentResponseDto> = error("Unused")
        }
        val api = Proxy.newProxyInstance(MiCatalogoApi::class.java.classLoader, arrayOf(MiCatalogoApi::class.java)) { _, method, arguments ->
            check(method.name=="catalog") { "Unexpected API call: ${method.name}" }
            val shop = arguments!![0] as String
            Response.success(CatalogSnapshotDto(ShopDto(id=shop, name="Tienda $shop"),
                listOf(RemoteCategoryDto("same-category", "Categoría $shop", "category", 0, "active", at.toString())),
                listOf(RemoteProductDto(id="same-product", categoryId="same-category", name="Producto $shop",
                    price="10.00", currency="DOP", saleUnit="unit", availabilityStatus="available", moderationStatus="active",
                    inventory=RemoteInventoryDto(true, stockQuantity=7, costPrice="3.00"), updatedAt=at.toString())), at.toString()))
        } as MiCatalogoApi
        catalog = MiCatalogoCatalogRepositoryImpl(api, MiCatalogoImageDownloader(context, "http://127.0.0.1:9/"),
            RoomDatabaseTransactor(db), db.categoryDao(), db.unitOfMeasureDao(), db.productDao(), db.inventoryDao(),
            db.posSaleOutboxDao(), Json { ignoreUnknownKeys=true }, customerApi, db.customerRouteDao(),
            db.paymentSyncDao(), db.operationOutboxDao())
    }

    @After fun close() = db.close()

    @Test fun sameRemoteIdentifiersAndRepeatedBootstrapStaySeparate() = runTest {
        remoteCustomers[a] = listOf(RemoteCustomerDto("same-customer", name="Cliente A", isActive=false))
        remoteCustomers[b] = listOf(RemoteCustomerDto("same-customer", name="Cliente B", isActive=true))
        for (shop in listOf(a,b,a,b)) assertTrue(catalog.syncCatalog(shop) is MiCatalogoResult.Success)
        val pa = db.productDao().findRemote(a,"same-product")!!
        val pb = db.productDao().findRemote(b,"same-product")!!
        assertNotEquals(pa.id,pb.id)
        assertEquals("Producto $a",pa.name)
        assertEquals("Producto $b",pb.name)
        assertEquals(7L,db.inventoryDao().findStock(pa.id,com.example.bspos.domain.model.InventoryLocationType.MAIN_WAREHOUSE,"MAIN",a)!!.quantity)
        assertNull(db.inventoryDao().findStock(pa.id,com.example.bspos.domain.model.InventoryLocationType.MAIN_WAREHOUSE,"MAIN",b))
        val ca=db.customerRouteDao().observeCustomers(a).first().single()
        val cb=db.customerRouteDao().observeCustomers(b).first().single()
        assertNotEquals(ca.id,cb.id)
        assertFalse(ca.isActive)
        assertEquals(0,createCalls) // A download can never publish every cached customer into this shop.
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM inventory_movements").use { it.moveToFirst(); assertEquals(2,it.getInt(0)) }
    }

    @Test fun missingCustomersAreArchivedOnlyInTheirShopAndPendingEditsSurvive() = runTest {
        val ca=localCustomer(a,"A")
        val pending=localCustomer(a,"Pendiente")
        val cb=localCustomer(b,"B")
        db.customerRouteDao().insertCustomer(ca)
        db.customerRouteDao().insertCustomer(pending)
        db.customerRouteDao().insertCustomer(cb)
        queueCustomer(pending)
        assertTrue(catalog.syncCustomers(a) is MiCatalogoResult.Success)
        val historical=db.customerRouteDao().findCustomer(ca.id,a)!!
        assertNotNull(historical.deletedAt)
        assertFalse(historical.isActive)
        assertEquals(pending,db.customerRouteDao().findCustomer(pending.id,a))
        assertEquals(cb,db.customerRouteDao().findCustomer(cb.id,b))
        assertEquals(0,createCalls)
    }

    @Test fun acknowledgementDuringDownloadDoesNotOverwritePendingCustomerProjection() = runTest {
        val customer=localCustomer(a,"Cambio pendiente")
        db.customerRouteDao().insertCustomer(customer)
        queueCustomer(customer)
        remoteCustomers[a]=listOf(RemoteCustomerDto(customer.miCatalogoCustomerId!!,clientCustomerUuid=customer.id.toString(),name="Nombre viejo",isActive=false))
        beforeDownload={ db.operationOutboxDao().confirmSent("edit-${customer.id}",a) }
        assertTrue(catalog.syncCustomers(a) is MiCatalogoResult.Success)
        assertEquals(customer,db.customerRouteDao().findCustomer(customer.id,a))
    }

    @Test fun remoteClientUuidFromAnotherShopNeverReassignsTheCachedCustomer() = runTest {
        val customer=localCustomer(b,"Sólo B")
        db.customerRouteDao().insertCustomer(customer)
        remoteCustomers[a]=listOf(RemoteCustomerDto("remote-a",clientCustomerUuid=customer.id.toString(),name="Sólo A",isActive=true))
        assertTrue(catalog.syncCustomers(a) is MiCatalogoResult.Success)
        assertEquals(customer,db.customerRouteDao().findCustomer(customer.id,b))
        val imported=db.customerRouteDao().observeCustomers(a).first().single()
        assertNotEquals(customer.id,imported.id)
        assertEquals("Sólo A",imported.businessName)
    }

    @Test fun onlineCustomerCreatePersistsOnServerBeforeRoomAndRejectDoesNotLeavePhantom() = runTest {
        preferences.select(a)
        val repo=CustomerRepositoryImpl(db.customerRouteDao(),shops=shops,api=customerApi)
        val record=Customer(UUID.randomUUID(),"Nuevo",createdAt=at,updatedAt=at)
        beforeCreate={ shop,body ->
            assertEquals(a,shop)
            assertNull(db.customerRouteDao().findCustomer(UUID.fromString(body.clientCustomerUuid),a))
        }
        repo.insert(record)
        assertNotNull(db.customerRouteDao().findCustomer(record.id,a)!!.miCatalogoCustomerId)
        createStatus=422
        val rejected=record.copy(id=UUID.randomUUID())
        assertTrue(runCatching { repo.insert(rejected) }.isFailure)
        assertNull(db.customerRouteDao().findCustomer(rejected.id,a))
        preferences.select(b)
        assertFalse(repo.update(record.copy(businessName="Intento cruzado")))
        assertEquals("Nuevo",db.customerRouteDao().findCustomer(record.id,a)!!.businessName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun switchClearsObservedCustomersAndMutationKeepsOneTenantUntilCommit() = runTest {
        val ca=localCustomer(a,"Cliente A")
        val cb=localCustomer(b,"Cliente B")
        db.customerRouteDao().insertCustomer(ca)
        db.customerRouteDao().insertCustomer(cb)
        preferences.select(a)
        val repo=CustomerRepositoryImpl(db.customerRouteDao(),shops=shops,api=customerApi)
        assertEquals(listOf(ca.id),repo.observeAll().first { it.isNotEmpty() }.map { it.id })
        preferences.select(b)
        assertEquals(listOf(cb.id),repo.observeAll().first { it.isNotEmpty() }.map { it.id })
        assertNull(repo.findById(ca.id))
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val mutation=async { shops.mutate { shop ->
            assertEquals(b,shop)
            shops.mutate { nested -> assertEquals(b,nested) } // No deadlock in an enclosing use-case transaction.
            entered.complete(Unit); release.await(); assertEquals(b,shops.current())
        } }
        entered.await()
        val switching=async { lock.mutex.withLock { preferences.select(a) } }
        runCurrent()
        assertFalse(switching.isCompleted)
        release.complete(Unit)
        mutation.await(); switching.await()
        assertEquals(a,shops.current())
    }

    private fun localCustomer(shop: String,name: String): CustomerEntity {
        val id=UUID.randomUUID()
        return CustomerEntity(id,name,miCatalogoCustomerId="remote-$id",miCatalogoCustomerShopId=shop,
            createdAt=at,updatedAt=at,tenant=TenantMetadata.assigned(shop))
    }
    private suspend fun queueCustomer(customer: CustomerEntity) = db.operationOutboxDao().insert(OperationOutboxEntity(
        "edit-${customer.id}",customer.tenant.shopId!!,"", """{"type":"customer_upsert","client_customer_uuid":"${customer.id}"}""",at))

    private class MemoryPreferences : DataStore<Preferences> {
        private val values=MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = values
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(values.value).also { values.value=it }
        suspend fun select(shop: String) { edit { it[ShopScope.ACTIVE_SHOP_ID]=shop } }
    }
}
