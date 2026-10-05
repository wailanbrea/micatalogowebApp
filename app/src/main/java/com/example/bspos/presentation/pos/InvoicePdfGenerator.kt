package com.example.bspos.presentation.pos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.bspos.R
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.InvoiceConfig
import com.example.bspos.domain.model.Sale
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdfGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f
    private const val PRIMARY = 0xff0875be.toInt()
    private const val NAVY = 0xff06477d.toInt()
    private const val INK = 0xff1d2f3d.toInt()
    private const val MUTED = 0xff657785.toInt()
    private const val LIGHT_BLUE = 0xffedf6fc.toInt()
    private const val LIGHT_GRAY = 0xfff5f7f9.toInt()
    private const val SUCCESS = 0xff21865b.toInt()
    private const val SUCCESS_LIGHT = 0xffe7f6ee.toInt()
    private const val WARNING = 0xffa56a00.toInt()
    private const val WARNING_LIGHT = 0xfffff3d8.toInt()

    fun create(context: Context, sale: Sale, lines: List<PosCartLine>, currency: CurrencyUnit = CurrencyUnit.DOP, config: InvoiceConfig = InvoiceConfig()) = run {
        val safeInvoice = sale.invoiceNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(context.cacheDir, "factura-$safeInvoice-${System.currentTimeMillis()}.pdf")
        val document = PdfDocument()
        val regular = Typeface.create("sans-serif", Typeface.NORMAL)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val bold = Typeface.create("sans-serif", Typeface.BOLD)
        var pageNumber = 1
        var page = document.startPage(pageInfo(pageNumber))
        var canvas = page.canvas
        drawPageHeader(context, canvas, sale, pageNumber, regular, medium, bold, config)
        drawTableHeader(canvas, regular, bold)
        var y = 282f

        lines.forEach { line ->
            val name = line.product.name
            val rowHeight = if (name.length > 34) 42f else 30f
            if (y + rowHeight > 700f) {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(pageInfo(pageNumber))
                canvas = page.canvas
                drawPageHeader(context, canvas, sale, pageNumber, regular, medium, bold, config)
                drawTableHeader(canvas, regular, bold)
                y = 282f
            }
            if ((pageNumber + lines.indexOf(line)) % 2 == 0) {
                val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIGHT_GRAY }
                canvas.drawRect(MARGIN, y - 20f, PAGE_WIDTH - MARGIN, y + rowHeight - 8f, rowPaint)
            }
            val nameLines = wrap(name, 34)
            text(canvas, nameLines.first(), MARGIN + 10f, y, 10.5f, INK, regular)
            if (nameLines.size > 1) text(canvas, nameLines[1], MARGIN + 10f, y + 14f, 9f, MUTED, regular)
            textRight(canvas, line.quantity.toString(), 384f, y, 10.5f, INK, medium)
            textRight(canvas, money(line.product.salePrice, currency), 470f, y, 10.5f, INK, regular)
            textRight(canvas, money(line.quantity * line.product.salePrice, currency), PAGE_WIDTH - MARGIN - 10f, y, 10.5f, INK, medium)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffe5ebf0.toInt(); strokeWidth = 1f }
            canvas.drawLine(MARGIN, y + rowHeight - 9f, PAGE_WIDTH - MARGIN, y + rowHeight - 9f, linePaint)
            y += rowHeight
        }

        if (y > 615f) {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(pageInfo(pageNumber))
            canvas = page.canvas
            drawPageHeader(context, canvas, sale, pageNumber, regular, medium, bold, config)
            y = 282f
        }
        drawSummary(canvas, sale, y + 20f, regular, medium, bold, currency)
        drawFooter(canvas, regular, config)
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun pageInfo(number: Int) = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()

    private fun drawPageHeader(context: Context, canvas: Canvas, sale: Sale, pageNumber: Int, regular: Typeface, medium: Typeface, bold: Typeface, config: InvoiceConfig) {
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        canvas.drawColor(android.graphics.Color.WHITE)
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PRIMARY }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 8f, accent)
        ContextCompat.getDrawable(context, R.drawable.ic_bs_logo)?.let { logo ->
            logo.setBounds(MARGIN.toInt(), 28, (MARGIN + 68).toInt(), 96)
            logo.draw(canvas)
        }
        text(canvas, config.businessName.ifBlank { "MiCatalogo" }, 122f, 55f, 22f, NAVY, bold)
        text(canvas, "Tu negocio, más lejos", 122f, 76f, 10f, MUTED, regular)
        val businessDetails = listOfNotNull(
            config.taxId.takeIf { it.isNotBlank() }?.let { "RNC / ID: $it" },
            config.phone.takeIf { it.isNotBlank() }?.let { "Tel: $it" },
            config.address.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (businessDetails.isNotBlank()) text(canvas, businessDetails.take(72), 122f, 94f, 8f, MUTED, regular)
        textRight(canvas, "FACTURA", PAGE_WIDTH - MARGIN, 48f, 22f, PRIMARY, bold)
        textRight(canvas, sale.invoiceNumber, PAGE_WIDTH - MARGIN, 71f, 11f, INK, medium)
        textRight(canvas, "Página $pageNumber", PAGE_WIDTH - MARGIN, 91f, 8.5f, MUTED, regular)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffdce6ed.toInt(); strokeWidth = 1f }
        canvas.drawLine(MARGIN, 122f, PAGE_WIDTH - MARGIN, 122f, linePaint)
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date.from(sale.date))
        drawInfoBox(canvas, MARGIN, 144f, 225f, "FECHA", date, regular, medium)
        drawInfoBox(canvas, 252f, 144f, 225f, "MÉTODO DE PAGO", paymentLabel(sale), regular, medium)
        val statusPaid = sale.pendingAmount == 0L
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (statusPaid) SUCCESS_LIGHT else WARNING_LIGHT }
        canvas.drawRoundRect(487f, 144f, 553f, 177f, 8f, 8f, statusPaint)
        textCentered(canvas, if (statusPaid) "PAGADA" else "PENDIENTE", 520f, 165f, 8.5f, if (statusPaid) SUCCESS else WARNING, medium)
        text(canvas, "CLIENTE", MARGIN, 208f, 8.5f, MUTED, medium)
        text(canvas, "Consumidor final", MARGIN, 226f, 11f, INK, regular)
    }

    private fun drawInfoBox(canvas: Canvas, left: Float, top: Float, width: Float, label: String, value: String, regular: Typeface, medium: Typeface) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIGHT_BLUE }
        canvas.drawRoundRect(left, top, left + width, top + 33f, 8f, 8f, fill)
        text(canvas, label, left + 10f, top + 13f, 7.5f, MUTED, medium)
        text(canvas, value, left + 10f, top + 26f, 9.5f, INK, regular)
    }

    private fun drawTableHeader(canvas: Canvas, regular: Typeface, bold: Typeface) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY }
        canvas.drawRoundRect(MARGIN, 246f, PAGE_WIDTH - MARGIN, 274f, 7f, 7f, fill)
        text(canvas, "PRODUCTO", MARGIN + 10f, 264f, 8.5f, android.graphics.Color.WHITE, bold)
        textRight(canvas, "CANT.", 384f, 264f, 8.5f, android.graphics.Color.WHITE, bold)
        textRight(canvas, "P. UNITARIO", 470f, 264f, 8.5f, android.graphics.Color.WHITE, bold)
        textRight(canvas, "IMPORTE", PAGE_WIDTH - MARGIN - 10f, 264f, 8.5f, android.graphics.Color.WHITE, bold)
    }

    private fun drawSummary(canvas: Canvas, sale: Sale, top: Float, regular: Typeface, medium: Typeface, bold: Typeface, currency: CurrencyUnit) {
        val left = 330f
        val right = PAGE_WIDTH - MARGIN
        val bottom = top + 142f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIGHT_BLUE }
        canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, fill)
        text(canvas, "RESUMEN", left + 14f, top + 24f, 10f, NAVY, bold)
        summaryLine(canvas, "Subtotal", money(sale.subtotal, currency), left + 14f, top + 48f, right - 14f, regular, medium)
        if (sale.discount > 0) summaryLine(canvas, "Descuento", "-${money(sale.discount, currency)}", left + 14f, top + 68f, right - 14f, regular, medium)
        if (sale.tax > 0) summaryLine(canvas, "Impuestos", money(sale.tax, currency), left + 14f, top + 88f, right - 14f, regular, medium)
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffc8dce9.toInt(); strokeWidth = 1f }
        canvas.drawLine(left + 14f, top + 98f, right - 14f, top + 98f, divider)
        summaryLine(canvas, "TOTAL", money(sale.total, currency), left + 14f, top + 122f, right - 14f, medium, bold, 13f, NAVY)
    }

    private fun summaryLine(canvas: Canvas, label: String, value: String, left: Float, y: Float, right: Float, labelTypeface: Typeface, valueTypeface: Typeface, size: Float = 9.5f, valueColor: Int = INK) {
        text(canvas, label, left, y, size, MUTED, labelTypeface)
        textRight(canvas, value, right, y, size, valueColor, valueTypeface)
    }

    private fun drawFooter(canvas: Canvas, regular: Typeface, config: InvoiceConfig) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffdce6ed.toInt(); strokeWidth = 1f }
        canvas.drawLine(MARGIN, 786f, PAGE_WIDTH - MARGIN, 786f, line)
        textCentered(canvas, config.footer.ifBlank { "Gracias por tu compra" }.take(90), PAGE_WIDTH / 2f, 808f, 8.5f, MUTED, regular)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; this.typeface = typeface; textAlign = Paint.Align.LEFT }
        canvas.drawText(value, x, y, paint)
    }

    private fun textRight(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; this.typeface = typeface; textAlign = Paint.Align.RIGHT }
        canvas.drawText(value, x, y, paint)
    }

    private fun textCentered(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, typeface: Typeface) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; this.typeface = typeface; textAlign = Paint.Align.CENTER }
        canvas.drawText(value, x, y, paint)
    }

    private fun wrap(value: String, maxCharacters: Int): List<String> = if (value.length <= maxCharacters) listOf(value) else listOf(value.take(maxCharacters - 1) + "…", value.drop(maxCharacters - 1).take(maxCharacters))

    private fun paymentLabel(sale: Sale): String = when (sale.paymentType.name) {
        "CASH" -> "Efectivo"
        "CARD" -> "Tarjeta"
        "TRANSFER" -> "Transferencia"
        "CREDIT" -> "Crédito"
        else -> "Mixto"
    }

    private fun money(cents: Long, currency: CurrencyUnit): String = MoneyUtils.formatCents(cents, currency)
}
