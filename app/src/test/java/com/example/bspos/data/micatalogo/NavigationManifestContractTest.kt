package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.ShopDto
import com.example.bspos.data.micatalogo.dto.decodeNavigationManifest
import com.example.bspos.domain.model.visibleSections
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class NavigationManifestContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(value: String) = decodeNavigationManifest(json.parseToJsonElement(value), json)

    @Test fun cachedShopRetainsManifestAndLegacyPermissionFields() {
        val input = """{"id":"shop-a","name":"QA_NAV_A","menu_permissions":["cash"],"enabled_menu_keys":null,
            "navigation":{"schema_version":1,"sections":[{"id":"sales","label":"Ventas","items":[
                {"id":"cash","key":"cash","label":"Caja","destination":"cash","url":"https://ignored.test"}]}]}}"""
        val shop = json.decodeFromString<ShopDto>(input)
        val cached = json.decodeFromString<ShopDto>(json.encodeToString(shop))
        assertEquals(listOf("cash"), cached.menuPermissions)
        assertNull(cached.enabledMenuKeys)
        assertEquals("cash", decodeNavigationManifest(cached.navigation, json)!!.sections.single().items.single().destination)
    }

    @Test fun legacyUnknownAndMalformedManifestsDoNotBreakShopDecoding() {
        assertNull(json.decodeFromString<ShopDto>("""{"id":"legacy"}""").navigation)
        for (value in listOf("null", "17", "[]", """{"schema_version":99}""", """{"schema_version":1,"sections":"bad"}""")) {
            val shop = json.decodeFromString<ShopDto>("""{"id":"safe","navigation":$value}""")
            assertEquals("safe", shop.id)
            assertNull(decodeNavigationManifest(shop.navigation, json))
        }
    }

    @Test fun arbitraryRoutesQueriesUrlsAndMismatchedKeysAreRejected() {
        val manifest = decode("""{"schema_version":1,"sections":[{"id":"sales","label":"Injected","items":[
            {"id":"attack-a","key":"cash","label":"Attack","destination":"finance"},
            {"id":"attack-b","key":"cash","label":"Attack","destination":"https://example.test"},
            {"id":"attack-c","key":"shop_settings","label":"Attack","destination":"shop_settings?section=unknown"},
            {"id":"attack-d","key":"cash","label":"Attack","destination":"admin_shops"},
            {"id":"safe","key":"cash","label":"Caja","destination":"cash"}]}]}""")!!
        assertEquals("Ventas", manifest.sections.single().label)
        assertEquals(listOf("safe"), manifest.sections.single().items.map { it.id })
    }

    @Test fun staleManifestCannotGrantSiblingPermissionsAndMoreIsNotAnAccessGate() {
        val manifest = decode("""{"schema_version":1,"sections":[{"id":"sales","label":"Ventas","items":[
            {"id":"cash","key":"cash","label":"Caja","destination":"cash"}]},
            {"id":"finance","label":"Finanzas","items":[{"id":"finance","key":"finance","label":"Ganancias","destination":"finance"}]}],
            "secondary":[{"id":"account","key":"account","label":"Mi cuenta","destination":"profile"},
            {"id":"help","key":"help","label":"Ayuda","destination":"help"}]}""")!!
        val visible = manifest.visibleSections({ it == "cash" }, routesEnabled = false)
        assertEquals(listOf("sales", "account_help"), visible.map { it.id })
        assertEquals(listOf("cash", "profile", "help"), visible.flatMap { it.items }.map { it.destination })
        assertEquals(listOf("account_help"), manifest.visibleSections({ false }, false).map { it.id })
    }

    @Test fun distributionRequiresItsExistingLocalSettingAndOwnPermission() {
        val manifest = decode("""{"schema_version":1,"contextual_sections":[{"id":"distribution","label":"Distribución",
            "requires_local_setting":"routesEnabled","items":[{"id":"routes","key":"routes","label":"Rutas","destination":"routes"}]}]}""")!!
        assertTrue(manifest.visibleSections({ true }, false).isEmpty())
        assertTrue(manifest.visibleSections({ false }, true).isEmpty())
        assertEquals("distribution", manifest.visibleSections({ it == "routes" }, true).single().id)
    }

    @Test fun unknownSectionsAndSecondaryBusinessDestinationsAreNotTrusted() {
        val manifest = decode("""{"schema_version":1,"sections":[{"id":"platform","label":"Admin","items":[
            {"id":"cash","key":"cash","label":"Caja","destination":"cash"}]}],"secondary":[
            {"id":"finance","key":"finance","label":"Ganancias","destination":"finance"}]}""")!!
        assertTrue(manifest.sections.isEmpty())
        assertTrue(manifest.secondary.isEmpty())
    }
}
