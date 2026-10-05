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
}
