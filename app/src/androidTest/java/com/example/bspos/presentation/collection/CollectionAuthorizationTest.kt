package com.example.bspos.presentation.collection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.core.database.ShopScope
import com.example.bspos.core.database.ShopSwitchLock
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.micatalogo.*
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.*
import com.example.bspos.domain.usecase.RecordPaymentUseCase
import dagger.Lazy
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionAuthorizationTest {
    private inline fun <reified T> fake(noinline body: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> body(method.name) } as T

    private inner class Fixture(keys: List<String>) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val at = Instant.parse("2026-10-14T12:00:00Z")
        val customer = Customer(UUID.randomUUID(), "QA_NAV_Debtor", balance = 100, createdAt = at, updatedAt = at,
            remoteCustomerId = "remote-customer", remoteShopId = "a")
        val snapshots = MutableStateFlow<List<MiCatalogoShop>?>(listOf(MiCatalogoShop("a", "QA_NAV_A", null,
            menuPermissions = keys, capabilities = mapOf("customers" to "enabled", "credit" to "disabled"))))
        val values = MutableStateFlow<Preferences>(preferencesOf(ShopScope.ACTIVE_SHOP_ID to "a", SHOP_SNAPSHOT_CACHE to
            buildJsonArray { add(buildJsonObject { put("id", "a"); put("menu_permissions", JsonArray(keys.map(::JsonPrimitive))) }) }.toString()))
        val preferences = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(values.value).also { values.value = it }
        }
        val permissions = CachedShopMenuPolicy(preferences, Json { ignoreUnknownKeys = true })
        val scope = ShopScope(preferences, ShopSwitchLock())
        var reads = 0
        var payments = 0
        val connection = fake<MiCatalogoConnectionRepository> { method -> when (method) {
            "observeConnection" -> MutableStateFlow(MiCatalogoConnectionState("http://localhost/", true, activeShopId = "a"))
            "observeShops" -> snapshots
            "activeShopId" -> "a"
            "shops" -> MiCatalogoResult.Success(snapshots.value.orEmpty())
            else -> error(method)
        } }
        val api = fake<MiCatalogoCustomerApi> { method ->
            check(method == "payment")
            payments++
            Response.error<Any>(503, "{}".toResponseBody())
        }
        val catalog = fake<MiCatalogoCatalogRepository> { method ->
            check(method == "syncCustomers")
            reads++
            MiCatalogoResult.Failure("QA offline")
        }
        val scheduler = PosSaleSyncScheduler(context)
        val recorder = RemoteMutationRecorder(RoomDatabaseTransactor(db), db.categoryDao(), db.operationOutboxDao(), scheduler)
        val record = RecordPaymentUseCase(RoomDatabaseTransactor(db), db.paymentDao(), db.saleDao(), db.customerRouteDao(),
            db.cashSessionDao(), db.paymentSyncDao(), db.posSaleOutboxDao(), scheduler, Json, scope, permissions)
        val store = ViewModelStore()
        val model = CollectionViewModel(fake<CustomerRepository> { flowOf(emptyList<Customer>()) }, fake<SaleRepository> { flowOf(emptyList<Sale>()) },
            record, Lazy { api }, connection, db.customerRouteDao(), catalog, scope, db.paymentSyncDao(),
            PaymentSyncRepository(db.paymentSyncDao(), db.posSaleOutboxDao(), db.customerRouteDao(), api, Json), recorder, permissions)
            .also { store.put("collection", it) }
        suspend fun prepare() {
            db.customerRouteDao().insertCustomer(CustomerEntity(customer.id, customer.businessName, balance = 100, creditLimit = 100,
                createdAt = at, updatedAt = at, miCatalogoCustomerId = customer.remoteCustomerId,
                miCatalogoCustomerShopId = "a", tenant = TenantMetadata.assigned("a")))
        }
        fun close() { store.clear(); db.close() }
    }

    @Test fun customerOrCreditPermissionDoesNotLoadOrCollectExistingDebts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(listOf("customers", "credit", "sales"))
        try {
            fixture.prepare(); advanceUntilIdle()
            assertFalse(fixture.model.canCollect.value)
            fixture.model.collectRemote(fixture.customer, 25, PaymentMethod.TRANSFER)
            fixture.model.collecting.first { !it }
            advanceUntilIdle()
            assertEquals(0, fixture.reads)
            assertEquals(0, fixture.payments)
            assertTrue(fixture.db.paymentSyncDao().pending().isEmpty())
            assertEquals(100L, fixture.db.customerRouteDao().findCustomer(fixture.customer.id, "a")!!.balance)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }

    @Test fun collectionOnlyQueuesOnceButFreshCacheRevocationOverridesAnOpenStaleScreen() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(listOf("collections"))
        try {
            fixture.prepare(); advanceUntilIdle()
            assertTrue(fixture.model.canCollect.value)
            fixture.model.collectRemote(fixture.customer, 25, PaymentMethod.TRANSFER)
            fixture.model.collecting.first { !it }
            advanceUntilIdle()
            val pending = fixture.db.paymentSyncDao().pending()
            assertEquals("Mensaje: ${fixture.model.message.value}; filas: ${fixture.db.paymentSyncDao().activeForCustomer("a", fixture.customer.id.toString())}", 1, pending.size)
            val before = pending.single()
            assertEquals(1, fixture.payments)
            fixture.values.value = preferencesOf(ShopScope.ACTIVE_SHOP_ID to "a", SHOP_SNAPSHOT_CACHE to "[]")
            fixture.model.collectRemote(fixture.customer, 50, PaymentMethod.TRANSFER)
            fixture.model.collecting.first { !it }
            advanceUntilIdle()
            assertEquals(before.payload, fixture.db.paymentSyncDao().pending().single().payload)
            assertEquals(1, fixture.payments)
            assertTrue(fixture.model.message.value.orEmpty().contains("permiso"))
            fixture.snapshots.value = emptyList()
            advanceUntilIdle()
            assertFalse(fixture.model.canCollect.value)
            assertNull(fixture.model.customer.value)
        } finally { fixture.close(); Dispatchers.resetMain() }
    }
}
