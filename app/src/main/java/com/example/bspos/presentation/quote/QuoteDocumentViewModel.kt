package com.example.bspos.presentation.quote

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.presentation.common.UiErrorBus
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QuoteDocumentViewModel @Inject constructor(private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository, @param:ApplicationContext private val context: Context) : ViewModel() {
    private var downloading = false
    fun download(id: String, name: String) {
        if (downloading) return
        downloading = true
        viewModelScope.launch {
            try {
                val shop = connection.activeShopId() ?: error("Selecciona una tienda.")
                val response = api.get().quotePdf(shop, id)
                check(response.isSuccessful) { "No se pudo descargar el PDF (${response.code()})." }
                val body = response.body() ?: error("Documento vacío.")
                val filename = name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80) + ".pdf"
                withContext(Dispatchers.IO) {
                    body.use {
                        check(body.contentType()?.toString()?.startsWith("application/pdf") == true) { "El servidor no devolvió un PDF." }
                        if (Build.VERSION.SDK_INT >= 29) {
                            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, filename); put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); put(MediaStore.Downloads.IS_PENDING, 1) }
                            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("No se pudo crear la descarga.")
                            context.contentResolver.openOutputStream(uri)?.use { output -> body.byteStream().use { input -> input.copyTo(output) } } ?: error("No se pudo escribir el PDF.")
                            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); context.contentResolver.update(uri, values, null, null)
                        } else {
                            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("Almacenamiento no disponible.")
                            val target = java.io.File(directory, filename)
                            check(!target.exists()) { "Ya existe un PDF con ese nombre." }
                            target.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
                        }
                    }
                }
                UiErrorBus.show("PDF guardado: $filename")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { UiErrorBus.show(error.message ?: "No se pudo descargar el documento.") }
            finally { downloading = false }
        }
    }
}
