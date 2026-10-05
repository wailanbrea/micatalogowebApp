package com.example.bspos.presentation.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.bspos.domain.model.MiCatalogoConnectionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rememberedBiometricSessionShowsFingerprintBesidePasswordEye() {
        var fingerprintClicks = 0

        composeRule.setContent {
            LoginCard(
                connection = MiCatalogoConnectionState(
                    baseUrl = "https://example.test/",
                    hasAccessToken = true,
                    isRemembered = true
                ),
                state = LoginUiState(),
                email = "seller@example.test",
                password = "secret",
                rememberMe = true,
                biometricAvailable = true,
                biometricMessage = null,
                onEmailChange = {},
                onPasswordChange = {},
                onRememberChange = {},
                onLogin = {},
                onFingerprint = { fingerprintClicks++ }
            )
        }

        composeRule.onNodeWithContentDescription("Desbloquear con huella").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mostrar contrasena").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Desbloquear con huella").performClick()
        assertEquals(1, fingerprintClicks)
    }
}
