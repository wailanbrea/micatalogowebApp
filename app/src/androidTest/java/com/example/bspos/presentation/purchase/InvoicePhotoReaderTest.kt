package com.example.bspos.presentation.purchase

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class InvoicePhotoReaderTest {
    @Test fun invalidLocalPhotoIsRejectedBeforeOcrWithoutCreatingAnInvoice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "invalid-invoice-${UUID.randomUUID()}.png")
        file.writeText("QA: this is not an image")
        val result = runCatching { InvoicePhotoReader.read(context, Uri.fromFile(file)) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("formato legible"))
    }
    @Test fun bundledReaderRecognizesALocalSyntheticInvoiceWithoutUploadingIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f }
        canvas.drawText("FACTURA DE PRUEBA", 40f, 110f, paint)
        canvas.drawText("SKU-100  5  120.00", 40f, 230f, paint)
        val file = File(context.cacheDir, "invoice-ocr-${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val text = InvoicePhotoReader.read(context, Uri.fromFile(file))
        assertTrue(text.contains("FACTURA", ignoreCase = true))
        assertTrue(text.contains("SKU-100", ignoreCase = true))
        assertTrue(text.contains("120.00"))
    }
}
