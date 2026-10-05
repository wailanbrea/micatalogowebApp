package com.example.bspos.presentation.pos

import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SalePaymentType
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceFormatterTest {
    @Test
    fun receiptUsesConfiguredWidthAndIncludesTotals() {
        val product = Product(
            id = UUID.randomUUID(),
            name = "Producto con un nombre suficientemente largo para envolver",
            internalCode = "P-1",
            categoryId = UUID.randomUUID(),
            unitId = UUID.randomUUID(),
            salePrice = 12500,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val sale = Sale(
            id = UUID.randomUUID(),
            invoiceNumber = "POS-1",
            date = Instant.EPOCH,
            subtotal = 25000,
            total = 25000,
            paymentType = SalePaymentType.CASH,
            paidAmount = 25000,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        val receipt = formatInvoice(sale, listOf(PosCartLine(product, 2)), maxLineWidth = 32)

        assertTrue(receipt.lines().all { it.length <= 32 })
        assertTrue(receipt.contains("TOTAL: RD$ 250.00"))
        assertTrue(receipt.contains("Producto con un nombre"))
    }
}
