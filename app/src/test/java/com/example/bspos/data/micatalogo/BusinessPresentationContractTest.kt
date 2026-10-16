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
        assertEquals(0, shop.presentation.productForm.schemaVersion)
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
    fun `adaptive product form contract decodes additively`() {
        val shop = json.decodeFromString<ShopDto>("""
            {
              "id":"perfume",
              "presentation": {
                "archetype":"fragrance",
                "product_form": {
                  "schema_version":1,
                  "default_sale_unit":"bottle",
                  "available_sale_units":["unit","bottle","ml"],
                  "sale_unit_to_inventory_unit":{"unit":"unit","bottle":"bottle","ml":"ml"},
                  "inventory_unit_uuids":{"unit":"550e8400-e29b-41d4-a716-446655440000","bottle":"550e8400-e29b-41d4-a716-446655440001"},
                  "supported_fields":["name","category","photo","price","volume_ml"],
                  "feature_states":{"model":"unsupported","serial_tracking":"unsupported"}
                }
              }
            }
        """.trimIndent())

        assertEquals(1, shop.presentation.productForm.schemaVersion)
        assertEquals("bottle", shop.presentation.productForm.defaultSaleUnit)
        assertTrue(shop.presentation.productForm.availableSaleUnits.contains("bottle"))
        assertEquals("bottle", shop.presentation.productForm.saleUnitToInventoryUnit["bottle"])
        assertEquals("unsupported", shop.presentation.productForm.featureStates["serial_tracking"])
    }

    @Test
    fun `domain presentation falls back for missing terminology`() {
        assertEquals("Productos", MiCatalogoBusinessPresentation().term("products", "Productos"))
    }
}
