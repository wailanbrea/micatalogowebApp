package com.example.bspos.presentation.emulatorqa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.BuildConfig
import com.example.bspos.MainActivity
import com.example.bspos.data.micatalogo.PosSaleSyncWorkerDependencies
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reads back a sale created by the Web UI from the same isolated Laravel/SQLite QA instance. */
@RunWith(AndroidJUnit4::class)
class SharedWebQaReadbackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun webSaleStockIsVisibleAfterAndroidLogin() {
        check(BuildConfig.APPLICATION_ID.contains(".offlinecheck"))
        check(BuildConfig.MICATALOGO_API_BASE_URL == "http://127.0.0.1:18767/")

        val args = InstrumentationRegistry.getArguments()
        val email = requireNotNull(args.getString("qaEmail")) { "qaEmail instrumentation argument is required" }
        val expectedStock = requireNotNull(args.getString("expectedStock")) { "expectedStock instrumentation argument is required" }

        val identity = org.json.JSONObject(
            java.net.URL(BuildConfig.MICATALOGO_API_BASE_URL + "__qa/identity").readText()
        )
        check(identity.getString("environment") == "testing")
        check(identity.getString("driver") == "sqlite")
        check(identity.getString("database") == "C:/Users/waila/AppData/Local/Temp/opencode/micatalogo-sync-20261012.sqlite")

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val connection = EntryPointAccessors.fromApplication(
            instrumentation.targetContext.applicationContext,
            PosSaleSyncWorkerDependencies::class.java
        ).connectionRepository()
        runBlocking { connection.logout() }
        compose.activityRule.scenario.recreate()

        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Iniciar sesión").fetchSemanticsNodes().isNotEmpty()
        }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement(email)
        fields[1].performTextReplacement("Sync-QA-2026!")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Iniciar sesión").performClick()

        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Buenas, QA Tienda A").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Abrir menu").performClick()
        compose.onNodeWithText("Buscar en el menú…").performTextReplacement("Vender")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNode(hasText("Vender") and hasClickAction() and
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected)
        ).performScrollTo().performClick()

        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("QA Producto A").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("En stock ($expectedStock)").assertIsDisplayed()
    }
}
