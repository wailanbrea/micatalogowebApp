package com.example.bspos.presentation.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.FeatureProductDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.QuoteCreateRequestDto
import com.example.bspos.data.micatalogo.dto.QuoteItemRequestDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuoteUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val products: List<FeatureProductDto> = emptyList(),
    val rows: List<FeatureRowDto> = emptyList(),
    val cart: Map<FeatureProductDto, Int> = emptyMap(),
    val convertingId: String? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class QuoteViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(QuoteUiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val shopId = connection.activeShopId()
            if (shopId.isNullOrBlank()) {
                _state.value = QuoteUiState(loading = false, error = "Selecciona una tienda activa para cotizar.")
                return@launch
            }
            runCatching { api.get().feature(shopId, "quotes") }
                .onSuccess { response ->
                    if (!response.isSuccessful) throw IllegalStateException("No se pudo cargar las cotizaciones.")
                    val module = response.body()?.module ?: throw IllegalStateException("Respuesta vacía del servidor.")
                    _state.value = _state.value.copy(loading = false, products = module.quoteProducts, rows = module.rows, error = null)
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "No se pudo cargar las cotizaciones.") }
        }
    }

    fun add(product: FeatureProductDto) {
        val current = _state.value.cart[product] ?: 0
        val max = product.stock ?: Int.MAX_VALUE
        if (current >= max) return
        _state.value = _state.value.copy(cart = _state.value.cart + (product to current + 1), error = null, message = null)
    }

    fun remove(product: FeatureProductDto) {
        val next = (_state.value.cart[product] ?: 0) - 1
        _state.value = _state.value.copy(cart = if (next > 0) _state.value.cart + (product to next) else _state.value.cart - product)
    }

    fun save(customerName: String, customerPhone: String, notes: String) {
        val current = _state.value
        if (current.cart.isEmpty() || current.saving) return
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = current.copy(saving = true, error = null, message = null)
            runCatching {
                api.get().createQuote(shopId, QuoteCreateRequestDto(
                    customerName = customerName.trim().ifBlank { null },
                    customerPhone = customerPhone.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    items = current.cart.map { (product, quantity) -> QuoteItemRequestDto(product.id, quantity, product.price) }
                ))
            }.onSuccess { response ->
                if (!response.isSuccessful) throw IllegalStateException("No se pudo guardar la cotización.")
                _state.value = _state.value.copy(saving = false, cart = emptyMap(), message = response.body()?.message ?: "Cotización guardada.")
                load()
            }.onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "No se pudo guardar la cotización.") }
        }
    }

    fun convert(quoteId: String) {
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(convertingId = quoteId, error = null, message = null)
            runCatching { api.get().convertQuote(shopId, quoteId) }
                .onSuccess { response ->
                    if (!response.isSuccessful) throw IllegalStateException("No se pudo convertir la cotización.")
                    _state.value = _state.value.copy(convertingId = null, message = response.body()?.message ?: "Cotización convertida en venta.")
                    load()
                }
                .onFailure { _state.value = _state.value.copy(convertingId = null, error = it.message ?: "No se pudo convertir la cotización.") }
        }
    }
}
