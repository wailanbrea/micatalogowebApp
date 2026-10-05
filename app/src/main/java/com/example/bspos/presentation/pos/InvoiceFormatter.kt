package com.example.bspos.presentation.pos

import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.InvoiceConfig
import com.example.bspos.domain.model.Sale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatInvoice(
    sale: Sale,
    lines: List<PosCartLine>,
    currency: CurrencyUnit = CurrencyUnit.DOP,
    config: InvoiceConfig = InvoiceConfig(),
    maxLineWidth: Int = 32
): String = buildString {
    val width = maxLineWidth.coerceIn(24, 48)
    appendLine(center(config.businessName, width))
    config.taxId.takeIf { it.isNotBlank() }?.let { appendLine(center("RNC / ID: $it", width)) }
    config.phone.takeIf { it.isNotBlank() }?.let { appendLine(center("Tel: $it", width)) }
    config.address.takeIf { it.isNotBlank() }?.let { wrap(it, width).forEach(::appendLine) }
    appendLine("Factura ${sale.invoiceNumber}".take(width))
    appendLine(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date.from(sale.date)).take(width))
    appendLine("Pago: ${sale.paymentType.invoiceLabel()}".take(width))
    appendLine("-".repeat(width))
    lines.forEach { line ->
        val subtotal = line.quantity * line.product.salePrice
        wrap("${line.quantity} x ${line.product.name}", width).forEach(::appendLine)
        appendLine("  ${MoneyUtils.formatCents(line.product.salePrice, currency)} c/u = ${MoneyUtils.formatCents(subtotal, currency)}".take(width))
    }
    appendLine("-".repeat(width))
    appendLine("Subtotal: ${MoneyUtils.formatCents(sale.subtotal, currency)}".take(width))
    if (sale.discount > 0) appendLine("Descuento: ${MoneyUtils.formatCents(sale.discount, currency)}".take(width))
    if (sale.tax > 0) appendLine("Impuesto: ${MoneyUtils.formatCents(sale.tax, currency)}".take(width))
    appendLine("TOTAL: ${MoneyUtils.formatCents(sale.total, currency)}".take(width))
    appendLine("Pagado: ${MoneyUtils.formatCents(sale.paidAmount, currency)}".take(width))
    if (sale.pendingAmount > 0) appendLine("Pendiente: ${MoneyUtils.formatCents(sale.pendingAmount, currency)}".take(width))
    appendLine()
    wrap(config.footer, width).forEach(::appendLine)
}

private fun center(value: String, width: Int): String {
    val text = value.trim().take(width)
    val left = (width - text.length) / 2
    return " ".repeat(left) + text
}

private fun wrap(value: String, width: Int): List<String> = value
    .trim()
    .chunked(width)
    .ifEmpty { listOf("") }

private fun com.example.bspos.domain.model.SalePaymentType.invoiceLabel(): String = when (this) {
    com.example.bspos.domain.model.SalePaymentType.CASH -> "Efectivo"
    com.example.bspos.domain.model.SalePaymentType.CARD -> "Tarjeta"
    com.example.bspos.domain.model.SalePaymentType.TRANSFER -> "Transferencia"
    com.example.bspos.domain.model.SalePaymentType.CREDIT -> "Crédito"
    com.example.bspos.domain.model.SalePaymentType.MIXED -> "Mixto"
}
