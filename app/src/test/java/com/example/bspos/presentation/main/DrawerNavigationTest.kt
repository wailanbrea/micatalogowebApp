package com.example.bspos.presentation.main

import com.example.bspos.domain.model.MiCatalogoNavigation
import com.example.bspos.domain.model.MiCatalogoNavigationItem
import com.example.bspos.domain.model.MiCatalogoNavigationSection
import com.example.bspos.domain.model.visibleSections
import com.example.bspos.presentation.navigation.Screen
import org.junit.Assert.*
import org.junit.Test

class DrawerNavigationTest {
    @Test fun legacyCacheKeepsElevenAreasAndIndependentDestinations() {
        val groups = buildDrawerNavigation(null, { true }, false, false)
        assertEquals(listOf("home", "sales", "orders", "catalog", "inventory", "purchases", "customers", "finance", "reports", "storefront", "settings", "account_help"), groups.map { it.id })
        assertTrue(Screen.Cash in groups.first { it.id == "sales" }.screens)
        assertTrue(Screen.InventoryAdjustments in groups.first { it.id == "inventory" }.screens)
        assertFalse(groups.any { it.id == "distribution" || it.id == "platform" })
    }

    @Test fun restrictedSellerHasHomeSellAndMoreWithoutInventoryOrFinance() {
        val permission: (String) -> Boolean = { it == "sales" }
        val groups = buildDrawerNavigation(null, permission, false, false)
        assertEquals(listOf("home", "sales", "account_help"), groups.map { it.id })
        assertEquals(listOf("Inicio", "Vender"), phoneNavigation(permission).map { it.first })
        assertFalse(isNavigationRouteAllowed("inventory", permission, false, false))
        assertFalse(isNavigationRouteAllowed("finance", permission, false, false))
        assertTrue(isNavigationRouteAllowed("help", permission, false, false))
    }

    @Test fun revocationOverridesCachedManifestAndUnknownRoutesNeverBecomeAuthorized() {
        val manifest = MiCatalogoNavigation(listOf(MiCatalogoNavigationSection("finance", "Finanzas", listOf(
            MiCatalogoNavigationItem("finance", "finance", "Ganancias", "finance")))), emptyList(), emptyList())
        val groups = buildDrawerNavigation(manifest, { false }, false, false)
        assertEquals(listOf("home", "account_help_fallback"), groups.map { it.id })
        assertFalse(isNavigationRouteAllowed("finance", { false }, false, false))
        assertFalse(isNavigationRouteAllowed("unknown", { true }, true, true))
        assertFalse(isNavigationRouteAllowed("admin_shops", { true }, false, true))
    }

    @Test fun phoneNeverExceedsFiveItemsIncludingMoreAndDistributionIsContextual() {
        assertEquals(4, phoneNavigation { true }.size)
        assertEquals(listOf("Inicio"), phoneNavigation { false }.map { it.first })
        assertFalse(isNavigationRouteAllowed("route_loads", { true }, false, false))
        assertTrue(isNavigationRouteAllowed("route_loads", { it == "routes" }, false, true))
        assertTrue(buildDrawerNavigation(null, { it == "routes" }, false, true).any { it.id == "distribution" })
    }

    @Test fun creditLedgerAndServicesAliasesDoNotGrantOtherOperations() {
        assertTrue(isNavigationRouteAllowed("credit_ledger", { it == "credit" }, false, false))
        assertFalse(isNavigationRouteAllowed("collections", { it == "credit" }, false, false))
        assertFalse(isNavigationRouteAllowed("credit", { it == "credit" }, false, false))
        assertFalse(isNavigationRouteAllowed("credit", { it in setOf("sales", "collections") }, false, false))
        assertTrue(isNavigationRouteAllowed("credit", { it in setOf("sales", "credit") }, false, false))
        assertTrue(isNavigationRouteAllowed("catalog", { it == "services" }, false, false))
        assertFalse(isNavigationRouteAllowed("catalog_create", { it == "services" }, false, false))
    }

    @Test fun servicesOnlyCanOpenTaxonomyWithoutUnlockingPhysicalCatalog() {
        val permission: (String) -> Boolean = { it == "services" }
        val manifest = MiCatalogoNavigation(listOf(MiCatalogoNavigationSection("catalog", "Catálogo", listOf(
            MiCatalogoNavigationItem("categories", "products", "Categorías y unidades", "categories")))), emptyList(), emptyList())
        val visible = manifest.visibleSections(permission, false)
        assertEquals(listOf("categories"), visible.flatMap { it.items }.map { it.destination })
        assertTrue(isNavigationRouteAllowed("categories", permission, false, false))
        assertTrue(isNavigationRouteAllowed("catalog", permission, false, false))
        assertFalse(isNavigationRouteAllowed("catalog_create", permission, false, false))
    }
    @Test fun internalAndLegacyRoutesKeepTheirCanonicalContext() {
        assertEquals("catalog", navigationAreaId("catalog_create"))
        assertEquals("catalog", navigationAreaId("decant_create"))
        assertEquals("settings", navigationAreaId("shop_settings?section=menus"))
        assertEquals("account_help", navigationAreaId("download_app"))
        assertEquals("sales", navigationAreaId("credit"))
        assertNull(navigationAreaId("unknown"))
    }
}
