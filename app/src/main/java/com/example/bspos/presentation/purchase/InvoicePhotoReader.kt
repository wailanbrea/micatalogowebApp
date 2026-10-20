package com.example.bspos.presentation.purchase

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bundled OCR; image bytes stay on this device. No invoice is submitted here. */
internal object InvoicePhotoReader {
    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                require(output.size() + count <= 10 * 1024 * 1024) { "La foto supera 10 MB." }
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
            output.toByteArray()
        }
            ?: error("No se pudo abrir la foto.")
        require(bytes.size <= 10 * 1024 * 1024) { "La foto supera 10 MB." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La foto no tiene un formato legible." }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2048) inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("No se pudo leer la foto.")
        val rotation = runCatching {
            bytes.inputStream().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL).let {
            when (it) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, rotation))
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        val text = result.text.trim()
                        if (text.length > 50000) continuation.resumeWithException(IllegalArgumentException("La foto contiene demasiado texto. Divide la factura en varias fotos."))
                        else if (text.isEmpty()) continuation.resumeWithException(IllegalArgumentException("No se encontró texto. Usa una foto nítida y bien iluminada."))
                        else continuation.resume(text)
                    }
                }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                .addOnCompleteListener { recognizer.close(); bitmap.recycle() }
        }
    }
}
