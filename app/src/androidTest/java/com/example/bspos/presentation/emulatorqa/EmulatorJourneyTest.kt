package com.example.bspos.presentation.emulatorqa

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.accessibility.AccessibilityNodeInfo
import com.example.bspos.BuildConfig
import com.example.bspos.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.rules.TestName
import com.example.bspos.data.local.tenant.LiveQaFixture
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real activity + real local QA API. Never eligible against production. */
@RunWith(AndroidJUnit4::class)
class EmulatorJourneyTest {
    @get:Rule val testName = TestName()
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private var drawerQuery=""
    private val newCustomer="QA Nuevo A2"
    private var fixture: org.json.JSONObject? = null
    private var previousAutofill: String? = null
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    @Before fun prepareIndependentScenario() {
        fixture = LiveQaFixture.optional(testName.methodName)
        if (fixture != null) {
            previousAutofill = shell("settings get secure autofill_service").also {
                require(it == "null" || Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+").matches(it)) { "Unexpected autofill component" }
            }
            shell("settings put secure autofill_service null")
            dismissAutofill()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext,
                com.example.bspos.data.micatalogo.PosSaleSyncWorkerDependencies::class.java)
            runBlocking { dependencies.connectionRepository().logout() }
            compose.activityRule.scenario.recreate()
        }
    }
    @After fun restoreAutofill() {
        previousAutofill?.let { shell("settings put secure autofill_service $it") }
    }
    private fun requireLive() {
        assumeTrue(fixture != null || InstrumentationRegistry.getArguments().getString("emulatorQaLive") == "true")
        check(BuildConfig.APPLICATION_ID.contains(".offlinecheck"))
        check(BuildConfig.MICATALOGO_API_BASE_URL in setOf("http://127.0.0.1:18765/", "http://127.0.0.1:18766/", "http://127.0.0.1:18767/"))
    }
    private fun fixtureEmail() = fixture?.getString("email") ?: "emulador-qa@example.test"
    private fun fixturePassword() = fixture?.getString("password") ?: "Emulador-QA-2026!"

    @Test fun loginSharedCustomerCreditOrderCollectionAndShopSwitch() {
        requireLive()
        try {
            waitFor("Iniciar sesion")
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(fixtureEmail())
            compose.onAllNodes(hasSetTextAction())[1].performTextReplacement(fixturePassword())
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Iniciar sesion").performClick()
            waitFor("Buenas, QA Tienda A")
            dismissAutofill()
            evidence("01-login-a")

            navigate("Clientes")
            waitFor("QA Cliente A Activo")
            compose.onNodeWithText("QA Cliente A Inactivo").assertExists()
            compose.onNodeWithText("8095550101 · Inactivo").assertExists()
            compose.onNodeWithText("QA Cliente B Activo").assertDoesNotExist()
            if (InstrumentationRegistry.getArguments().getString("skipQaCustomerCreation")!="true") {
                compose.onNodeWithText("Nuevo").performClick()
                fill("Nombre *","QA")
                fill("Apellido *","Nuevo A2")
                fill("Número de documento *","QA-LIVE-A-004")
                fill("Teléfono *","8095550133")
                fill("Dirección *","Calle QA 3")
                fill("Límite de crédito (RD$) *","10000")
                Espresso.closeSoftKeyboard()
                compose.onNodeWithText("Guardar").performClick()
            }
            waitFor(newCustomer)
            evidence("02-customer-created-a")

            if (InstrumentationRegistry.getArguments().getString("resumeAfterOrder")!="true") {
            navigate("Caja")
            waitFor("No hay una sesión de caja abierta")
            fill("Fondo inicial","0")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Abrir caja").performClick()
            waitFor("Caja abierta")
            evidence("02b-cash-open-a")

            navigate("Pedidos")
            waitFor("QA Comprador A2")
            compose.onNodeWithText("Confirmar pedido").performScrollTo().performClick()
            compose.onNodeWithText("Crédito").performClick()
            waitFor(newCustomer)
            compose.onNodeWithText("QA Cliente A Inactivo").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText(newCustomer).performScrollTo().performClick()
            evidence("03-order-shared-customer-a")
            compose.onNodeWithText("Confirmar venta").performClick()
            waitFor("Pedido actualizado")
            compose.onNodeWithText("Cerrar").performClick()
            compose.waitUntil(45_000) { compose.onAllNodesWithText("Confirmar pedido").fetchSemanticsNodes().isEmpty() }
            waitFor("QA Comprador A2")
            evidence("04-order-confirmed-a")
            }

            navigate("Crédito")
            waitFor(newCustomer)
            compose.onNodeWithText("Monto a cobrar").performTextReplacement("600")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Cobrar").performScrollTo().performClick()
            waitFor("Nadie te debe")
            evidence("05-debt-collected-a")

            switchShop("QA Tienda B")
            navigate("Clientes")
            waitFor("QA Cliente B Activo")
            compose.onNodeWithText("QA Cliente B Inactivo").assertExists()
            compose.onNodeWithText(newCustomer).assertDoesNotExist()
            compose.onNodeWithText("QA Cliente A Activo").assertDoesNotExist()
            evidence("06-customers-only-b")

            navigate("Terminal")
            waitFor("QA Producto B")
            compose.onNodeWithText("QA Producto A").assertDoesNotExist()
            compose.onNodeWithText("QA Producto B").performClick()
            evidence("07-cart-b-before-switch")
            switchShop("QA Tienda A")
            navigate("Terminal")
            waitFor("QA Producto A")
            compose.onNodeWithText("QA Producto B").assertDoesNotExist()
            compose.onNodeWithText("Cobrar").assertDoesNotExist() // The empty cart has no charge action.
            evidence("08-terminal-a-after-switch")
            navigate("Clientes")
            waitFor(newCustomer)
            compose.onNodeWithText("QA Cliente B Activo").assertDoesNotExist()
            evidence("09-customer-retained-only-a")
            navigate("Caja")
            waitFor("Caja abierta")
            fill("Efectivo contado","600")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Cerrar caja").performScrollTo().performClick()
            waitFor("No hay una sesión de caja abierta")
            evidence("10-cash-closed-a")
        } catch(error:Throwable) {
            compose.onAllNodes(isRoot())[0].printToLog("EMULATOR_QA_FAILURE")
            evidence("failure")
            throw error
        }
    }

