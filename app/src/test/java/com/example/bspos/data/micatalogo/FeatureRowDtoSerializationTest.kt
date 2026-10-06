package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureRowDtoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `row id accepts numeric values from legacy feature responses`() {
        val row = json.decodeFromString<FeatureRowDto>("""{"id":1,"primary":"Pedido"}""")

        assertEquals("1", row.id)
    }

    @Test
    fun `row id keeps string values from current feature responses`() {
        val row = json.decodeFromString<FeatureRowDto>("""{"id":"order-1","primary":"Pedido"}""")

        assertEquals("order-1", row.id)
    }
}
