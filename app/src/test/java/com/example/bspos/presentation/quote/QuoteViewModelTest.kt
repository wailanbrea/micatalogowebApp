package com.example.bspos.presentation.quote

import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.api.CustomerListDto
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class QuoteViewModelTest {
    private val product = FeatureProductDto(id = "product-1", name = "Perfume", price = "950.00", stock = 0)
    private inline fun <reified T> proxy(noinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args ?: emptyArray())
        } as T

    private fun connection() = proxy<MiCatalogoConnectionRepository> { method, _ ->
        check(method == "activeShopId") { "Unexpected connection operation: $method" }
        "shop-1"
    }
    private fun clients(vararg customers: RemoteCustomerDto) = Lazy {
        proxy<MiCatalogoCustomerApi> { method, _ ->
            check(method == "customers")
            Response.success(CustomerListDto(customers.toList()))
        }
    }

    @Test fun savingCallsOnlyQuoteCreationAndBlocksDuplicateTap() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val requests = mutableListOf<QuoteCreateRequestDto>()
            val api = proxy<MiCatalogoApi> { method, args ->
                when (method) {
                    "createQuote" -> { requests += args[1] as QuoteCreateRequestDto; Response.success(QuoteResponseDto(message = "Cotización guardada.")) }
                    "feature" -> Response.success(FeatureResponseDto(module = FeatureModuleDto(quoteProducts = listOf(product))))
                    else -> error("Saving a quote must not call sales or inventory: $method")
                }
            }
            val model = QuoteViewModel(Lazy { api }, connection(), clients())
            model.add(product)
            model.add(product)
            model.save(" Cliente ", "8298144525", "2026-10-14", "Solo cotización")
            model.save("Cliente", "", "", "")
            assertTrue(model.state.value.saving)
            advanceUntilIdle()
            assertEquals(1, requests.size)
            assertEquals(2, requests.single().items.single().quantity)
            assertEquals("950.00", requests.single().items.single().unitPrice)
            assertEquals("Cliente", requests.single().customerName)
            assertNull(requests.single().customerId)
            assertFalse(model.state.value.saving)
            assertTrue(model.state.value.cart.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun rejectedSaveKeepsDraftAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val api = proxy<MiCatalogoApi> { method, _ ->
                check(method == "createQuote")
                Response.error<QuoteResponseDto>(422, "{}".toResponseBody())
            }
            val model = QuoteViewModel(Lazy { api }, connection(), clients())
            model.add(product)
            model.save("", "", "", "")
            advanceUntilIdle()
            assertFalse(model.state.value.saving)
            assertEquals(1, model.state.value.cart[product])
            assertNotNull(model.state.value.error)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun conversionIsExplicitAndKeepsInvoiceForSalesNavigation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var conversions = 0
            val api = proxy<MiCatalogoApi> { method, _ ->
                when (method) {
                    "convertQuote" -> {
                        conversions++
                        Response.success(QuoteResponseDto(invoiceNumber = "FAC-001", invoiceUrl = "https://example.test/factura"))
                    }
                    "feature" -> Response.success(FeatureResponseDto())
                    else -> error("Unexpected operation: $method")
                }
            }
            val model = QuoteViewModel(Lazy { api }, connection(), clients())
            model.convert("quote-1")
            model.convert("quote-1")
            advanceUntilIdle()
            assertEquals(1, conversions)
            assertEquals("FAC-001", model.state.value.convertedInvoiceNumber)
            assertEquals("https://example.test/factura", model.state.value.convertedInvoiceUrl)
            assertNull(model.state.value.convertingId)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun existingCustomerIsLinkedByServerIdAndKeepsRegisteredContact() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var request: QuoteCreateRequestDto? = null
            val api = proxy<MiCatalogoApi> { method, args ->
                when (method) {
                    "feature" -> Response.success(FeatureResponseDto())
                    "createQuote" -> { request = args[1] as QuoteCreateRequestDto; Response.success(QuoteResponseDto(message = "Guardada")) }
                    else -> error("Unexpected operation: $method")
                }
            }
            val model = QuoteViewModel(Lazy { api }, connection(), clients(RemoteCustomerDto(id = "customer-1", name = "María Gómez", phone = "8295550100")))
            model.load(); advanceUntilIdle()
            model.add(product)
            model.save("Nombre genérico anterior", "000", "2026-10-14", "", "customer-1")
            advanceUntilIdle()
            assertEquals("customer-1", request?.customerId)
            assertEquals("María Gómez", request?.customerName)
            assertEquals("8295550100", request?.customerPhone)
        } finally { Dispatchers.resetMain() }
    }
}
