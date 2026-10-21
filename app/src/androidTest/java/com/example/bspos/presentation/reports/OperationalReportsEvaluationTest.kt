package com.example.bspos.presentation.reports

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.*
import android.print.*
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Regressions for accessible layouts and user-selected print paper. */
class OperationalReportsEvaluationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = compose.activity
    private val section = FeatureSectionDto("sales", "Ventas",
        listOf(FeatureKpiDto("Ventas netas", "RD$ 999,999,999.99"), FeatureKpiDto("Ganancia bruta", "RD$ 123,456,789.99")),
        listOf(FeatureRowDto(primary = "Producto con nombre largo para comprobar distribución", value = "RD$ 1,000.00")), scope = "2026-10-01 - 2026-10-20")

    @Test fun narrowScreenWithLargeTextKeepsActionsReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                BSPOSTheme { Scaffold { padding -> Box(Modifier.padding(padding).width(320.dp)) {
                    OperationalReportsScreen(FeatureModuleDto(sections = listOf(section)), "month", {}, {}, {})
                } } }
            }
        }
        compose.onNodeWithTag("operational_reports").performScrollToNode(hasText("RD$ 999,999,999.99"))
        val amountLayout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("RD$ 999,999,999.99", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(amountLayout) }
        File(context.getExternalFilesDir(null), "reports-large-font-metrics-fixed.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertEquals(1, amountLayout.single().lineCount)
        // horizontalScroll measures the paragraph with unbounded width. Check
        // actual glyph bounds against the visible node instead of paragraph width.
        val lastGlyph = amountLayout.single().getBoundingBox("RD$ 999,999,999.99".lastIndex)
        val visibleWidth = compose.onNodeWithText("RD$ 999,999,999.99", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        assertTrue("The complete amount must fit", lastGlyph.right <= visibleWidth + 1f)
        compose.onNodeWithTag("operational_reports").performScrollToNode(hasText("WhatsApp"))
        compose.onNodeWithText("WhatsApp").assertIsDisplayed()
        compose.onNodeWithText("Imprimir").assertIsDisplayed()
        for (label in listOf("WhatsApp", "Imprimir", "Compartir PDF", "Exportar")) {
            val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals("Action must not split: $label", 1, layout.single().lineCount)
        }
        File(context.getExternalFilesDir(null), "reports-narrow-large-font.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("operational_reports").performScrollToNode(hasText("Buscar en el detalle"))
        compose.onNodeWithText("Buscar en el detalle").assertIsDisplayed()
    }

    @Test fun letterPaperSelectionMustBeReflectedInPrintedPdf() {
        compose.setContent { BSPOSTheme { } }
        val file = OperationalReportPdf.create(context, OperationalReportSnapshot("Reportes operativos", null, section))
        val adapter = OperationalReportPrintAdapter(context, file)
        var observed: PrintAttributes? = null
        var job: PrintJob? = null
        val wrapper = object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, current: PrintAttributes, signal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                observed = current
                adapter.onLayout(old, current, signal, callback, extras)
            }
            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, signal: CancellationSignal, callback: WriteResultCallback) {
                adapter.onWrite(pages, destination, signal, callback)
            }
        }
        try {
            compose.runOnIdle {
                val manager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                job = manager.print("QA Letter paper", wrapper, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                    .setMinMargins(PrintAttributes.Margins(500, 500, 500, 500)).build())
            }
            compose.waitUntil(15000) { observed?.mediaSize != null }
            assertEquals(PrintAttributes.MediaSize.NA_LETTER.id, observed!!.mediaSize!!.id)
            val output = File(context.getExternalFilesDir(null), "report-letter-selection.pdf")
            ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE).use {
                adapter.writeSelectedPages(arrayOf(PageRange.ALL_PAGES), it, CancellationSignal())
            }
            PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                renderer.openPage(0).use { page ->
                    println("PAPER_EVALUATION selected=Letter expected=612x792 actual=${page.width}x${page.height}")
                    assertEquals("Letter width must be honored", 612, page.width)
                    assertEquals("Letter height must be honored", 792, page.height)
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { job?.cancel() }
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
        }
    }
}
