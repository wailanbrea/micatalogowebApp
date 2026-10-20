package com.example.bspos.presentation.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.bspos.R
import com.example.bspos.data.micatalogo.dto.FeatureSectionDto
import com.example.bspos.data.micatalogo.dto.OperationalReportContextDto
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class OperationalReportSnapshot(
    val title: String,
    val context: OperationalReportContextDto?,
    val section: FeatureSectionDto,
    val query: String = ""
)

/** Native, searchable A4 PDF. Every measured line is preserved across page breaks. */
object OperationalReportPdf {
    fun create(context: Context, snapshot: OperationalReportSnapshot): File {
        val file = File(context.cacheDir, "reporte-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()
        try {
            val writer = Writer(context, document, snapshot)
            writer.startPage()
            snapshot.section.kpis.forEach { metric ->
                writer.block(metric.label, size = 10f, muted = true)
                writer.block(metric.value, size = 17f, bold = true)
                writer.gap(9f)
            }
            if (snapshot.query.isNotBlank()) {
                writer.block(context.getString(R.string.reports_filter, snapshot.query), size = 10f)
                writer.block(context.getString(R.string.reports_total_note), size = 9f, muted = true)
            }
            writer.gap(12f)
            writer.block(context.getString(R.string.reports_detail), size = 14f, bold = true)
            writer.block(context.resources.getQuantityString(R.plurals.reports_records, snapshot.section.rows.size, snapshot.section.rows.size), size = 9f, muted = true)
            writer.gap(10f)
            if (snapshot.section.rows.isEmpty()) writer.block(context.getString(R.string.reports_empty))
            snapshot.section.rows.forEach { row ->
                writer.keepTogether(72f)
                writer.rule()
                writer.block(row.primary, bold = true)
                if (row.secondary.isNotBlank()) writer.block(row.secondary, size = 10f, muted = true)
                if (row.value.isNotBlank()) writer.block(row.value, size = 12f, bold = true)
                if (row.status.isNotBlank()) writer.block(row.status, size = 9f, muted = true)
                writer.gap(12f)
            }
            snapshot.section.note?.takeIf { it.isNotBlank() }?.let {
                writer.gap(12f)
                writer.block(context.getString(R.string.reports_info), bold = true)
                writer.block(it, size = 9f, muted = true)
            }
            writer.finish()
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        return file
    }

    private class Writer(val context: Context, val document: PdfDocument, val snapshot: OperationalReportSnapshot) {
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var number = 0
        private var y = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ink = 0xff0f172a.toInt()
        private val mutedInk = 0xff64748b.toInt()
        fun startPage() {
            number++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
            canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            paint.color = 0xff1e60f4.toInt()
            canvas.drawRect(0f, 0f, 595f, 6f, paint)
            y = 42f
            block(snapshot.context?.shopName?.ifBlank { snapshot.title } ?: snapshot.title, 16f, bold = true)
            block("${snapshot.title} / ${snapshot.section.label}", 11f, muted = true)
            block(snapshot.section.scope, 9f, muted = true)
            snapshot.context?.let { metadata ->
                val generated = runCatching {
                    OffsetDateTime.parse(metadata.generatedAt).atZoneSameInstant(ZoneId.of(metadata.timezone))
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z", Locale("es")))
                }.getOrDefault(metadata.generatedAt)
                if (generated.isNotBlank()) block(context.getString(R.string.reports_generated, generated), 8f, muted = true)
            }
            gap(14f)
        }
        fun finish() {
            paint.apply { color = mutedInk; textSize = 8f; typeface = Typeface.DEFAULT }
            canvas.drawText("MiCatalogo", 40f, 808f, paint)
            val pageLabel = context.getString(R.string.reports_page, number)
            canvas.drawText(pageLabel, 555f - paint.measureText(pageLabel), 808f, paint)
            document.finishPage(page)
        }
        fun gap(height: Float) { y += height }
        fun keepTogether(height: Float) { if (y + height > 765f) { finish(); startPage() } }
        fun rule() {
            keepTogether(20f)
            paint.color = 0xffe2e8f0.toInt()
            canvas.drawRect(40f, y, 555f, y + 1f, paint)
            y += 15f
        }
        fun block(value: String, size: Float = 11f, bold: Boolean = false, muted: Boolean = false) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (muted) mutedInk else ink
                textSize = size
                typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            }
            value.replace('\t', ' ').split('\n').forEach { paragraph ->
                var remaining = paragraph.trim()
                if (remaining.isEmpty()) gap(size * 1.35f)
                while (remaining.isNotEmpty()) {
                    var count = textPaint.breakText(remaining, true, 515f, null).coerceAtLeast(1)
                    if (count < remaining.length) {
                        val space = remaining.lastIndexOf(' ', count - 1)
                        if (space > 0) count = space
                    }
                    val metrics = textPaint.fontMetrics
                    val height = metrics.descent - metrics.ascent + 4f
                    keepTogether(height)
                    canvas.drawText(remaining.substring(0, count), 40f, y - metrics.ascent, textPaint)
                    y += height
                    remaining = remaining.substring(count).trimStart()
                }
            }
        }
    }
}
