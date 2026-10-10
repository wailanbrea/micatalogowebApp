package com.example.bspos.presentation.dashboard

import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.*
import dagger.Lazy
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class FirstSaleStatusReaderTest {
    private fun api(call: (Array<out Any?>) -> Response<FeatureResponseDto>): MiCatalogoApi =
        Proxy.newProxyInstance(MiCatalogoApi::class.java.classLoader, arrayOf(MiCatalogoApi::class.java)) { _, method, args ->
            check(method.name == "feature") { "Milestone checking must only read sales: ${method.name}" }
            call(args ?: emptyArray())
        } as MiCatalogoApi
    private fun count(value: String) = Response.success(FeatureResponseDto(module = FeatureModuleDto(kpis = listOf(FeatureKpiDto("Ventas del período", value)))))

    @Test fun lifetimeCountCompletesMilestoneEvenWhenNoInvoiceIsStoredOnThisDevice() = runTest {
        val requests = mutableListOf<String>()
        val reader = FirstSaleStatusReader(Lazy { api { args ->
            assertEquals("active-shop", args[0]); assertEquals("sales", args[1]); assertEquals("all", args[2])
            requests += args[4].toString(); count("14")
        } })
        assertTrue(reader.hasSale("active-shop"))
        assertEquals(listOf("paid"), requests)
    }

    @Test fun creditAndMixedSalesQualifyWithoutCashSales() = runTest {
        val requests = mutableListOf<String>()
        val reader = FirstSaleStatusReader(Lazy { api { args ->
            val status = args[4].toString(); requests += status; count(if (status == "credit") "2" else "0")
        } })
        assertTrue(reader.hasSale("shop-credit"))
        assertEquals(listOf("paid", "partial", "credit"), requests)
    }

    @Test fun onlyConfirmedEmptyHistoryReturnsNoSale() = runTest {
        val reader = FirstSaleStatusReader(Lazy { api { count("0") } })
        assertFalse(reader.hasSale("new-shop"))
    }

    @Test fun failedLookupIsUnknownRatherThanAnEmptySalesHistory() = runTest {
        val reader = FirstSaleStatusReader(Lazy { api { Response.error(503, "{}".toResponseBody()) } })
        assertTrue(runCatching { reader.hasSale("offline-shop") }.isFailure)
    }
}
