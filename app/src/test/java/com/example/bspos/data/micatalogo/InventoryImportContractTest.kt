package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.InventoryImportPreviewDto
import com.example.bspos.data.micatalogo.dto.InventoryImportRequestDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class InventoryImportContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val quota = """{"plan":"pro","plan_label":"Pro","product_count":0,"product_limit":1500,"products_remaining":1500,"image_limit":3,"can_add_products":true}"""

    @Test fun oldPreviewRemainsReadableButCannotBecomeAClientSideConfirmation() {
        val preview = json.decodeFromString<InventoryImportPreviewDto>("""{"quota":$quota,"headers":["nombre","precio"],"rows":[],"valid_rows":0,"invalid_rows":0}""")
        assertNull(preview.sessionId)
        assertEquals(1, preview.headerRow)
    }

    @Test fun sessionRequestDoesNotSerializeInventoryRows() {
        val request = InventoryImportRequestDto(sessionId = "server-session", duplicateStrategy = "update", createMissingCategories = true)
        val payload = json.parseToJsonElement(json.encodeToString(request)).jsonObject
        assertEquals("server-session", payload["session_id"]!!.jsonPrimitive.content)
        assertFalse(payload.containsKey("rows"))
        assertEquals("update", payload["duplicate_strategy"]!!.jsonPrimitive.content)
    }

    @Test fun adaptiveMetadataRetainsOriginalLabelsAndIdentifierStrings() {
        val preview = json.decodeFromString<InventoryImportPreviewDto>("""{
            "quota":$quota,"session_id":"session","header_row":8,
            "sheet":{"name":"Existencias","index":1,"confidence":1},
            "original_headers":["Código del producto","Nombre"],
            "mapping_confidence":{"barcode":{"field":"barcode","source_column":"codigo del producto","original_header":"Código del producto","confidence":0.9,"reason":"EAN/UPC","examples":["0850050062035"]}},
            "ignored_columns":[{"source_column":"creado","original_header":"Creado","reason":"Calculado"}],
            "rows":[{"name":"Producto","barcode":"0850050062035","product_code":"000045","price":"2500.50","valid":true}],
            "counts":{"new":1},"new_rows_count":1
        }""")
        assertEquals(8, preview.headerRow)
        assertEquals("0850050062035", preview.rows.single().barcode)
        assertEquals("000045", preview.rows.single().productCode)
        assertEquals("2500.50", preview.rows.single().price)
        assertEquals("Código del producto", preview.mappingConfidence["barcode"]!!.header)
        assertEquals(1, preview.newRows)
    }
}
