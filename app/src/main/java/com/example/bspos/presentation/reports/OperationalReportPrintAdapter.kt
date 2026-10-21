package com.example.bspos.presentation.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.print.pdf.PrintedPdfDocument
import com.example.bspos.R
import java.io.File
import java.io.FileOutputStream

/** Prints requested pages only, with real page count and cancellation support. */
class OperationalReportPrintAdapter(private val context: Context, private val file: File) : PrintDocumentAdapter() {
    @Volatile private var layoutAttributes = normalizedAttributes(null)

    private fun normalizedAttributes(attributes: PrintAttributes?): PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(attributes?.mediaSize ?: PrintAttributes.MediaSize.ISO_A4)
        .setResolution(attributes?.resolution ?: PrintAttributes.Resolution("reports", context.getString(R.string.reports_print), 300, 300))
        .setMinMargins(attributes?.minMargins ?: PrintAttributes.Margins.NO_MARGINS)
        .setColorMode(attributes?.colorMode?.takeIf { it != 0 } ?: PrintAttributes.COLOR_MODE_COLOR)
        .build()

    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        try {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                layoutAttributes = normalizedAttributes(newAttributes)
                callback.onLayoutFinished(PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(renderer.pageCount).build(), oldAttributes != newAttributes)
            }
        } catch (_: Exception) { callback.onLayoutFailed(context.getString(R.string.reports_print_error)) }
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
        try {
            callback.onWriteFinished(writeSelectedPages(pages, destination, cancellationSignal))
        } catch (_: android.os.OperationCanceledException) { callback.onWriteCancelled() }
        catch (_: Exception) { callback.onWriteFailed(context.getString(R.string.reports_print_error)) }
    }

    internal fun writeSelectedPages(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal): Array<PageRange> {
            val printed = mutableListOf<PageRange>()
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                val output = PrintedPdfDocument(context, layoutAttributes)
                try {
                    for (index in 0 until renderer.pageCount) {
                        cancellationSignal.throwIfCanceled()
                        if (pages.none { index >= it.start && index <= it.end }) continue
                        renderer.openPage(index).use { source ->
                            val bitmap = Bitmap.createBitmap(source.width * 3, source.height * 3, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val page = output.startPage(printed.size)
                                val area = RectF(page.info.contentRect)
                                val scale = minOf(area.width() / source.width, area.height() / source.height)
                                val width = source.width * scale
                                val height = source.height * scale
                                val target = RectF(area.centerX() - width / 2f, area.centerY() - height / 2f,
                                    area.centerX() + width / 2f, area.centerY() + height / 2f)
                                page.canvas.drawBitmap(bitmap, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
                                output.finishPage(page)
                            } finally { bitmap.recycle() }
                        }
                        printed.add(PageRange(index, index))
                    }
                    cancellationSignal.throwIfCanceled()
                    if (printed.isNotEmpty()) FileOutputStream(destination.fileDescriptor).use(output::writeTo)
                } finally { output.close() }
            }
            return printed.toTypedArray()
    }
}
