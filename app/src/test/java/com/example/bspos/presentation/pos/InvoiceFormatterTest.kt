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

    @Test
    fun receiptIncludesCustomerDetailsOnCreditSale() {
        val product = Product(
            id = UUID.randomUUID(),
            name = "Perfume",
            internalCode = "P-2",
            categoryId = UUID.randomUUID(),
            unitId = UUID.randomUUID(),
            salePrice = 100000,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val customer = com.example.bspos.domain.model.Customer(
            id = UUID.randomUUID(),
            businessName = "Tienda Juan",
            firstName = "Juan",
            lastName = "Perez",
            phone = "809-555-1234",
            taxId = "001-0000000-1",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val sale = Sale(
            id = UUID.randomUUID(),
            invoiceNumber = "POS-CR-1",
            date = Instant.EPOCH,
            subtotal = 100000,
            total = 100000,
            paymentType = SalePaymentType.CREDIT,
            paidAmount = 0,
            pendingAmount = 100000,
            customerId = customer.id,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

        val receipt = formatInvoice(sale, listOf(PosCartLine(product, 1)), maxLineWidth = 42, customer = customer)

        assertTrue(receipt.contains("Cliente: Juan Perez"))
        assertTrue(receipt.contains("RNC/ID: 001-0000000-1"))
        assertTrue(receipt.contains("Tel: 809-555-1234"))
    }
}
