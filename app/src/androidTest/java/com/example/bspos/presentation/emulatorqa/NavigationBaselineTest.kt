package com.example.bspos.presentation.emulatorqa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.BuildConfig
import com.example.bspos.MainActivity
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.micatalogo.PosSaleSyncWorkerDependencies
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** F0 screenshots use real API login and preserve application data and pending outboxes. */
class NavigationBaselineTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun captureExistingNavigationForEachRoleAndBusiness() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("navigationQa") == "true")
        check(BuildConfig.APPLICATION_ID.contains(".offlinecheck"))
        check(BuildConfig.MICATALOGO_API_BASE_URL == "http://127.0.0.1:18768/")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val formFactor = InstrumentationRegistry.getArguments().getString("navigationFormFactor") ?: "phone"
        require(formFactor in setOf("phone", "tablet"))
        val evidenceStage = InstrumentationRegistry.getArguments().getString("navigationEvidenceStage") ?: "before"
        require(evidenceStage in setOf("before", "f3"))
        val identity = java.net.URL(BuildConfig.MICATALOGO_API_BASE_URL + "__qa/navigation/identity").readText()
        check(org.json.JSONObject(identity).getString("database") == "C:/Users/waila/AppData/Local/Temp/opencode/micatalogo-navigation-20261014.sqlite")
        val connection = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext,
            PosSaleSyncWorkerDependencies::class.java).connectionRepository()
        val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("navigation-qa"))
        fun snapshot(stage: String) {
            val counts = org.json.JSONObject()
            val path = instrumentation.targetContext.getDatabasePath(AppDatabase.NAME)
            check(path.isFile) { "Room baseline requires an existing QA database" }
            android.database.sqlite.SQLiteDatabase.openDatabase(path.absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null).use { tables ->
                    while (tables.moveToNext()) {
                        val name = tables.getString(0)
                        require(Regex("[A-Za-z0-9_]+").matches(name))
                        database.rawQuery("SELECT COUNT(*) FROM \"$name\"", null).use { rows ->
                            check(rows.moveToFirst())
                            counts.put(name, rows.getLong(0))
                        }
                    }
                }
                File(folder, "${if (evidenceStage == "before") "baseline" else evidenceStage}-room-$formFactor-$stage.json").writeText(org.json.JSONObject()
                    .put("package", BuildConfig.APPLICATION_ID).put("schema", database.version)
                    .put("tables", counts).toString(2))
            }
        }
        snapshot("before")
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val autofill = shell("settings get secure autofill_service")
        require(autofill == "null" || Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+").matches(autofill))
        shell("settings put secure autofill_service null")
        try {
            for (role in listOf("owner", "manager", "sales", "accountant", "barber", "grocery", "cash", "expenses", "services", "reports")) {
                runBlocking { connection.logout() }
                compose.activityRule.scenario.recreate()
                compose.waitUntil(45000) { compose.onAllNodesWithText("Iniciar sesión").fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("qa-nav-$role@example.test")
                compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("QA-NAV-2026!")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                compose.onNodeWithText("Iniciar sesión").performClick()
                compose.waitUntil(45000) { compose.onAllNodes(hasContentDescription("Abrir menu") or hasContentDescription("Abrir menú")).fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodes(hasContentDescription("Abrir menu") or hasContentDescription("Abrir menú"))[0].performClick()
                compose.waitForIdle()
                File(folder, "$evidenceStage-android-$formFactor-$role.png").outputStream().use { stream ->
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                }
                File(folder, "$evidenceStage-android-$formFactor-$role.txt").writeText(
                    compose.onAllNodes(isRoot())[0].printToString()
                )
                compose.onAllNodes(isRoot())[0].printToLog("QA_NAV_BASELINE_$role")
                println("QA_NAV_BASELINE_CAPTURE: $role")
            }
        } finally {
            shell("settings put secure autofill_service $autofill")
            snapshot("after")
        }
    }
}
