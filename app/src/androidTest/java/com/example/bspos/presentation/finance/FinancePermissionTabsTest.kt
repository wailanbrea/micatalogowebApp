package com.example.bspos.presentation.finance

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.bspos.core.ui.theme.BSPOSTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FinancePermissionTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cashOnlyDoesNotComposeFinancialOrExpenseTabs() {
        compose.setContent { BSPOSTheme { FinancePermissionTabs(FinanceAccess(cash = true), 0) {} } }
        compose.onNodeWithText("Caja en línea").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Resumen y KPIs").assertDoesNotExist()
        compose.onNodeWithText("Estado de Resultados").assertDoesNotExist()
        compose.onNodeWithText("Flujo de Efectivo").assertDoesNotExist()
        compose.onNodeWithText("Gastos").assertDoesNotExist()
    }

    @Test fun revocationReindexesTabsAndRemovesForbiddenContent() {
        val access = mutableStateOf(FinanceAccess(finance = true, cash = true))
        var selected = -1
        compose.setContent { BSPOSTheme { FinancePermissionTabs(access.value, 4) { selected = it } } }
        compose.onNodeWithText("Caja en línea").assertIsSelected()
        compose.runOnIdle { access.value = FinanceAccess(expenses = true) }
        compose.onNodeWithText("Caja en línea").assertDoesNotExist()
        compose.onNodeWithText("Resumen y KPIs").assertDoesNotExist()
        compose.onNodeWithText("Gastos").assertIsDisplayed().assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(3, selected) }
        compose.runOnIdle { access.value = FinanceAccess() }
        compose.onNodeWithText("Gastos").assertDoesNotExist()
        compose.onNodeWithText("No tienes acceso a estas operaciones.").assertIsDisplayed()
    }
}
