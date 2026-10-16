package com.example.bspos.presentation.emulatorqa

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.BuildConfig
import com.example.bspos.MainActivity
import com.example.bspos.data.micatalogo.PosSaleSyncWorkerDependencies
import com.example.bspos.domain.model.MiCatalogoResult
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue

class NavigationLiveRevocationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun request(path: String, method: String, token: String? = null, body: JSONObject? = null): String {
        val http = URL(BuildConfig.MICATALOGO_API_BASE_URL + path).openConnection() as HttpURLConnection
        http.requestMethod = method
        http.connectTimeout = 15000; http.readTimeout = 30000
        http.setRequestProperty("Accept", "application/json")
        http.setRequestProperty("X-MiCatalogo-Version-Code", BuildConfig.VERSION_CODE.toString())
        token?.let { http.setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (body != null) {
                http.doOutput = true
                http.setRequestProperty("Content-Type", "application/json")
                http.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            check(http.responseCode in 200..299) { "QA navigation request rejected: $method $path (${http.responseCode})" }
            return http.inputStream.bufferedReader().use { it.readText() }
        } finally { http.disconnect() }
    }

    @Test fun photoOnlyWorkspaceOpensImagePickerWithoutProductEditor() {
        assumeTrue("Requiere ejecución LIVE de navegación contra la instancia QA aislada 127.0.0.1:18768",
            InstrumentationRegistry.getArguments().getString("navigationQa") == "true" &&
                BuildConfig.APPLICATION_ID.contains(".offlinecheck") &&
                BuildConfig.MICATALOGO_API_BASE_URL == "http://127.0.0.1:18768/")
        val identity = JSONObject(request("__qa/navigation/identity", "GET"))
        check(identity.getString("environment") == "testing" && identity.getString("driver") == "sqlite")
        check(identity.getString("database") == "C:/Users/waila/AppData/Local/Temp/opencode/micatalogo-navigation-20261014.sqlite")
        val login = JSONObject(request("api/v1/auth/login", "POST", body = JSONObject()
            .put("email", "qa-nav-owner@example.test").put("password", "QA-NAV-2026!").put("device_name", "QA_NAV_Photos")))
        val token = login.getString("access_token")
        val shops = JSONArray(request("api/v1/shops", "GET", token))
        val shop = (0 until shops.length()).map { shops.getJSONObject(it) }.single { it.getString("name") == "QA_NAV_Perfumes" }
        val sellers = shop.getJSONArray("sellers")
        val target = (0 until sellers.length()).map { sellers.getJSONObject(it) }.single { it.getString("email") == "qa-nav-services@example.test" }
        val original = JSONArray(target.getJSONArray("menu_permissions").toString())
        val permissionPath = "api/v1/shops/${shop.getString("id")}/sellers/${target.getString("id")}/menus"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val connection = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext, PosSaleSyncWorkerDependencies::class.java).connectionRepository()
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val autofill = shell("settings get secure autofill_service")
        require(autofill == "null" || Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+").matches(autofill))
        try {
            request(permissionPath, "PUT", token, JSONObject().put("menu_permissions", JSONArray().put("photos").put("account").put("help")))
            shell("settings put secure autofill_service null")
            runBlocking { connection.logout() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(45000) { compose.onAllNodesWithText("Iniciar sesion").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("qa-nav-services@example.test")
            compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("QA-NAV-2026!")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Iniciar sesion").performClick()
            compose.waitUntil(45000) { compose.onAllNodesWithContentDescription("Abrir menu").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Abrir menu").performClick()
            compose.onNodeWithText("Buscar en el menú…").performTextReplacement("Fotos")
            compose.onNode(hasText("Fotos") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
            compose.waitUntil(45000) { compose.onAllNodesWithText("Gestión de fotografías").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Todos").performClick()
            compose.waitUntil(45000) { compose.onAllNodesWithText("Cambiar foto").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Nuevo producto").assertDoesNotExist()
            compose.onNodeWithText("Nuevo servicio").assertDoesNotExist()
            val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("navigation-qa"))
            File(folder, "f5-photo-only-workspace.txt").writeText(compose.onAllNodes(isRoot())[0].printToString())
            compose.onAllNodesWithText("Cambiar foto")[0].performClick()
            val pickerPackage = Regex(".*(documentsui|providers\\.media\\.module|photopicker).*")
            val deadline = android.os.SystemClock.uptimeMillis() + 15000
            while (android.os.SystemClock.uptimeMillis() < deadline &&
                !pickerPackage.matches(instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty())) {
                Thread.sleep(100)
            }
            val activePackage = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()
            File(folder, "f5-photo-only-picker-package.txt").writeText(activePackage)
            check(pickerPackage.matches(activePackage)) {
                "El permiso Fotos debe abrir el selector de imágenes, no el editor de productos."
            }
            File(folder, "f5-photo-only-picker.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitUntil(15000) { compose.onAllNodesWithText("Gestión de fotografías").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Eliminar producto").assertDoesNotExist()
        } finally {
            try {
                request(permissionPath, "PUT", token, JSONObject().put("menu_permissions", original))
                runBlocking { connection.shops() }
            } finally {
                shell("settings put secure autofill_service $autofill")
                request("api/v1/auth/logout", "POST", token)
            }
        }
    }

    @Test fun serverRevocationClosesTheOpenWorkspaceAndBackDoesNotRestoreIt() {
        check(InstrumentationRegistry.getArguments().getString("navigationQa") == "true")
        check(BuildConfig.APPLICATION_ID.contains(".offlinecheck") && BuildConfig.MICATALOGO_API_BASE_URL == "http://127.0.0.1:18768/")
        val identity = JSONObject(request("__qa/navigation/identity", "GET"))
        check(identity.getString("environment") == "testing" && identity.getString("driver") == "sqlite")
        check(identity.getString("database") == "C:/Users/waila/AppData/Local/Temp/opencode/micatalogo-navigation-20261014.sqlite")
        val ownerLogin = JSONObject(request("api/v1/auth/login", "POST", body = JSONObject()
            .put("email", "qa-nav-owner@example.test").put("password", "QA-NAV-2026!").put("device_name", "QA_NAV_Revocation")))
        val ownerToken = ownerLogin.getString("access_token")
        val ownerShops = JSONArray(request("api/v1/shops", "GET", ownerToken))
        val shop = (0 until ownerShops.length()).map { ownerShops.getJSONObject(it) }.single { it.getString("name") == "QA_NAV_Perfumes" }
        val sellers = shop.getJSONArray("sellers")
        val target = (0 until sellers.length()).map { sellers.getJSONObject(it) }.single { it.getString("email") == "qa-nav-services@example.test" }
        val original = JSONArray(target.getJSONArray("menu_permissions").toString())
        check((0 until original.length()).any { original.getString(it) == "services" })
        val permissionPath = "api/v1/shops/${shop.getString("id")}/sellers/${target.getString("id")}/menus"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val connection = EntryPointAccessors.fromApplication(instrumentation.targetContext.applicationContext, PosSaleSyncWorkerDependencies::class.java).connectionRepository()
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val autofill = shell("settings get secure autofill_service")
        require(autofill == "null" || Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+").matches(autofill))
        shell("settings put secure autofill_service null")
        var changed = false
        try {
            runBlocking { connection.logout() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(45000) { compose.onAllNodesWithText("Iniciar sesion").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("qa-nav-services@example.test")
            compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("QA-NAV-2026!")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("Iniciar sesion").performClick()
            compose.waitUntil(45000) { compose.onAllNodesWithContentDescription("Abrir menu").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Abrir menu").performClick()
            compose.onNodeWithText("Buscar en el menú…").performTextReplacement("Servicios")
            compose.onNode(hasText("Servicios") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
            try {
                compose.waitUntil(45000) { compose.onAllNodesWithText("Nuevo servicio").fetchSemanticsNodes().isNotEmpty() }
            } catch (failure: Throwable) {
                val diagnostic = runBlocking { connection.shops() }
                if (diagnostic is MiCatalogoResult.Success) println("QA_NAV_SERVICE_POLICY: " + diagnostic.value.map { Triple(it.id, it.menuPermissions, it.catalogPermissions) })
                val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("navigation-qa"))
                File(folder, "f5-live-revocation-open-failure.txt").writeText(compose.onAllNodes(isRoot())[0].printToString())
                File(folder, "f5-live-revocation-open-failure.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                throw failure
            }
            request(permissionPath, "PUT", ownerToken, JSONObject().put("menu_permissions", JSONArray().put("account").put("help")))
            changed = true
            check(runBlocking { connection.shops() } is MiCatalogoResult.Success)
            compose.waitUntil(45000) { compose.onAllNodesWithText("Tu administrador no ha habilitado el acceso a Ventas.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Nuevo servicio").assertDoesNotExist()
            compose.onNodeWithContentDescription("Abrir menu").performClick()
            compose.onNodeWithText("Buscar en el menú…").performTextReplacement("Mi cuenta")
            compose.onNode(hasText("Mi cuenta") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
            compose.waitForIdle()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitUntil(45000) { compose.onAllNodesWithText("Tu administrador no ha habilitado el acceso a Ventas.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Nuevo servicio").assertDoesNotExist()
            val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("navigation-qa"))
            File(folder, "f5-live-revocation-after-back.txt").writeText(compose.onAllNodes(isRoot())[0].printToString())
            File(folder, "f5-live-revocation-after-back.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            try {
                if (changed) {
                    request(permissionPath, "PUT", ownerToken, JSONObject().put("menu_permissions", original))
                    runBlocking { connection.shops() }
                }
            } finally {
                shell("settings put secure autofill_service $autofill")
                request("api/v1/auth/logout", "POST", ownerToken)
            }
        }
    }
}
