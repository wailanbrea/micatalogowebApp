package com.example.bspos.presentation.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.PurchaseCreateRequestDto
import com.example.bspos.data.micatalogo.dto.PurchaseItemRequestDto
import com.example.bspos.data.micatalogo.dto.PurchaseWorkspaceDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseLineDraft(
    val productId: String = "",
    val quantity: String = "1",
    val unitCost: String = ""
)

data class PurchaseModuleUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val workspace: PurchaseWorkspaceDto? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class PurchaseModuleViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(PurchaseModuleUiState())
    val state: StateFlow<PurchaseModuleUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val shopId = connection.activeShopId()
            if (shopId.isNullOrBlank()) {
                _state.value = PurchaseModuleUiState(loading = false, error = "Selecciona una tienda activa para gestionar Compras.")
                return@launch
            }
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val response = api.get().purchases(shopId)
                if (!response.isSuccessful) error(response.errorBody()?.string().orEmpty().ifBlank { "No se pudo cargar Compras." })
                response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
            }.onSuccess { workspace ->
                _state.value = _state.value.copy(loading = false, workspace = workspace, error = null)
            }.onFailure { failure ->
                _state.value = _state.value.copy(loading = false, error = failure.message ?: "No se pudo cargar Compras.")
            }
        }
    }

    fun create(
        type: String,
        documentNumber: String,
        supplierId: String?,
        mode: String,
        notes: String?,
        lines: List<PurchaseLineDraft>
    ) {
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(saving = true, error = null, message = null)
            val request = PurchaseCreateRequestDto(
                type = type,
                documentNumber = documentNumber.trim(),
                supplierId = supplierId,
                mode = mode,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                items = lines.map { line ->
                    PurchaseItemRequestDto(
                        productId = line.productId,
                        quantity = line.quantity.toIntOrNull() ?: 0,
                        unitCost = line.unitCost.replace(',', '.').ifBlank { "0" }
                    )
                }
            )
            runCatching {
                val response = api.get().createPurchase(shopId, request)
                if (!response.isSuccessful) error(response.errorBody()?.string().orEmpty().ifBlank { "No se pudo guardar la compra." })
                response.body()?.message ?: "Compra guardada."
            }.onSuccess { message ->
                _state.value = _state.value.copy(saving = false, message = message)
                load()
            }.onFailure { failure ->
                _state.value = _state.value.copy(saving = false, error = failure.message ?: "No se pudo guardar la compra.")
            }
        }
    }

    fun receive(documentId: String) {
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(saving = true, error = null, message = null)
            runCatching {
                val response = api.get().receivePurchase(shopId, documentId)
                if (!response.isSuccessful) error(response.errorBody()?.string().orEmpty().ifBlank { "No se pudo recibir la compra." })
                response.body()?.message ?: "Compra recibida."
            }.onSuccess { message ->
                _state.value = _state.value.copy(saving = false, message = message)
                load()
            }.onFailure { failure ->
                _state.value = _state.value.copy(saving = false, error = failure.message ?: "No se pudo recibir la compra.")
            }
        }
    }
}
