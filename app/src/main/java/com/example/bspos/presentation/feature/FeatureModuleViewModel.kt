package com.example.bspos.presentation.feature

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.FeatureResponseDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.presentation.common.UiErrorBus
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class FeatureModuleUiState(
    val loading: Boolean = true,
    val response: FeatureResponseDto? = null,
    val error: String? = null
)

@HiltViewModel
class FeatureModuleViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(FeatureModuleUiState())
    val state: StateFlow<FeatureModuleUiState> = _state.asStateFlow()

    fun load(feature: String) {
        viewModelScope.launch {
            _state.value = FeatureModuleUiState(loading = true)
            val shopId = connection.activeShopId()
            if (shopId.isNullOrBlank()) {
                _state.value = FeatureModuleUiState(loading = false, error = "Selecciona una tienda activa para consultar este módulo.")
                return@launch
            }
            runCatching {
                val response = api.get().feature(shopId, feature)
                if (!response.isSuccessful) error(response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: "No se pudo cargar el módulo.")
                response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
            }.onSuccess { _state.value = FeatureModuleUiState(loading = false, response = it) }
                .onFailure { _state.value = FeatureModuleUiState(loading = false, error = it.message ?: "No se pudo cargar el módulo.") }
        }
    }

    fun exportReport(feature: String, format: String) {
        if (feature != "reports" || format !in setOf("csv", "xlsx")) return
        viewModelScope.launch {
            runCatching {
                val shopId = connection.activeShopId().orEmpty()
                check(shopId.isNotBlank()) { "Selecciona una tienda activa para exportar el reporte." }
                val response = withContext(Dispatchers.IO) { api.get().exportReport(shopId, format) }
                check(response.isSuccessful) { "No se pudo exportar el reporte." }
                val body = response.body() ?: error("La exportación llegó vacía.")
                saveExport(body, format)
            }.onSuccess { fileName ->
                UiErrorBus.show("Reporte guardado: $fileName")
            }.onFailure { error ->
                UiErrorBus.show(error.message ?: "No se pudo guardar el reporte.")
            }
        }
    }

    private suspend fun saveExport(body: okhttp3.ResponseBody, format: String): String = withContext(Dispatchers.IO) {
        val extension = if (format == "xlsx") "xlsx" else "csv"
        val mime = if (extension == "xlsx") {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        } else {
            "text/csv"
        }
        val displayName = "micatalogo-reportes-${System.currentTimeMillis()}.$extension"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("No se pudo abrir la carpeta Descargas.")
            try {
                context.contentResolver.openOutputStream(uri)?.use { output -> body.byteStream().use { it.copyTo(output) } }
                    ?: error("No se pudo guardar el archivo exportado.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            } finally {
                body.close()
            }
        } else {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val file = File(directory, displayName)
            file.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
            body.close()
        }
        displayName
    }
}
