package com.example.bspos.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCatalogoMenuAccessTest {
    @Test
    fun assignedSellerOnlyGetsMandatoryAndGrantedMenus() {
        val permissions = listOf("customers", "more")

        assertTrue(canAccessMiCatalogoMenu(false, false, permissions, "sales"))
        assertTrue(canAccessMiCatalogoMenu(false, false, permissions, "products"))
        assertTrue(canAccessMiCatalogoMenu(false, false, permissions, "printers"))
        assertTrue(canAccessMiCatalogoMenu(false, false, permissions, "customers"))
        assertFalse(canAccessMiCatalogoMenu(false, false, permissions, "inventory"))
        assertFalse(canAccessMiCatalogoMenu(false, false, permissions, "settings"))
        assertFalse(canAccessMiCatalogoMenu(false, false, permissions, "sellers"))
    }

    @Test
    fun shopAndPlatformOwnersGetAllShopMenus() {
        assertTrue(canAccessMiCatalogoMenu(false, true, emptyList(), "settings"))
        assertTrue(canAccessMiCatalogoMenu(false, true, emptyList(), "sellers"))
        assertTrue(canAccessMiCatalogoMenu(true, false, emptyList(), "settings"))
    }

    @Test
    fun sellerPermissionEditorCoversTheSameFeatureKeysAsThePanel() {
        val expected = setOf(
            "sales", "products", "printers", "quotes", "orders", "encargos", "shipments",
            "day_close", "containers", "loads", "suppliers", "purchase_invoices", "photos",
            "storefront", "services", "price_health", "pricing", "decants", "attributes",
            "import", "customers", "credit", "inventory", "collections", "cash", "finance",
            "inventory_adjustments", "partners", "expenses", "reports", "commissions",
            "authorizations", "returns", "routes", "more", "accountant", "account", "updates",
            "help", "practice", "support", "metrics", "public_catalog"
        )

        assertTrue(expected.all { key -> SellerMenuOptions.any { it.key == key } })
        assertTrue(SellerMenuOptions.none { it.key in setOf("settings", "shop_settings", "sellers") })
    }
}
