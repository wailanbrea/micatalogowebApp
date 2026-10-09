package com.example.bspos.presentation.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FinanceAgingDto
import com.example.bspos.data.micatalogo.dto.FinanceCurrentStateDto
import com.example.bspos.data.micatalogo.dto.FinancePeriodDto
import com.example.bspos.data.micatalogo.dto.FinanceSummaryDto
import org.junit.Rule
import org.junit.Test

class FinanceContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun missingSummaryShowsUnavailableStateInsteadOfFakeZero() {
        compose.setContent {
            BSPOSTheme {
                FinanceSummaryTab(summary = null, onNavigateToCash = null)
            }
        }

        compose.onNodeWithText("No hay datos disponibles para el período.").assertIsDisplayed()
        compose.onNodeWithText("RD$ 0.00").assertDoesNotExist()
    }

    @Test
    fun missingCashFlowShowsUnavailableStateInsteadOfFakeZero() {
        compose.setContent {
            BSPOSTheme {
                CashFlowTab(cashFlow = null)
            }
        }

        compose.onNodeWithText("No hay datos de flujo de efectivo disponibles.").assertIsDisplayed()
        compose.onNodeWithText("RD$ 0.00").assertDoesNotExist()
    }

    @Test
    fun periodKpiOpensFullExplanationDialog() {
        compose.setContent {
            BSPOSTheme {
                FinanceSummaryTab(
                    summary = FinanceSummaryDto(
                        from = "2026-10-01",
                        to = "2026-10-09",
                        period = FinancePeriodDto(netSales = 1234.0, salesCount = 4, unitsSold = 7),
                        currentState = FinanceCurrentStateDto(aging = FinanceAgingDto())
                    ),
                    onNavigateToCash = null
                )
            }
        }

        compose.onNodeWithText("Ventas netas").performClick()
        compose.onNodeWithText(
            "Total de las ventas del período después de descuentos y devoluciones, antes de restar el costo de la mercancía y los gastos operativos. Incluye 4 ventas y 7 unidades."
        ).assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText(
            "Total de las ventas del período después de descuentos y devoluciones, antes de restar el costo de la mercancía y los gastos operativos. Incluye 4 ventas y 7 unidades."
        ).assertDoesNotExist()
    }
}
