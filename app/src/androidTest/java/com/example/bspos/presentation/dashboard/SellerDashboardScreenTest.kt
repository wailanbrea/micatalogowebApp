package com.example.bspos.presentation.dashboard

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.SellerSummaryDayDto
import com.example.bspos.data.micatalogo.dto.SellerSummaryMetricsDto
import com.example.bspos.data.micatalogo.dto.SellerSummaryDto
import com.example.bspos.data.micatalogo.dto.SellerSummarySaleDto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SellerDashboardScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sellerDashboardUsesAdminSummaryPresentationAndKeepsSellerOnlyMetrics() {
        val summary = SellerSummaryDto(
            period = "today",
            periodLabel = "Hoy",
            metrics = SellerSummaryMetricsDto(count = 2, total = 125000, commission = 12500, average = 62500),
            chart = listOf(SellerSummaryDayDto("Lun", 125000)),
            sales = listOf(SellerSummarySaleDto("FAC-001", "Cliente QA", "Hoy", 125000, 12500, "paid"))
        )

        compose.setContent {
            BSPOSTheme {
                SellerDashboardScreen(
                    shopId = "qa-shop",
                    businessName = "Tienda QA",
                    showSales = true,
                    showProducts = true,
                    showCustomers = true,
                    onNewSale = {},
                    onProducts = {},
                    onCustomers = {},
                    onAllSales = {},
                    onSupport = {},
                    onHideSupport = {},
                    showSupport = false,
                    viewModel = null,
                    previewSummary = summary
                )
            }
        }

        compose.onNodeWithTag("seller-dashboard").assertExists()
        compose.onNodeWithText("RESUMEN").assertExists()
        compose.onNodeWithText("Buenas, Tienda QA").assertExists()
        compose.onNodeWithText("TUS VENTAS").assertExists()
        compose.onNodeWithText("TU COMISIÓN").assertExists()
        compose.onNodeWithText("FAC-001").assertExists()
        compose.onNodeWithText("Capital al costo").assertDoesNotExist()
        compose.onNodeWithText("Ganancia del negocio").assertDoesNotExist()
    }
}
