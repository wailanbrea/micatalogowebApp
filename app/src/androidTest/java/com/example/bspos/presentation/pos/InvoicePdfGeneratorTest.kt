package com.example.bspos.presentation.pos

import android.content.Context
import android.graphics.pdf.PdfRenderer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.InvoiceConfig
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SalePaymentType
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvoicePdfGeneratorTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")

    private fun product(name: String, price: Long) = Product(
        id = UUID.randomUUID(),
        name = name,
        internalCode = "PDF-${UUID.randomUUID()}",
        categoryId = UUID.randomUUID(),
        unitId = UUID.randomUUID(),
        salePrice = price,
        averageCost = price / 2,
        createdAt = now,
        updatedAt = now,
    )

    private fun sale(total: Long) = Sale(
        id = UUID.randomUUID(),
        invoiceNumber = "PDF-QA-001",
        date = now,
        subtotal = total,
        total = total,
        paymentType = SalePaymentType.CASH,
        paidAmount = total,
        createdAt = now,
        updatedAt = now,
    )

    private fun pageCount(context: Context, uri: android.net.Uri): Int =
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }

    @Test
    fun createsReadablePdfThroughFileProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val line = PosCartLine(product("Factura PDF QA", 12500), 2)
        val uri = InvoicePdfGenerator.create(
            context,
            sale(25000),
            listOf(line),
            CurrencyUnit.DOP,
            InvoiceConfig(businessName = "BSolutions QA"),
        )

        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals(1, pageCount(context, uri))
    }

    @Test
    fun longCartCreatesMoreThanOnePdfPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val lines = (1..40).map { index ->
            PosCartLine(product("Producto PDF QA con nombre largo $index", 10000), 1)
        }
        val uri = InvoicePdfGenerator.create(context, sale(400000), lines)

        assertTrue("Una factura larga debe paginarse", pageCount(context, uri) >= 2)
    }
}
