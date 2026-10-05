package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.ShopDto
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MiCatalogoQuotaTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun premiumQuotaIsDecodedFromServer() {
        val shop = json.decodeFromString<ShopDto>("""{"id":"aromas","quota":{"plan":"premium","plan_label":"Premium","product_count":239,"product_limit":500,"products_remaining":261,"image_limit":3,"can_add_products":true}}""")
        assertEquals(239, shop.quota?.productCount)
        assertEquals(500, shop.quota?.productLimit)
        assertEquals(261, shop.quota?.productsRemaining)
    }

    @Test
    fun olderServerDoesNotInventAQuota() {
        assertNull(json.decodeFromString<ShopDto>("""{"id":"old","name":"Old shop"}""").quota)
    }

    @Test
    fun validationMessageIsShownInsteadOfHttpReasonPhrase() {
        val body = """{"message":"Validation failed","errors":{"products":["El plan Premium permite 500 productos."]}}"""
        assertEquals("El plan Premium permite 500 productos.", serverErrorMessage(body, "Error"))
        assertEquals("Cuenta suspendida", serverErrorMessage("""{"message":"Cuenta suspendida"}""", "Error"))
        assertEquals("Error", serverErrorMessage("<html>Bad gateway</html>", "Error"))
    }
}
