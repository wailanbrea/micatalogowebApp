package com.example.bspos.presentation.sales

import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SaleItem
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.model.SaleStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesCsvExportTest {
    @Test fun includesInvoiceAndEscapedLineDetailsWithoutLosingCents() {
        val saleId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val now = Instant.parse("2026-10-07T12:00:00Z")
        val sale = Sale(saleId, "V-42", customerId = customerId, date = now, subtotal = 1234, total = 1234, paymentType = SalePaymentType.CASH, paidAmount = 1234, status = SaleStatus.COMPLETED, createdAt = now, updatedAt = now)
        val item = SaleItem(UUID.randomUUID(), saleId, productId, quantity = 2, unitPrice = 617, unitCostSnapshot = 0, subtotal = 1234)
        val csv = buildSalesCsv(listOf(sale), mapOf(customerId to "Cliente, \"VIP\""), emptyMap(), listOf(item), CurrencyUnit.DOP)
        assertTrue(csv.contains("\"factura\",\"fecha\",\"cliente\""))
        assertTrue(csv.contains("\"Cliente, \"\"VIP\"\"\""))
        assertTrue(csv.contains("\"2\",\"RD$ 6.17\",\"RD$ 12.34\""))
    }

    @Test fun emptySaleStillProducesOneInvoiceRow() {
        val now = Instant.parse("2026-10-07T12:00:00Z")
        val sale = Sale(UUID.randomUUID(), "V-43", date = now, subtotal = 500, total = 500, paymentType = SalePaymentType.CREDIT, pendingAmount = 500, createdAt = now, updatedAt = now)
        val csv = buildSalesCsv(listOf(sale), emptyMap(), emptyMap(), emptyList(), CurrencyUnit.DOP)
        assertTrue(csv.contains("\"V-43\""))
        assertTrue(csv.contains("\"Pendiente\",\"RD$ 5.00\""))
    }
}
