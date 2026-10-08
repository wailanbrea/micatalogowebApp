package com.example.bspos.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.ShopSettingsDto
import com.example.bspos.data.micatalogo.dto.ShopSettingsUpdateDto
import com.example.bspos.data.micatalogo.dto.ShopLogoUploadResponseDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.presentation.common.UiErrorBus
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import javax.inject.Inject

data class ShopSettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val settings: ShopSettingsDto? = null,
    val error: String? = null
)

@HiltViewModel
class ShopSettingsViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ShopSettingsUiState())
    val state: StateFlow<ShopSettingsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val shopId = connection.activeShopId().orEmpty()
                check(shopId.isNotBlank()) { "Selecciona una tienda activa." }
                val response = api.get().shopSettings(shopId)
                check(response.isSuccessful) { response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: "No se pudo cargar la configuración." }
                response.body() ?: error("La configuración llegó vacía.")
            }.onSuccess { _state.value = ShopSettingsUiState(loading = false, settings = it) }
                .onFailure { _state.value = ShopSettingsUiState(loading = false, error = it.message ?: "No se pudo cargar la configuración.") }
        }
    }

    fun save(settings: ShopSettingsUpdateDto) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            runCatching {
                val shopId = connection.activeShopId().orEmpty()
                check(shopId.isNotBlank()) { "Selecciona una tienda activa." }
                val response = api.get().updateShopSettings(shopId, settings)
                check(response.isSuccessful) { response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: "No se pudo guardar la configuración." }
                response.body() ?: error("La configuración guardada llegó vacía.")
            }.onSuccess {
                _state.value = ShopSettingsUiState(loading = false, saving = false, settings = it)
                UiErrorBus.show("Configuración guardada.")
            }.onFailure {
                _state.value = _state.value.copy(saving = false, error = it.message ?: "No se pudo guardar la configuración.")
                UiErrorBus.show(it.message ?: "No se pudo guardar la configuración.")
            }
        }
    }

    fun uploadLogo(fileName: String, mimeType: String?, bytes: ByteArray) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            runCatching {
                val shopId = connection.activeShopId().orEmpty()
                check(shopId.isNotBlank()) { "Selecciona una tienda activa." }
                val body = bytes.toRequestBody(mimeType?.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("logo", fileName, body)
                val response = api.get().uploadShopLogo(shopId, part)
                check(response.isSuccessful) { response.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: "No se pudo subir el logo." }
                response.body() ?: error("La respuesta del logo llegó vacía.")
            }.onSuccess { result: ShopLogoUploadResponseDto ->
                _state.value = _state.value.copy(loading = false, saving = false, settings = _state.value.settings?.copy(logoUrl = result.logoUrl))
                UiErrorBus.show("Logo actualizado.")
            }.onFailure {
                _state.value = _state.value.copy(saving = false, error = it.message ?: "No se pudo subir el logo.")
                UiErrorBus.show(it.message ?: "No se pudo subir el logo.")
            }
        }
    }
}
