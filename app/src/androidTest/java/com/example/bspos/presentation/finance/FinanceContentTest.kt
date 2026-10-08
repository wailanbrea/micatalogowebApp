package com.example.bspos.presentation.finance

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.bspos.core.ui.theme.BSPOSTheme
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
}
