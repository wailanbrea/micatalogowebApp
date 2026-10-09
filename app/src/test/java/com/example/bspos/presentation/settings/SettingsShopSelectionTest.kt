package com.example.bspos.presentation.settings

import com.example.bspos.domain.model.MiCatalogoShop
import org.junit.Assert.*
import org.junit.Test

class SettingsShopSelectionTest {
    private val shops = listOf(MiCatalogoShop("a", "Tienda A", null), MiCatalogoShop("b", "Tienda B", null))
    @Test fun selectedShopWinsWithoutChangingThePosActiveShop() {
        assertEquals("b", resolveSettingsShop(shops, "b", "a")?.id)
    }
    @Test fun removedShopFallsBackToActiveAndSingleShopNeedsNoSelection() {
        assertEquals("a", resolveSettingsShop(shops, "removed", "a")?.id)
        assertEquals("b", resolveSettingsShop(listOf(shops[1]), null, "a")?.id)
        assertNull(resolveSettingsShop(emptyList(), null, null))
    }
}
