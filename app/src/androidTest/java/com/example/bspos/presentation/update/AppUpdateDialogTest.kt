package com.example.bspos.presentation.update

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requiredUpdateDoesNotOfferSkip() {
        composeRule.setContent {
            BSPOSTheme {
                AppUpdateDialog(
                    state = AppUpdateState.Available(update(isRequired = true)),
                    onDownload = {},
                    onRetryCheck = {},
                    onDismissCheckFailure = {}
                )
            }
        }

        composeRule.onNodeWithText("Actualizacion requerida").assertIsDisplayed()
        composeRule.onNodeWithText("Actualizar").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Mas tarde").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun optionalUpdateIsAlsoBlocking() {
        composeRule.setContent {
            BSPOSTheme {
                AppUpdateDialog(
                    state = AppUpdateState.Available(update(isRequired = false)),
                    onDownload = {},
                    onRetryCheck = {},
                    onDismissCheckFailure = {}
                )
            }
        }

        composeRule.onNodeWithText("Actualizacion disponible").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Mas tarde").fetchSemanticsNodes().isEmpty())
    }

    private fun update(isRequired: Boolean) = AvailableAppUpdate(
        versionCode = 7,
        versionName = "1.0.6",
        apkUrl = "https://micatalogo.bsolutions.dev/downloads/bspos-1.0.6.apk",
        apkSha256 = "a".repeat(64),
        releaseNotes = "Actualizacion de seguridad.",
        isRequired = isRequired
    )
}
