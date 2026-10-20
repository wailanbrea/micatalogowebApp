package com.example.bspos.presentation.practice

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.bspos.core.ui.theme.BSPOSTheme
import org.junit.Rule
import org.junit.Test

class PracticeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun trainingCanSellCollectReceiveAndRestartWithoutAnAccountOrRepository() {
        compose.setContent { BSPOSTheme { PracticeScreen() } }
        compose.onNodeWithText("Existencia de práctica: 10").assertExists()
        compose.onNodeWithText("Simular venta a crédito").performScrollTo().performClick()
        compose.onNodeWithText("Existencia de práctica: 9").performScrollTo().assertExists()
        compose.onNodeWithText("Simular cobro del saldo").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Simular cobro del saldo").assertIsNotEnabled()
        compose.onNodeWithText("Recibir 5 unidades de práctica").performScrollTo().performClick()
        compose.onNodeWithText("Existencia de práctica: 14").performScrollTo().assertExists()
        compose.onNodeWithText("Reiniciar práctica").performScrollTo().performClick()
        compose.onNodeWithText("Existencia de práctica: 10").performScrollTo().assertExists()
        compose.onNodeWithText("Simular cobro del saldo").performScrollTo().assertIsNotEnabled()
    }
}
