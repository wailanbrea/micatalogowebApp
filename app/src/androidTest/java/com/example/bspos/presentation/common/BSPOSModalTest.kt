package com.example.bspos.presentation.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class BSPOSModalTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sharedAlertKeepsContentAndActionsFunctional() {
        var confirmed = false
        compose.setContent {
            BSPOSTheme {
                BSPOSAlertDialog(
                    onDismissRequest = {},
                    title = { Text("Título modal") },
                    text = { Text("Contenido modal") },
                    confirmButton = {
                        TextButton(onClick = { confirmed = true }) { Text("Confirmar") }
                    },
                    dismissButton = { TextButton(onClick = {}) { Text("Cancelar") } }
                )
            }
        }

        compose.onNodeWithText("Título modal").assertIsDisplayed()
        compose.onNodeWithText("Contenido modal").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        compose.onNodeWithText("Confirmar").performClick()

        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun sharedSheetDisplaysItsContent() {
        compose.setContent {
            BSPOSTheme {
                BSPOSModalBottomSheet(onDismissRequest = {}) {
                    Text("Contenido de hoja")
                }
            }
        }

        compose.onNodeWithText("Contenido de hoja").assertIsDisplayed()
    }
}
