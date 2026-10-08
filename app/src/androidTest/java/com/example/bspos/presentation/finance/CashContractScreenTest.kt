package com.example.bspos.presentation.finance

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.CashCurrentSessionResponseDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test

class CashContractScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun realExpectedBalanceIsDisplayedAndNoOpenSessionCanBeOpened() {
        val source = InstrumentationRegistry.getInstrumentation().context.assets.open("cash-current-session.json")
            .bufferedReader().use { it.readText() }
        val cash = mutableStateOf<CashCurrentSessionResponseDto?>(Json { ignoreUnknownKeys = true }.decodeFromString(source))
        compose.setContent { BSPOSTheme { RemoteCashTab(cash.value, false, true, { _, _ -> }, { _, _ -> }, { _, _, _ -> }) } }
        compose.onNodeWithText("Esperado en caja").assertExists()
        compose.onNodeWithText("RD$ 15,000.00").assertExists()
        compose.runOnIdle { cash.value = null }
        compose.onNodeWithText("No hay una sesión de caja abierta").assertIsDisplayed()
        compose.onNodeWithText("Abrir caja").assertIsDisplayed()
    }

    @Test fun readOnlyRoleDoesNotSeeCashMutationControls() {
        compose.setContent {
            BSPOSTheme {
                RemoteCashTab(null, false, false, { _, _ -> }, { _, _ -> }, { _, _, _ -> })
            }
        }

        compose.onNodeWithText("Consulta de caja en solo lectura. Tu rol no puede abrir, cerrar ni registrar movimientos.").assertIsDisplayed()
        compose.onNodeWithText("Abrir caja").assertDoesNotExist()
        compose.onNodeWithText("Guardar movimiento").assertDoesNotExist()
        compose.onNodeWithText("Cerrar caja").assertDoesNotExist()
    }
}
