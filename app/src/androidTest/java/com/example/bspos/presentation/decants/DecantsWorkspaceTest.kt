package com.example.bspos.presentation.decants

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecantsWorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private val opening = DecantOpeningDto("opening", "2026-10-06", 100, 15, costCents = 100000, remainingCostCents = 15000)
    private val size = DecantSizeDto("size", "Perfume 5 ml", 5, 30000, prepared = 5, capacity = 3, emptyVials = 2, vialCostCents = 5000)
    private val group = DecantFragranceDto("source", "Perfume Demo 100ml", volumeMl = 100, sealed = 10, tracked = true, sizes = listOf(size), openings = listOf(opening))
    private val workspace = DecantWorkspaceDto(enabled = true, canManage = true, finance = true, groups = listOf(group), prepared = 5, preparedValueCents = 150000,
        vials = listOf(DecantVialDto("vial", "5ml", 5, empty = 2, nextCostCents = 5000, lots = listOf(DecantVialLotDto(1, "2026-10-06", 2, 2, 5000)))))

    @Test fun preparationLimitsQuantityByPhysicalEmptyVialsAndPreservesTheRemainingPerfume() {
        var result: List<String> = emptyList()
        compose.setContent { BSPOSTheme {
            DecantPrepareSheet(workspace, null, true, {}, {}) { source, bottle, presentation, quantity, price ->
                result = listOf(source.id, bottle?.id.orEmpty(), presentation.id, quantity.toString(), price)
            }
        } }
        compose.onNodeWithText("quedan 15 de 100 ml").performClick()
        compose.onNodeWithText("5 ml").performClick()
        compose.onNodeWithText("Máximo (2)").performScrollTo().performClick()
        compose.onNodeWithText("Máximo limitado por ml y envases. Sobrante: 5 ml.").assertExists()
        capture("style-decant-preparation")
        compose.onNodeWithText("Preparar 2 frasco(s)").assertIsEnabled().performClick()
        assertEquals(listOf("source", "opening", "size", "2", "300.00"), result)
    }

    @Test fun noVialsPreventsPreparationEvenWhenThereIsPerfume() {
        val emptyGroup = group.copy(sizes = listOf(size.copy(emptyVials = 0)))
        compose.setContent { BSPOSTheme { DecantPrepareSheet(workspace.copy(groups = listOf(emptyGroup)), null, true, {}, {}) { _, _, _, _, _ -> } } }
        compose.onNodeWithText("quedan 15 de 100 ml").performClick()
        compose.onNodeWithText("5 ml").performClick()
        compose.onNodeWithText("Preparar 1 frasco(s)").assertIsNotEnabled()
    }

    @Test fun cardsShowPreparedUnitsRatherThanSummingCompetingCapacities() {
        compose.setContent { BSPOSTheme { DecantsContent(workspace, null, null, false, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Perfume Demo 100ml").performScrollTo().assertExists()
        compose.onNodeWithText("1 tamaños · 5 listos").assertExists()
        capture("style-decant-workspace")
    }

    private fun capture(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
