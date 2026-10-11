package com.example.bspos.presentation.orders

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.local.tenant.tenantTestScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.*
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

/** Holds the HTTP response so the first ON_RESUME frame is verified explicitly. */
@RunWith(AndroidJUnit4::class)
class OrdersEntryTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var vm:OrdersViewModel
    private var shop="qa-entry-a"
    private var response=CompletableDeferred<FeatureResponseDto>()
    private val requests=AtomicInteger()

    private fun show() {
        val api=object:MiCatalogoApi by stub(MiCatalogoApi::class.java) {
            override suspend fun feature(shopId:String,feature:String,period:String?,query:String?,status:String?):Response<FeatureResponseDto> {
                requests.incrementAndGet()
                return Response.success(response.await())
            }
        }
        val connection=stub(MiCatalogoConnectionRepository::class.java) { method ->
            if(method=="activeShopId") shop else error("Unexpected connection method: $method")
        }
        val customers=stub(CustomerRepository::class.java) { method ->
            if(method=="observeAll") flowOf(emptyList<Customer>()) else error("Unexpected customer method: $method")
        }
        val catalog=stub(MiCatalogoCatalogRepository::class.java) { method ->
            if(method=="syncCustomers") MiCatalogoResult.Success(0) else error("Unexpected catalog method: $method")
        }
        compose.runOnIdle { vm=OrdersViewModel(Lazy { api },connection,customers,catalog,tenantTestScope(shop)) }
        compose.setContent { BSPOSTheme { OrdersScreen(viewModel=vm) } }
        compose.waitUntil(5_000) { requests.get()>0 }
    }

    @After fun cancelPendingWork() { if(::vm.isInitialized) compose.runOnIdle { vm.viewModelScope.cancel() } }

    @Test fun firstResumeDoesNotShowEmptyLandingBeforeExistingOrdersArrive() {
        show()
        compose.onNodeWithText("Pedidos").assertExists()
        compose.onNodeWithText("Cargando pedidos…").assertExists()
        compose.onNodeWithText("Aún no tienes pedidos").assertDoesNotExist()
        compose.onNodeWithText("COMPARTIR MI TIENDA").assertDoesNotExist()
        compose.runOnIdle { assertTrue("First refresh without a cache is still an initial load",vm.state.value.loading) }
        response.complete(module("Pedido A"))
        compose.waitUntil(5_000) { !vm.state.value.loading }
        compose.onNodeWithText("Pedido A").assertExists()
    }

    @Test fun emptyLandingAppearsOnlyAfterServerConfirmsAnEmptyList() {
        show()
        compose.onNodeWithText("Aún no tienes pedidos").assertDoesNotExist()
        response.complete(module())
        compose.waitUntil(5_000) { !vm.state.value.loading && requests.get()>0 }
        compose.onNodeWithText("Aún no tienes pedidos").assertExists()
    }

    @Test fun returningToLoadedOrdersRetainsTheListWhileRefreshing() {
        show()
        response.complete(module("Pedido A"))
        compose.waitUntil(5_000) { vm.state.value.rows.isNotEmpty() }
        response=CompletableDeferred()
        compose.runOnIdle { vm.load(refresh=true) }
        compose.waitUntil(5_000) { requests.get()>1 }
        compose.onNodeWithText("Pedido A").assertExists()
        compose.onNodeWithText("Aún no tienes pedidos").assertDoesNotExist()
        compose.runOnIdle { assertFalse(vm.state.value.loading); assertTrue(vm.state.value.refreshing) }
    }

    @Test fun anotherShopDoesNotDisplayThePreviousShopOrdersDuringLoading() {
        show()
        response.complete(module("Pedido A"))
        compose.waitUntil(5_000) { vm.state.value.rows.isNotEmpty() }
        response=CompletableDeferred()
        compose.runOnIdle { shop="qa-entry-b"; vm.load(refresh=true) }
        compose.waitUntil(5_000) { requests.get()>1 }
        compose.onNodeWithText("Pedido A").assertDoesNotExist()
        compose.onNodeWithText("Aún no tienes pedidos").assertDoesNotExist()
        response.complete(module("Pedido B"))
        compose.waitUntil(5_000) { vm.state.value.rows.any { it.primary=="Pedido B" } }
        compose.onNodeWithText("Pedido B").assertExists()
    }

    private fun module(vararg orders:String)=FeatureResponseDto(featureKey="orders",module=FeatureModuleDto(
        rows=orders.mapIndexed { index,name -> FeatureRowDto(id="$index",primary=name,status="Pendiente",value="RD$ 100.00") }))

    private fun <T:Any> stub(type:Class<T>,call:(String)->Any?={ error("Unexpected method: $it") }):T = checkNotNull(type.cast(
        Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,_ -> call(method.name) }))
}
