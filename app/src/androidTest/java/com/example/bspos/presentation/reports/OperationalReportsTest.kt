package com.example.bspos.presentation.reports

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class OperationalReportsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val metadata = OperationalReportContextDto("Tienda QA", "2026-10-01", "2026-10-19", "America/Santo_Domingo", "2026-10-20T02:00:00Z")
    private fun capture(name: String) {
        File(context.getExternalFilesDir(null), name).outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun dashboardStyleSectionsSearchAndPeriodActions() {
        var period = ""
        val module = FeatureModuleDto(reportContext = metadata, sections = listOf(
            FeatureSectionDto("sales", "Ventas", listOf(FeatureKpiDto("Ventas netas", "RD$ 1,500.00")),
                listOf(FeatureRowDto(primary = "Perfume Ámbar", secondary = "3 unidades", value = "RD$ 500.00"), FeatureRowDto(primary = "Jabón", value = "RD$ 100.00")), "Explicación contable extensa", "2026-10-01 - 2026-10-19"),
            FeatureSectionDto("services", "Servicios", rows = emptyList(), scope = "Catálogo actual")))
        compose.setContent { BSPOSTheme { Scaffold { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) { OperationalReportsScreen(module, "month", { period = it }, {}, {}) } } } }
        compose.onNodeWithText("Tienda QA").assertIsDisplayed()
        compose.onNodeWithText("WhatsApp").assertIsDisplayed()
        compose.onNodeWithText("Imprimir").assertIsDisplayed()
        capture("reports-dashboard.png")
        compose.onNodeWithText("Hoy").performClick()
        compose.runOnIdle { assertEquals("today", period) }
        compose.onNodeWithTag("operational_reports").performScrollToNode(hasText("Buscar en el detalle"))
        compose.onNodeWithText("Buscar en el detalle").performTextInput("Ámbar")
        compose.onNodeWithText("1 registro").assertExists()
        compose.onNodeWithTag("operational_reports").performScrollToNode(hasText("Perfume Ámbar"))
        compose.onNodeWithText("Perfume Ámbar").assertIsDisplayed()
        compose.onNodeWithText("Jabón").assertDoesNotExist()
        compose.onNodeWithText("Explicación contable extensa").assertDoesNotExist()
        capture("reports-filter.png")
        compose.onNodeWithTag("operational_reports").performScrollToNode(hasText("Servicios"))
        compose.onNodeWithText("Servicios").performClick()
        compose.onNodeWithTag("operational_reports").performScrollToNode(hasText("Sin registros"))
        compose.onNodeWithText("Sin registros").assertIsDisplayed()
    }

    @Test fun liveReadModelRendersAndSharesPdfThroughAndroidChooser() {
        val fixture = checkNotNull(com.example.bspos.data.local.tenant.LiveQaFixture.optional("operationalReportsReadModel"))
        val url = java.net.URL(com.example.bspos.BuildConfig.MICATALOGO_API_BASE_URL + "api/v1/shops/${fixture.getString("shop_a")}/features/reports?period=today")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer ${fixture.getString("token")}")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-MiCatalogo-Version-Code", com.example.bspos.BuildConfig.VERSION_CODE.toString())
        connection.connectTimeout = 15000; connection.readTimeout = 30000
        val module = try {
            assertEquals(200, connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<FeatureResponseDto>(body).module
        } finally { connection.disconnect() }
        assertNotNull(module.reportContext)
        assertTrue(module.sections.isNotEmpty())
        compose.setContent { BSPOSTheme { Scaffold { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) { OperationalReportsScreen(module, "today", {}, {}, {}) } } } }
        compose.onNodeWithText(module.reportContext!!.shopName).assertIsDisplayed()
        capture("reports-live.png")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var shared: android.content.Intent? = null
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.action != android.content.Intent.ACTION_CHOOSER) return null
                shared = intent.getParcelableExtra(android.content.Intent.EXTRA_INTENT)
                return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithTag("operational_reports").performScrollToNode(hasText("Compartir PDF"))
            compose.onNodeWithText("Compartir PDF").performClick()
            compose.waitUntil(15000) { shared != null }
            assertEquals("application/pdf", shared!!.type)
            val uri = shared!!.clipData!!.getItemAt(0).uri
            assertTrue(context.contentResolver.openInputStream(uri)!!.use { it.readBytes().size } > 500)
        } finally { instrumentation.removeMonitor(monitor) }
        compose.onNodeWithText("Imprimir").performClick()
        compose.waitUntil(15000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("printspooler") == true }
        capture("reports-print-dialog.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
    }

    @Test fun multiPagePdfPreservesLongDetailAndPrintingRespectsPageSelectionAndCancellation() {
        val rows = (1..90).map { FeatureRowDto(primary = "Producto $it - Ámbar y café para una descripción que debe conservarse completa", secondary = "Detalle con acentos, cantidades y una referencia " + "muy extensa ".repeat(12), value = "RD$ 1,234.56", status = if (it == 90) "ÚLTIMO REGISTRO CONSERVADO" else "Calculada") }
        val section = FeatureSectionDto("sales", "Ventas", listOf(FeatureKpiDto("Ventas netas", "RD$ 111,110.40")), rows, "Valores reales del periodo", "2026-10-01 - 2026-10-19")
        val file = OperationalReportPdf.create(context, OperationalReportSnapshot("Reportes operativos", metadata, section))
        val sendIntent = reportPdfSendIntent(context, file, "Reporte QA")
        assertEquals("application/pdf", sendIntent.type)
        assertEquals(android.content.Intent.ACTION_SEND, sendIntent.action)
        assertTrue(sendIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val sharedUri = sendIntent.clipData!!.getItemAt(0).uri
        assertEquals("content", sharedUri.scheme)
        assertEquals(file.length(), context.contentResolver.openInputStream(sharedUri)!!.use { it.readBytes().size.toLong() })
        file.copyTo(File(context.getExternalFilesDir(null), "qa-report-multipage.pdf"), overwrite = true)
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount > 5)
            for (index in listOf(0, renderer.pageCount - 1)) {
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(context.getExternalFilesDir(null), "report-page-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
        val selected = File(context.cacheDir, "report-print-selected.pdf")
        ParcelFileDescriptor.open(selected, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE).use { destination ->
            val result = OperationalReportPrintAdapter(context, file).writeSelectedPages(arrayOf(PageRange(1, 2)), destination, CancellationSignal())
            assertEquals(2, result.size)
        }
        PdfRenderer(ParcelFileDescriptor.open(selected, ParcelFileDescriptor.MODE_READ_ONLY)).use { assertEquals(2, it.pageCount) }
        val cancelled = CancellationSignal().apply { cancel() }
        ParcelFileDescriptor.open(File(context.cacheDir, "cancelled-print.pdf"), ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE).use { destination ->
            try { OperationalReportPrintAdapter(context, file).writeSelectedPages(arrayOf(PageRange.ALL_PAGES), destination, cancelled); fail("Expected cancellation") }
            catch (_: android.os.OperationCanceledException) { }
        }
    }
}
