package com.example.bspos.presentation.pos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.usecase.PosPaymentSplitInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SplitPaymentDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun creditExpansionAndConfirmationPreserveTheFormOnError() {
        var confirmed = emptyList<PosPaymentSplitInput>()
        val error = mutableStateOf<String?>(null)
        compose.setContent {
            BSPOSTheme {
                SplitPaymentDialog(950000, "Cliente de prueba", {}, { payments, _ ->
                    confirmed = payments
                    error.value = "No se pudo registrar el crédito de prueba."
                }, creditAvailable = 1000000, errorMessage = error.value)
            }
        }
        compose.onNodeWithTag("split-cash").performTextReplacement("2000")
        compose.onNodeWithText("Saldo restante a crédito").performScrollTo().performClick()
        compose.onNodeWithText("Saldo pendiente: RD$ 7,500.00").assertExists()
        compose.onNodeWithText("Fecha de vencimiento (AAAA-MM-DD)").assertExists()
        compose.onNodeWithText("Confirmar Cobro").assertIsEnabled().performClick()
        assertEquals(listOf("cash", "credit"), confirmed.map { it.method })
        assertEquals(750000L, confirmed.last().amountCents)
        compose.onNodeWithText("No se pudo registrar el crédito de prueba.").assertExists()
        compose.onNodeWithTag("split-cash").assertTextContains("2000")
        compose.onNodeWithText("Saldo pendiente: RD$ 7,500.00").assertExists()
    }

    @Test fun aCreditPaymentWithoutCustomerCannotBeSubmitted() {
        compose.setContent { BSPOSTheme { SplitPaymentDialog(950000, null, {}, { _, _ -> }) } }
        compose.onNodeWithText("Saldo restante a crédito").performScrollTo().performClick()
        compose.onNodeWithText("Selecciona un cliente para la parte a crédito.").assertExists()
        compose.onNodeWithText("Confirmar Cobro").assertIsNotEnabled()
    }
}
