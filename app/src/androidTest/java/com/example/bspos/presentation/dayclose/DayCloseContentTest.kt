package com.example.bspos.presentation.dayclose

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.presentation.finance.FinanceUiState
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayCloseContentTest {
    @get:Rule val compose = createComposeRule()
    private val date = "2026-10-07"
    private var saved = false
    private fun show(closed: Boolean = false, stale: Boolean = false) {
        val record = if (closed) DailyClosureRecordDto("closure", date, expectedCash = 9500.0,
            closedAt = "2026-10-08T03:13:35Z") else null
        val state = FinanceUiState(fromDate = date, toDate = date,
            dailyClose = DailyCloseDto(if (stale) "2026-10-08" else date, salesCash = 9500.0, expectedCash = 9500.0, closure = record),
            summary = FinanceSummaryDto(date, date, FinancePeriodDto(), currentState = FinanceCurrentStateDto(aging = FinanceAgingDto())))
        compose.setContent {
            BSPOSTheme { Scaffold { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    DayCloseContent(state, {}, { _, _ -> saved = true }, {})
                }
            } }
        }
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun showsReferenceCardsWithRealValuesAndReachableSave() {
        show()
        compose.onNodeWithText("VENTAS COBRADAS").assertIsDisplayed()
        compose.onNodeWithText("07/10/2026").assertIsDisplayed()
        capture("day-close-top")
        compose.onNodeWithTag("day-close-list").performScrollToNode(hasText("EFECTIVO EN CAJA"))
        compose.onNodeWithText("Deberías tener").assertIsDisplayed()
        capture("day-close-cash")
        compose.onNodeWithTag("day-close-list").performScrollToNode(hasTestTag("day-close-save"))
        compose.onNodeWithTag("day-close-save").performClick()
        compose.onNodeWithTag("day-close-counted").performTextInput("1..2")
        compose.onNodeWithText("Guardar", substring = false).performClick()
        compose.onNodeWithText("Indica un monto válido con hasta dos decimales.").assertExists()
        assertFalse(saved)
    }
    @Test fun closedDayHasRealSnapshotAndNoFakeReopenAction() {
        show(closed = true)
        compose.onNodeWithTag("day-close-list").performScrollToNode(hasText("Sin arqueo"))
        compose.onNodeWithText("Sin arqueo").assertIsDisplayed()
        compose.onNodeWithTag("day-close-save").assertDoesNotExist()
        compose.onNodeWithText("Reabrir día").assertDoesNotExist()
        capture("day-close-closed")
    }
    @Test fun neverShowsWrongDateDataAsCurrentDay() {
        show(stale = true)
        compose.onNodeWithText("VENTAS COBRADAS").assertDoesNotExist()
        compose.onNodeWithTag("day-close-save").assertDoesNotExist()
    }
}
