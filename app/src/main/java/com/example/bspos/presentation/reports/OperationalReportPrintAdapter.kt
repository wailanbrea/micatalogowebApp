package com.example.bspos.presentation.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import com.example.bspos.R
import java.io.File
import java.io.FileOutputStream

/** Prints requested pages only, with real page count and cancellation support. */
class OperationalReportPrintAdapter(private val context: Context, private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        try {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
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
                val output = PdfDocument()
                try {
                    for (index in 0 until renderer.pageCount) {
                        cancellationSignal.throwIfCanceled()
                        if (pages.none { index >= it.start && index <= it.end }) continue
                        renderer.openPage(index).use { source ->
                            val bitmap = Bitmap.createBitmap(source.width * 3, source.height * 3, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                val page = output.startPage(PdfDocument.PageInfo.Builder(source.width, source.height, printed.size + 1).create())
                                page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), null)
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
