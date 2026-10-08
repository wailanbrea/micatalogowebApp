package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.SellerCreateRequestDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SellerCreatePermissionsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun selectedMenusAreIncludedInTheCreationRequest() {
        val request = SellerCreateRequestDto("seller@example.com", "percentage", "5", listOf("customers"))
        val payload = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertEquals(listOf("customers"), payload.getValue("menu_permissions").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun removingAllMenusSendsAnExplicitEmptyArray() {
        val request = SellerCreateRequestDto("seller@example.com", "percentage", "5", emptyList())
        val payload = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertEquals(0, payload.getValue("menu_permissions").jsonArray.size)
    }
}