    private fun waitFor(text:String) = compose.waitUntil(45_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun ownerSummaryReadsServerForEachShop() {
        requireLive()
        waitFor("Iniciar sesion")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(fixtureEmail())
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement(fixturePassword())
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Iniciar sesion").performClick()
        waitFor("Buenas, QA Tienda A")
        dismissAutofill()
        waitFor("RD$ 600.00")
        compose.onNodeWithText("RD$ 360.00").assertExists()
        evidence("11-server-summary-a-600-profit-360")
        compose.onNodeWithText("Este mes").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("server-summary-metrics").fetchSemanticsNodes().isNotEmpty() }
        waitFor("RD$ 600.00")
        switchShop("QA Tienda B")
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("server-summary-metrics").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(45_000) { compose.onAllNodesWithText("RD$ 0.00").fetchSemanticsNodes().size>=4 }
        compose.onNodeWithText("RD$ 600.00").assertDoesNotExist()
        compose.onNodeWithText("RD$ 360.00").assertDoesNotExist()
        evidence("12-server-summary-b-zero")
        switchShop("QA Tienda A")
        waitFor("RD$ 600.00")
    }
    @Test fun ordersEntryAndReturnDoNotShowTheEmptyLandingForExistingOrders() {
        requireLive()
        waitFor("Iniciar sesion")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(fixtureEmail())
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement(fixturePassword())
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Iniciar sesion").performClick()
        waitFor("Buenas, QA Tienda A")
        dismissAutofill()
        navigate("Pedidos")
        compose.onNodeWithText("COMPARTIR MI TIENDA").assertDoesNotExist()
        waitFor("QA Comprador A2")
        compose.onNodeWithText("Aún no tienes pedidos").assertDoesNotExist()
        evidence("13-orders-first-entry")
        compose.onNode(hasText("Confirmados") and hasClickAction()).performClick()
        waitFor("QA Comprador A2")
        navigate("Terminal")
        waitFor("QA Producto A")
        navigate("Pedidos")
        compose.onNodeWithText("COMPARTIR MI TIENDA").assertDoesNotExist()
        waitFor("QA Comprador A2")
        evidence("14-orders-return-filter-retained")
    }

    @Test fun compactCustomersOpenMovementsAndExplainTheirDebt() {
        requireLive()
        waitFor("Iniciar sesion")
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(fixtureEmail())
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement(fixturePassword())
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Iniciar sesion").performClick()
        compose.waitUntil(45_000) { compose.onAllNodesWithText("Buenas, QA Tienda A").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Buenas, QA Tienda B").fetchSemanticsNodes().isNotEmpty() }
        dismissAutofill()
        if(compose.onAllNodesWithText("Buenas, QA Tienda B").fetchSemanticsNodes().isEmpty()) switchShop("QA Tienda B")
        navigate("Clientes")
        waitFor("QA Cliente B Activo")
        evidence("15-compact-customer-cards")
        compose.onNodeWithText("QA Cliente B Activo").performClick()
        waitFor("Origen de la deuda")
        compose.onNodeWithText(fixture?.getString("invoice_number") ?: "FAC-20261011-AJG8LEOW").assertExists()
        compose.onNodeWithText("Pedido ${fixture?.getString("order_b_number") ?: "QA-PEDIDO-B"} · Pedido WhatsApp").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Abono recibido"))
        compose.onAllNodesWithText("Abono recibido")[0].assertExists()
        evidence("16-customer-debt-origin-and-movements")
        Espresso.pressBack()
        waitFor("Cartera pendiente")
        compose.onNodeWithText("QA Cliente B Activo").assertExists()
        compose.onNodeWithText("QA Cliente A Activo").assertDoesNotExist()
    }

    private fun fill(label:String,value:String) = compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
    private fun openDrawer() {
        val menu=hasContentDescription("Abrir menu") or hasContentDescription("Abrir menú")
        compose.onAllNodes(menu)[0].performClick()
        compose.waitForIdle()
    }
    private fun navigate(label:String) {
        openDrawer()
        val query=hasSetTextAction() and hasText(if(drawerQuery.isBlank()) "Buscar en el menú…" else drawerQuery)
        compose.onNode(query).performTextReplacement(label)
        drawerQuery=label
        Espresso.closeSoftKeyboard()
        compose.onNode(hasText(label) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).performScrollTo().performClick()
        compose.waitForIdle()
    }
    private fun switchShop(name:String) {
        openDrawer()
        compose.onNodeWithText("Tienda activa").performClick()
        compose.onNode(hasText(name) and hasClickAction()).performClick()
        drawerQuery=""
        waitFor("Buenas, $name")
        dismissAutofill()
    }
    private fun dismissAutofill() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText("Not now")?.firstOrNull()
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    private fun evidence(label:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=context.getExternalFilesDir("qa") ?: return
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(folder,"$label-${System.currentTimeMillis()}.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        image.recycle()
        println("EMULATOR_QA_STEP: $label")
    }
}
