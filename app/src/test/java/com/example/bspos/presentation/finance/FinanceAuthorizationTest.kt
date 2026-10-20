package com.example.bspos.presentation.finance

import com.example.bspos.data.micatalogo.ShopReadModelEvents
import com.example.bspos.data.micatalogo.dto.FinanceSummaryDto
import com.example.bspos.data.micatalogo.dto.FinancePeriodDto
import com.example.bspos.data.micatalogo.dto.FinanceCurrentStateDto
import com.example.bspos.data.micatalogo.dto.FinanceAgingDto
import com.example.bspos.domain.model.MiCatalogoConnectionState
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.repository.FinanceRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceAuthorizationTest {
    @Test fun delegatedDayCloseReadsOnlyItsReconciliationWithoutRequiringProfitAccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val calls = mutableListOf<String>()
            val viewModel = model(listOf("day_close"), calls)
            viewModel.selectPeriod(FinancePeriodFilter.TODAY)
            advanceUntilIdle()
            assertEquals(listOf("getDailyClose"), calls)
            viewModel.setMenuCeiling(setOf("day_close"))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.access.finance)
            assertTrue(viewModel.uiState.value.access.dayClose)
        } finally { Dispatchers.resetMain() }
    }
    private inline fun <reified T> proxy(noinline call: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> call(method.name) } as T

    private fun model(keys: List<String>, calls: MutableList<String>): FinanceViewModel {
        val connection = proxy<MiCatalogoConnectionRepository> { method -> when (method) {
            "observeConnection" -> MutableStateFlow(MiCatalogoConnectionState(baseUrl = "http://localhost/", hasAccessToken = true, activeShopId = "shop-a"))
            "activeShopId" -> "shop-a"
            "shops" -> MiCatalogoResult.Success(listOf(MiCatalogoShop(id = "shop-a", name = "QA_NAV_A", slug = null, menuPermissions = keys)))
            else -> error("Unexpected connection call: $method")
        } }
        val finance = proxy<FinanceRepository> { method -> calls += method; MiCatalogoResult.Failure("QA probe") }
        return FinanceViewModel(finance, connection, ShopReadModelEvents())
    }

    @Test fun cashOnlyDoesNotReadFinanceExpensesOrDayClose() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val calls = mutableListOf<String>()
            val viewModel = model(listOf("cash"), calls)
            advanceUntilIdle()
            assertEquals(listOf("getCurrentCashSession"), calls)
            calls.clear()
            viewModel.selectPeriod(FinancePeriodFilter.TODAY)
            advanceUntilIdle()
            assertEquals(listOf("getCurrentCashSession"), calls)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun expensesOnlyDoesNotReadFinanceOrCash() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val calls = mutableListOf<String>()
            model(listOf("expenses"), calls)
            advanceUntilIdle()
            assertEquals(setOf("getExpenses", "getExpenseCategories"), calls.toSet())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun accountantFinancialReadDoesNotFetchOperationalSiblingsOrAllowMutations() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val calls = mutableListOf<String>()
            val viewModel = model(listOf("finance", "reports"), calls)
            advanceUntilIdle()
            assertEquals(listOf("getFinanceSummary"), calls)
            calls.clear()
            viewModel.openRemoteCash("10.00", null)
            viewModel.registerExpense("Blocked", "10.00", null, "cash", "2026-10-14")
            viewModel.closeDaily(null, null)
            advanceUntilIdle()
            assertTrue(calls.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun noFinancialKeysMeansNoFinancialRepositoryRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val calls = mutableListOf<String>()
            model(emptyList(), calls)
            advanceUntilIdle()
            assertTrue(calls.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun shellCeilingCanRevokeButCannotGrantAccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val calls = mutableListOf<String>()
            val viewModel = model(listOf("cash"), calls)
            viewModel.setMenuCeiling(setOf("finance", "cash", "expenses"))
            advanceUntilIdle()
            assertEquals(listOf("getCurrentCashSession"), calls)
            assertEquals(listOf(4), viewModel.uiState.value.access.tabs)
            calls.clear()
            viewModel.setMenuCeiling(emptySet())
            viewModel.refresh()
            viewModel.openRemoteCash("10.00", null)
            advanceUntilIdle()
            assertTrue(calls.isEmpty())
            assertTrue(viewModel.uiState.value.access.tabs.isEmpty())
            assertNull(viewModel.uiState.value.cashSession)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun lateSummaryIsDiscardedAfterPermissionRevocation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var pending: Continuation<MiCatalogoResult<FinanceSummaryDto>>? = null
            val connection = proxy<MiCatalogoConnectionRepository> { method -> when (method) {
                "observeConnection" -> MutableStateFlow(MiCatalogoConnectionState(baseUrl = "http://localhost/", hasAccessToken = true, activeShopId = "shop-a"))
                "activeShopId" -> "shop-a"
                "shops" -> MiCatalogoResult.Success(listOf(MiCatalogoShop(id = "shop-a", name = "QA_NAV_A", slug = null, menuPermissions = listOf("finance"))))
                else -> error(method)
            } }
            val finance = Proxy.newProxyInstance(FinanceRepository::class.java.classLoader, arrayOf(FinanceRepository::class.java)) { _, method, args ->
                check(method.name == "getFinanceSummary")
                @Suppress("UNCHECKED_CAST")
                val continuation = args.last() as Continuation<MiCatalogoResult<FinanceSummaryDto>>
                pending = continuation
                COROUTINE_SUSPENDED
            } as FinanceRepository
            val viewModel = FinanceViewModel(finance, connection, ShopReadModelEvents())
            advanceUntilIdle()
            assertNotNull(pending)
            viewModel.setMenuCeiling(emptySet())
            pending!!.resume(MiCatalogoResult.Success(FinanceSummaryDto("2026-10-01", "2026-10-14", FinancePeriodDto(netSales = 9999.0), currentState = FinanceCurrentStateDto(aging = FinanceAgingDto()))))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.summary)
            assertTrue(viewModel.uiState.value.access.tabs.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
