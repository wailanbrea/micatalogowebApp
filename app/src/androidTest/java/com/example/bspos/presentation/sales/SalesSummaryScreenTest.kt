package com.example.bspos.presentation.sales

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SalePaymentType
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesSummaryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summaryRendersCountSalesAverageAndOutstandingPercentageInGrid() {
        val now = Instant.parse("2026-10-07T12:00:00Z")
        val sales = listOf(
            Sale(UUID.randomUUID(), "V-1", date = now, subtotal = 10000, total = 10000, paymentType = SalePaymentType.CASH, paidAmount = 10000, createdAt = now, updatedAt = now),
            Sale(UUID.randomUUID(), "V-2", date = now, subtotal = 10000, total = 10000, paymentType = SalePaymentType.CREDIT, paidAmount = 5000, pendingAmount = 5000, createdAt = now, updatedAt = now)
        )
        compose.setContent {
            BSPOSTheme {
                CompositionLocalProvider(LocalCurrency provides CurrencyUnit.DOP) { SalesSummary(sales) }
            }
        }
        compose.onNodeWithText("VENTAS DEL PERÍODO").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("TOTAL VENDIDO").assertIsDisplayed()
        compose.onNodeWithText("RD$ 200.00").assertIsDisplayed()
        compose.onNodeWithText("PROMEDIO POR VENTA").assertIsDisplayed()
        compose.onNodeWithText("RD$ 100.00").assertIsDisplayed()
        compose.onNodeWithText("A CRÉDITO").assertIsDisplayed()
        compose.onNodeWithText("25%").assertIsDisplayed()
    }
}
