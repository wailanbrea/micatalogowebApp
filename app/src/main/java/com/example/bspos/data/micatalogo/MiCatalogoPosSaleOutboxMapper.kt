package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.PosPaymentSplitDto
import com.example.bspos.data.micatalogo.dto.PosSaleUploadItemDto
import com.example.bspos.data.micatalogo.dto.PosSaleUploadRequestDto
import java.math.BigDecimal
import java.util.UUID

data class PosSaleOutboxLine(
    val remoteShopId: String?,
    val remoteProductId: String?,
    val quantity: Long,
    val unitPriceCents: Long,
    val discountCents: Long = 0,
    val taxCents: Long = 0,
    val expectedSaleUnit: String? = null,
    val expectedVolumeMl: Int? = null,
    val expectedSourceProductId: String? = null
)

data class PosSaleOutboxSnapshot(
    val remoteShopId: String,
    val request: PosSaleUploadRequestDto
)

object MiCatalogoPosSaleOutboxMapper {
    fun snapshot(
        saleId: UUID,
        paidAmount: Long,
        pendingAmount: Long,
        lines: List<PosSaleOutboxLine>,
        payments: List<PosPaymentSplitDto>? = null,
        dueDate: String? = null,
        saleMode: String = "retail"
    ): PosSaleOutboxSnapshot? {
        if (lines.isEmpty()) return null
        val shopIds = lines.mapNotNull { it.remoteShopId?.trim()?.takeIf(String::isNotBlank) }.distinct()
        if (shopIds.size != 1 || lines.any { it.remoteShopId.isNullOrBlank() || it.remoteProductId.isNullOrBlank() || it.quantity !in 1..Int.MAX_VALUE || it.unitPriceCents < 0 }) return null

        return PosSaleOutboxSnapshot(
            remoteShopId = shopIds.single(),
            request = PosSaleUploadRequestDto(
                clientSaleUuid = saleId.toString(),
                paymentStatus = paymentStatus(paidAmount, pendingAmount),
                saleMode = saleMode,
                creditAmount = pendingAmount.takeIf { it > 0 }?.let(::decimalPrice),
                items = lines.map {
                    PosSaleUploadItemDto(
                        productId = it.remoteProductId!!.trim(),
                        quantity = it.quantity.toInt(),
                        unitPrice = decimalPrice(it.unitPriceCents),
                        discount = decimalPrice(it.discountCents),
                        tax = decimalPrice(it.taxCents),
                        expectedSaleUnit = it.expectedSaleUnit,
                        expectedVolumeMl = it.expectedVolumeMl,
                        expectedSourceProductId = it.expectedSourceProductId
                    )
                },
                payments = payments,
                dueDate = dueDate
            )
        )
    }

    fun paymentStatus(paidAmount: Long, pendingAmount: Long): String = when {
        pendingAmount == 0L -> "paid"
        paidAmount == 0L -> "pending"
        else -> "partial"
    }

    fun decimalPrice(cents: Long): String = BigDecimal.valueOf(cents, 2).setScale(2).toPlainString()
}
