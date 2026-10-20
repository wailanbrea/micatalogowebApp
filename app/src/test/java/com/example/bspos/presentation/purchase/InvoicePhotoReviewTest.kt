package com.example.bspos.presentation.purchase

import com.example.bspos.data.micatalogo.dto.PurchaseProductDto
import org.junit.Assert.*
import org.junit.Test

class InvoicePhotoReviewTest {
    private val product = PurchaseProductDto("p1", "Perfume de ejemplo", "SKU-100", "120.00")
    @Test fun identifiesCatalogCodesWithoutGuessingFinancialValues() {
        val line = photoInvoiceSuggestions("SKU-100 5 10.00 50.00", listOf(product)).single()
        assertEquals("p1", line.productId)
        assertEquals("", line.quantity)
        assertEquals("", line.unitCost)
    }
    @Test fun codeSubstringIsNotAnExactProductMatch() {
        val line = photoInvoiceSuggestions("SKU-1000", listOf(product)).single()
        assertEquals("", line.productId)
    }
    @Test fun repeatedMentionDoesNotInventMultipleQuantities() {
        assertEquals(1, photoInvoiceSuggestions("SKU-100\nSKU-100", listOf(product)).size)
    }
    @Test fun unmatchedPhotoRequiresManualProductSelection() {
        val line = photoInvoiceSuggestions("Factura sin nombre conocido", listOf(product)).single()
        assertEquals("", line.productId)
        assertEquals("", line.quantity)
    }
}
