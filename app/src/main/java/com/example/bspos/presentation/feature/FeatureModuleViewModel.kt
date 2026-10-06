package com.example.bspos.presentation.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.FeatureResponseDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeatureModuleUiState(
    val loading: Boolean = true,
    val response: FeatureResponseDto? = null,
    val error: String? = null
)

@HiltViewModel
class FeatureModuleViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository
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
}
