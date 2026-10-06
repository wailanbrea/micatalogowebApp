package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.ShopDto
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessPresentationContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `presentation contract is backward compatible with old shop payloads`() {
        val shop = json.decodeFromString<ShopDto>("""{"id":"legacy","name":"Tienda"}""")

        assertEquals("general_retail", shop.presentation.archetype)
        assertTrue(shop.presentation.catalog.showStock)
    }

    @Test
    fun `presentation carries dynamic labels and capability driven controls`() {
        val shop = json.decodeFromString<ShopDto>("""
            {
              "id":"fashion",
              "presentation": {
                "archetype":"fashion",
                "terminology":{"products":"Artículos","new_product":"Nuevo artículo"},
                "pos":{"show_wholesale":false,"show_credit":true,"show_inventory":true},
                "catalog":{"show_stock":true}
              }
            }
        """.trimIndent())

        assertEquals("fashion", shop.presentation.archetype)
        assertEquals("Artículos", shop.presentation.terminology["products"])
        assertFalse(shop.presentation.pos.showWholesale)
        assertTrue(shop.presentation.pos.showCredit)
    }

    @Test
    fun `domain presentation falls back for missing terminology`() {
        assertEquals("Productos", MiCatalogoBusinessPresentation().term("products", "Productos"))
    }
}
