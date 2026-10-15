package com.example.bspos.presentation.emulatorqa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.BuildConfig
import com.example.bspos.MainActivity
import com.example.bspos.data.micatalogo.PosSaleSyncWorkerDependencies
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** Real login + shell + financial destination. Never performs a commercial mutation. */
class NavigationRoleAccessTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun cashAndExpensesOnlyCannotComposeSiblingFinancialData() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("navigationQa") == "true")
        check(BuildConfig.APPLICATION_ID.contains(".offlinecheck"))
        check(BuildConfig.MICATALOGO_API_BASE_URL == "http://127.0.0.1:18768/")
        val identity = org.json.JSONObject(java.net.URL(BuildConfig.MICATALOGO_API_BASE_URL + "__qa/navigation/identity").readText())
        check(identity.getString("environment") == "testing" && identity.getString("driver") == "sqlite")
        check(identity.getString("database") == "C:/Users/waila/AppData/Local/Temp/opencode/micatalogo-navigation-20261014.sqlite")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val connection = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext,
            PosSaleSyncWorkerDependencies::class.java).connectionRepository()
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }
        val autofill = shell("settings get secure autofill_service")
        require(autofill == "null" || Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+").matches(autofill))
        shell("settings put secure autofill_service null")
        try {
            for ((role, destination, tab, forbidden) in listOf(
                listOf("cash", "Caja", "Caja en línea", "Gastos"),
                listOf("expenses", "Gastos", "Gastos", "Caja en línea")
            )) {
                runBlocking { connection.logout() }
                compose.activityRule.scenario.recreate()
                compose.waitUntil(45000) { compose.onAllNodesWithText("Iniciar sesion").fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("qa-nav-$role@example.test")
                compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("QA-NAV-2026!")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                compose.onNodeWithText("Iniciar sesion").performClick()
                compose.waitUntil(45000) { compose.onAllNodes(hasContentDescription("Abrir menu")).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("Abrir menu").performClick()
                compose.onNodeWithText("Buscar en el menú…").performTextReplacement(destination)
                compose.onNode(hasText(destination) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
                val tabMatcher = hasText(tab) and hasAnyAncestor(hasTestTag("finance-permission-tabs"))
                compose.waitUntil(45000) { compose.onAllNodes(tabMatcher).fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(tabMatcher).assertIsDisplayed().assertIsSelected()
                compose.onNodeWithText("Resumen y KPIs").assertDoesNotExist()
                compose.onNodeWithText("Estado de Resultados").assertDoesNotExist()
                compose.onNodeWithText("Flujo de Efectivo").assertDoesNotExist()
                compose.onNodeWithText(forbidden).assertDoesNotExist()
                val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("navigation-qa"))
                File(folder, "f3-android-phone-$role-workspace.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                File(folder, "f3-android-phone-$role-workspace.txt").writeText(compose.onAllNodes(isRoot())[0].printToString())
            }
        } finally { shell("settings put secure autofill_service $autofill") }
    }
}
