package com.example.bspos.presentation.dashboard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SalePaymentType
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResumenOverviewTest {
    @get:Rule val compose = createComposeRule()

    private fun show(sales: List<Sale> = emptyList(), showEncargos: Boolean = false) {
        compose.setContent {
            BSPOSTheme {
                ResumenOverview(
                    businessName = "Mi negocio", sales = sales, saleItems = emptyList(), costTotals = emptyMap(),
                    customers = emptyList(), products = emptyList(), quantities = emptyMap(), payments = emptyList(),
                    pendingOrders = emptyList(), ordersError = null,
                    showSales = true, showCollections = true, showInventory = true, showEncargos = showEncargos, showCost = true,
                    onNewSale = {}, onCollections = {}, onInventory = {}, onStockFilter = {}, onMovements = {},
                    onProducts = {}, onPhotos = {}, onEncargos = {}, onDayClose = {},
                    onStorefront = {}, onProfile = {}, onSupport = {}, onAllSales = {}, onSale = {}
                )
            }
        }
    }

    @Test fun emptyOverviewKeepsEveryOperationalSectionVisible() {
        show(showEncargos = true)
        compose.onAllNodesWithText("Ocultar")[0].performClick()
        compose.waitForIdle()
        listOf("INGRESOS", "Ventas recientes", "Top productos", "Encargos", "Por cobrar", "Dinero por método", "Inventario bajo").forEach {
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(it))
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Todo bien surtido.").assertIsDisplayed()
    }

    @Test fun switchingPeriodIncludesYesterdayAndUsesActualSaleCount() {
        val at = Instant.now()
        show(listOf(Sale(UUID.randomUUID(), "V-PERIOD", date = at, subtotal = 150000, total = 150000, paymentType = SalePaymentType.CASH, paidAmount = 150000, createdAt = at, updatedAt = at)))
        compose.onAllNodesWithText("Ocultar")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Últimos 7").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("VENTAS").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("RD$ 1,500.00").fetchSemanticsNodes().isNotEmpty())
    }
}
