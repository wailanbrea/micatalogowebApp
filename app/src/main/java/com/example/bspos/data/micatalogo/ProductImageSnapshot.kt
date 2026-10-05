package com.example.bspos.data.micatalogo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

data class ProductImageBytes(val base64: String, val sha256: String)

/** Copies selected bytes now, not at retry time; strips EXIF and bounds Room row size. */
class ProductImageSnapshot @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun capture(path: String): ProductImageBytes = withContext(Dispatchers.IO) {
        fun open(): InputStream {
            val uri = Uri.parse(path)
            return when (uri.scheme) {
                "content", "file" -> checkNotNull(context.contentResolver.openInputStream(uri)) { "No se pudo abrir la foto." }
                null -> File(path).inputStream()
                else -> error("Elige una foto del dispositivo; no se descargan enlaces arbitrarios.")
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 60_000_000L) { "Foto inválida o demasiado grande." }
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 1600) sample *= 2
        var bitmap = checkNotNull(open().use { BitmapFactory.decodeStream(it, null,
            BitmapFactory.Options().apply { inSampleSize = sample }) }) { "No se pudo leer la foto." }
        try {
            val orientation = runCatching { open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            if (!matrix.isIdentity) {
                val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (oriented !== bitmap) bitmap.recycle()
                bitmap = oriented
            }
            if (bitmap.hasAlpha()) {
                val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
                Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
                bitmap.recycle()
                bitmap = opaque
            }
            var quality = 90
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "No se pudo preparar la foto." }
                    output.toByteArray()
                }
                if (bytes.size <= 512 * 1024) break
                if (quality > 50) quality -= 10
                else {
                    val smaller = Bitmap.createScaledBitmap(bitmap, maxOf(1, bitmap.width / 2), maxOf(1, bitmap.height / 2), true)
                    if (smaller !== bitmap) bitmap.recycle()
                    bitmap = smaller
                    quality = 80
                }
            } while (true)
            ProductImageBytes(Base64.encodeToString(bytes, Base64.NO_WRAP),
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
        } finally { bitmap.recycle() }
    }
}
