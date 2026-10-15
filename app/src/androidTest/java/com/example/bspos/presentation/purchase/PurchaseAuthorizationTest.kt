package com.example.bspos.presentation.purchase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.bspos.core.database.ShopScope
import com.example.bspos.core.database.ShopSwitchLock
import com.example.bspos.data.micatalogo.ShopReadModelEvents
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.PurchaseDocumentDto
import com.example.bspos.data.micatalogo.dto.PurchaseWorkspaceDto
import com.example.bspos.domain.model.MiCatalogoConnectionState
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.ShopMutationGateway
import dagger.Lazy
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseAuthorizationTest {
    private class Fixture(keys: List<String>) {
        val state = MutableStateFlow(MiCatalogoConnectionState("http://localhost/", true, activeShopId = "a"))
        val snapshots = MutableStateFlow<List<MiCatalogoShop>?>(listOf(MiCatalogoShop("a", "QA_NAV_A", null, menuPermissions = keys)))
        val calls = mutableListOf<String>()
        var pending: Continuation<Response<PurchaseWorkspaceDto>>? = null
        var delayRead = false
        val documents = listOf(PurchaseDocumentDto(id = "invoice-a", type = "purchase_invoice"), PurchaseDocumentDto(id = "container-a", type = "container"))
        val values = MutableStateFlow(preferencesOf(ShopScope.ACTIVE_SHOP_ID to "a"))
        val preferences = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(values.value).also { values.value = it }
        }
        val connection = Proxy.newProxyInstance(MiCatalogoConnectionRepository::class.java.classLoader, arrayOf(MiCatalogoConnectionRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "observeConnection" -> state
                "observeShops" -> snapshots
                "activeShopId" -> state.value.activeShopId
                "shops" -> MiCatalogoResult.Success(snapshots.value.orEmpty())
                else -> error(method.name)
            }
        } as MiCatalogoConnectionRepository
        val api = Proxy.newProxyInstance(MiCatalogoApi::class.java.classLoader, arrayOf(MiCatalogoApi::class.java)) { _, method, args ->
            calls += method.name
            if (delayRead) {
                @Suppress("UNCHECKED_CAST")
                pending = args.last() as Continuation<Response<PurchaseWorkspaceDto>>
                COROUTINE_SUSPENDED
            } else Response.success(PurchaseWorkspaceDto(documents = documents))
        } as MiCatalogoApi
        val gateway = object : ShopMutationGateway {
            override suspend fun submit(shop: String, type: String, request: JsonObject, resource: String?): JsonObject {
                calls += "$type:$resource"
                return buildJsonObject { put("message", "QA_NAV_ACK") }
            }
        }
        val store = ViewModelStore()
        val model = PurchaseModuleViewModel(Lazy { api }, connection, ApplicationProvider.getApplicationContext<Context>(),
            gateway, ShopScope(preferences, ShopSwitchLock()), ShopReadModelEvents(), Json).also { store.put("purchase", it) }
    }

    @Test fun noPurchasePermissionsMeansNoReadOrQueuedMutation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(listOf("reports"))
        try {
            advanceUntilIdle()
            fixture.model.load("containers")
            fixture.model.create("container", "Forbidden", null, "draft", null, emptyList())
            fixture.model.createSupplier("Forbidden", "DOP", "", "", "", "")
            advanceUntilIdle()
            assertTrue("No se consultan ni encolan operaciones ajenas: ${fixture.calls}", fixture.calls.isEmpty())
            assertNull(fixture.model.state.value.workspace)
        } finally { fixture.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun supplierCreationReloadsOnlySuppliers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(listOf("suppliers"))
        try {
            fixture.model.load("suppliers")
            advanceUntilIdle()
            fixture.calls.clear()
            fixture.model.createSupplier("QA_NAV_Supplier", "DOP", "", "", "", "")
            advanceUntilIdle()
            assertEquals(listOf("supplier_create:null", "suppliers"), fixture.calls)
        } finally { fixture.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun invoicePermissionCannotCreateOrReceiveAContainer() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(listOf("purchase_invoices"))
        try {
            fixture.model.load("purchase_invoices")
            advanceUntilIdle()
            fixture.calls.clear()
            fixture.model.create("container", "Forbidden", null, "draft", null, emptyList())
            fixture.model.receive("container-a", "purchase_invoices")
            advanceUntilIdle()
            assertTrue("No debe encolarse un tipo ajeno: ${fixture.calls}", fixture.calls.isEmpty())
            assertEquals(listOf("invoice-a"), fixture.model.state.value.workspace!!.documents.map { it.id })
            fixture.model.receive("invoice-a", "purchase_invoices")
            advanceUntilIdle()
            assertTrue(fixture.calls.contains("purchase_receive:invoice-a"))
        } finally { fixture.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun revocationClearsWorkspaceAndDiscardsAnAlreadyStartedResponse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(listOf("purchase_invoices"))
        try {
            fixture.delayRead = true
            fixture.model.load("purchase_invoices")
            advanceUntilIdle()
            val delayed = checkNotNull(fixture.pending)
            fixture.snapshots.value = listOf(fixture.snapshots.value!!.single().copy(menuPermissions = emptyList()))
            advanceUntilIdle()
            delayed.resume(Response.success(PurchaseWorkspaceDto(documents = fixture.documents)))
            advanceUntilIdle()
            assertNull("Una respuesta tardía no restaura datos revocados", fixture.model.state.value.workspace)
            fixture.calls.clear()
            fixture.model.create("purchase_invoice", "Forbidden", null, "draft", null, emptyList())
            advanceUntilIdle()
            assertTrue(fixture.calls.isEmpty())
        } finally { fixture.store.clear(); Dispatchers.resetMain() }
    }

    @Test fun switchingAwayAndBackDoesNotAcceptTheFirstShopsOldResponse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(listOf("purchase_invoices"))
        try {
            fixture.delayRead = true
            fixture.model.load("purchase_invoices"); advanceUntilIdle()
            val original = checkNotNull(fixture.pending)
            fixture.state.value = fixture.state.value.copy(activeShopId = "b")
            fixture.values.value = preferencesOf(ShopScope.ACTIVE_SHOP_ID to "b")
            fixture.snapshots.value = listOf(MiCatalogoShop("b", "QA_NAV_B", null, menuPermissions = listOf("purchase_invoices")))
            advanceUntilIdle()
            fixture.state.value = fixture.state.value.copy(activeShopId = "a")
            fixture.values.value = preferencesOf(ShopScope.ACTIVE_SHOP_ID to "a")
            fixture.snapshots.value = listOf(MiCatalogoShop("a", "QA_NAV_A", null, menuPermissions = listOf("purchase_invoices")))
            advanceUntilIdle()
            val current = checkNotNull(fixture.pending)
            original.resume(Response.success(PurchaseWorkspaceDto(documents = listOf(PurchaseDocumentDto(id = "stale", type = "purchase_invoice")))))
            advanceUntilIdle()
            assertNull(fixture.model.state.value.workspace)
            current.resume(Response.success(PurchaseWorkspaceDto(documents = listOf(PurchaseDocumentDto(id = "fresh", type = "purchase_invoice")))))
            advanceUntilIdle()
            assertEquals(listOf("fresh"), fixture.model.state.value.workspace!!.documents.map { it.id })
        } finally { fixture.store.clear(); Dispatchers.resetMain() }
    }
}
