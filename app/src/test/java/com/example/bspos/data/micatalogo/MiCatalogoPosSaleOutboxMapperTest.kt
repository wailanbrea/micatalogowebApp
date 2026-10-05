package com.example.bspos.data.micatalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class MiCatalogoPosSaleOutboxMapperTest {
    private val saleId = UUID.fromString("1f80e670-0a23-4f0e-a16b-c9ea9ccefaaa")

    @Test fun capturesDecantPresentationInImmutableRequest() {
        val snapshot = MiCatalogoPosSaleOutboxMapper.snapshot(saleId, 250, 0,
            listOf(PosSaleOutboxLine("shop", "product", 1, 250,
                expectedSaleUnit = "decant", expectedVolumeMl = 5, expectedSourceProductId = "bottle")))!!
        val item = snapshot.request.items.single()
        assertEquals("decant", item.expectedSaleUnit)
        assertEquals(5, item.expectedVolumeMl)
        assertEquals("bottle", item.expectedSourceProductId)
    }

    @Test
    fun snapshotsExactCentPricesAndPartialPaymentForOneRemoteShop() {
        val snapshot = MiCatalogoPosSaleOutboxMapper.snapshot(
            saleId = saleId,
            paidAmount = 125,
            pendingAmount = 375,
            lines = listOf(
                PosSaleOutboxLine("01JSHOP", "01JPRODUCTA", 2, 125),
                PosSaleOutboxLine("01JSHOP", "01JPRODUCTB", 1, 0)
            )
        )!!

        assertEquals("01JSHOP", snapshot.remoteShopId)
        assertEquals(saleId.toString(), snapshot.request.clientSaleUuid)
        assertEquals("partial", snapshot.request.paymentStatus)
        assertEquals("3.75", snapshot.request.creditAmount)
        assertNull(snapshot.request.customerId)
        assertEquals("1.25", snapshot.request.items[0].unitPrice)
        assertEquals("0.00", snapshot.request.items[1].unitPrice)
        assertEquals(2, snapshot.request.items[0].quantity)
    }

    @Test
    fun snapshotsWholesaleModeWithTheWholesaleLinePrice() {
        val snapshot = MiCatalogoPosSaleOutboxMapper.snapshot(
            saleId = saleId,
            paidAmount = 44000,
            pendingAmount = 0,
            lines = listOf(PosSaleOutboxLine("01JSHOP", "01JPRODUCT", 2, 22000)),
            saleMode = "wholesale"
        )!!

        assertEquals("wholesale", snapshot.request.saleMode)
        assertEquals("220.00", snapshot.request.items.single().unitPrice)
        assertEquals("paid", snapshot.request.paymentStatus)
    }

    @Test
    fun snapshotsSingleMethodCardAndTransferCollections() {
        val lines = listOf(PosSaleOutboxLine("01JSHOP", "01JPRODUCT", 1, 1250))
        val card = MiCatalogoPosSaleOutboxMapper.snapshot(saleId, 1250, 0, lines,
            listOf(com.example.bspos.data.micatalogo.dto.PosPaymentSplitDto("card", "12.50")))!!
        val transfer = MiCatalogoPosSaleOutboxMapper.snapshot(saleId, 1250, 0, lines,
            listOf(com.example.bspos.data.micatalogo.dto.PosPaymentSplitDto("bank_transfer", "12.50")))!!

        assertEquals("card", card.request.payments?.single()?.method)
        assertEquals("12.50", card.request.payments?.single()?.amount)
        assertEquals("bank_transfer", transfer.request.payments?.single()?.method)
        assertEquals("12.50", transfer.request.payments?.single()?.amount)
    }

    @Test
    fun rejectsLinesWithoutRemoteIdentityOrFromDifferentShops() {
        assertNull(
            MiCatalogoPosSaleOutboxMapper.snapshot(
                saleId, 500, 0,
                listOf(PosSaleOutboxLine("01JSHOP", null, 1, 500))
            )
        )
        assertNull(
            MiCatalogoPosSaleOutboxMapper.snapshot(
                saleId, 500, 0,
                listOf(
                    PosSaleOutboxLine("01JSHOPA", "01JPRODUCTA", 1, 500),
                    PosSaleOutboxLine("01JSHOPB", "01JPRODUCTB", 1, 500)
                )
            )
        )
    }

    @Test
    fun derivesPaidAndPendingPaymentStates() {
        assertEquals("paid", MiCatalogoPosSaleOutboxMapper.paymentStatus(500, 0))
        assertEquals("pending", MiCatalogoPosSaleOutboxMapper.paymentStatus(0, 500))
    }

    @Test fun preservesLineDiscountTaxAndOriginalCreditSnapshot() {
        val snapshot = MiCatalogoPosSaleOutboxMapper.snapshot(saleId, 100, 250,
            listOf(PosSaleOutboxLine("shop", "product", 2, 200, 60, 10)))!!
        assertEquals("0.60", snapshot.request.items.single().discount)
        assertEquals("0.10", snapshot.request.items.single().tax)
        assertEquals("2.50", snapshot.request.creditAmount)
        assertEquals("partial", snapshot.request.paymentStatus)
    }
}
