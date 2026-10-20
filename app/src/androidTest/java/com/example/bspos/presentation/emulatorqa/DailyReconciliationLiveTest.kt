package com.example.bspos.presentation.emulatorqa

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.BuildConfig
import com.example.bspos.MainActivity
import com.example.bspos.data.local.tenant.LiveQaFixture
import com.example.bspos.data.micatalogo.PosSaleSyncWorkerDependencies
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DailyReconciliationLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun auditedDailyReconciliationPreservesOriginal() {
        check(BuildConfig.APPLICATION_ID.contains(".offlinecheck") && BuildConfig.MICATALOGO_API_BASE_URL == "http://127.0.0.1:18767/")
        val fixture = checkNotNull(LiveQaFixture.optional("auditedDailyReconciliationPreservesOriginal")) { "Use syncQaLive=true with the isolated fixture" }
        val shop = fixture.getString("shop_a")
        val token = fixture.getString("token")
        fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
            val http = URL(BuildConfig.MICATALOGO_API_BASE_URL + path).openConnection() as HttpURLConnection
            http.requestMethod = method; http.connectTimeout = 15000; http.readTimeout = 30000
            http.setRequestProperty("Authorization", "Bearer $token"); http.setRequestProperty("Accept", "application/json")
            http.setRequestProperty("X-MiCatalogo-Version-Code", BuildConfig.VERSION_CODE.toString())
            try {
                if (body != null) {
                    http.doOutput = true; http.setRequestProperty("Content-Type", "application/json")
                    http.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                check(http.responseCode in 200..299) { "Isolated reconciliation HTTP ${http.responseCode} at $path" }
                return JSONObject(http.inputStream.bufferedReader().use { it.readText() })
            } finally { http.disconnect() }
        }
        val base = "api/v1/shops/$shop"
        val date = LocalDate.now(ZoneId.of(fixture.getString("business_timezone"))).toString()
        val original = request("$base/finance/day-close", "POST", JSONObject().put("date", date).put("counted_cash", "0.00").put("notes", "QA original live snapshot")).getJSONObject("closure")
        val sale = JSONObject().put("client_sale_uuid", UUID.randomUUID().toString()).put("payment_status", "paid")
            .put("credit_amount", "0.00").put("tax", "0.00").put("discount", "0.00")
            .put("payments", JSONArray().put(JSONObject().put("method", "cash").put("amount", "300.00")))
            .put("items", JSONArray().put(JSONObject().put("product_id", fixture.getString("product")).put("quantity", 1).put("unit_price", "300.00")))
        request("$base/pos-sales", "POST", sale)
        request("$base/pos-sales", "POST", sale)
        val before = request("$base/finance/day-close?date=$date")
        assertEquals(300.0, before.getDouble("expected_cash"), 0.0)
        assertEquals(0.0, before.getJSONObject("closure").getDouble("expected_cash"), 0.0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val connection = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext, PosSaleSyncWorkerDependencies::class.java).connectionRepository()
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val autofill = shell("settings get secure autofill_service")
        require(autofill == "null" || Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+").matches(autofill))
        try {
            shell("settings put secure autofill_service null")
            runBlocking { connection.logout() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(45000) { compose.onAllNodesWithText("Iniciar sesión").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(fixture.getString("email"))
            compose.onAllNodes(hasSetTextAction())[1].performTextReplacement(fixture.getString("password"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Iniciar sesión").performClick()
            compose.waitUntil(45000) { compose.onAllNodesWithContentDescription("Abrir menu").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { connection.shops(); connection.selectShop(shop) }
            compose.waitUntil(45000) { runBlocking { connection.activeShopId() } == shop }
            compose.onNodeWithContentDescription("Abrir menu").performClick()
            compose.onNodeWithText("Buscar en el menú…").performTextReplacement("Cierre")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNode(hasText("Cierre de día") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
            compose.waitUntil(45000) { compose.onAllNodesWithText("VENTAS COBRADAS").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("day-close-list").performScrollToNode(hasTestTag("day-close-adjust"))
            compose.onNodeWithTag("day-close-adjust").performClick()
            compose.onNodeWithTag("day-close-adjust-count").performTextReplacement("310.00")
            compose.onNodeWithTag("day-close-adjust-reason").performTextReplacement("QA live adjustment after late sale")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("day-close-adjust-confirm").assertIsEnabled().performClick()
            compose.waitUntil(45000) { compose.onAllNodesWithTag("day-close-adjust-confirm").fetchSemanticsNodes().isEmpty() }
            val after = request("$base/finance/day-close?date=$date")
            assertEquals(original.toString(), after.getJSONObject("closure").toString())
            assertEquals(1, after.getJSONArray("adjustments").length())
            val adjustment = after.getJSONArray("adjustments").getJSONObject(0)
            assertEquals("300.00", adjustment.getString("expected_cash"))
            assertEquals("310.00", adjustment.getString("counted_cash"))
            assertEquals("10.00", adjustment.getString("difference"))
            val replay = request("$base/finance/day-close/${original.getString("id")}/adjustments", "POST", JSONObject()
                .put("client_operation_uuid", adjustment.getString("client_operation_uuid")).put("counted_cash", "310.00").put("reason", adjustment.getString("reason")))
            assertEquals(adjustment.getString("id"), replay.getJSONObject("adjustment").getString("id"))
            assertEquals(1, replay.getJSONArray("adjustments").length())
            val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("reconciliation-qa"))
            File(folder, "daily-reconciliation-live.json").writeText(JSONObject().put("shop", shop).put("email", fixture.getString("email"))
                .put("before", before).put("after", after).put("replay_adjustment_id", replay.getJSONObject("adjustment").getString("id")).toString(2))
            compose.onNodeWithTag("day-close-list").performScrollToNode(hasText(adjustment.getString("reason")))
            File(folder, "daily-reconciliation-live.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { shell("settings put secure autofill_service $autofill") }
    }
}
