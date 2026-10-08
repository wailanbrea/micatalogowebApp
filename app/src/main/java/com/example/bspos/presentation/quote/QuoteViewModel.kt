package com.example.bspos.presentation.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import com.example.bspos.data.micatalogo.dto.FeatureProductDto
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
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
    val kpis: List<FeatureKpiDto> = emptyList(),
    val rows: List<FeatureRowDto> = emptyList(),
    val customers: List<RemoteCustomerDto> = emptyList(),
    val customersLoading: Boolean = false,
    val customerError: String? = null,
    val cart: Map<FeatureProductDto, Int> = emptyMap(),
    val convertingId: String? = null,
    val convertedInvoiceNumber: String? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class QuoteViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository,
    private val customerApi: Lazy<MiCatalogoCustomerApi>
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
            _state.value = _state.value.copy(customersLoading = true, customerError = null)
            viewModelScope.launch {
                runCatching {
                    val response = customerApi.get().customers(shopId)
                    check(response.isSuccessful) { "No se pudieron cargar los clientes. Reabre Cotizaciones para reintentar." }
                    checkNotNull(response.body()).customers.filter { it.isActive != false }
                }.onSuccess { clients -> _state.value = _state.value.copy(customers = clients, customersLoading = false) }
                    .onFailure { _state.value = _state.value.copy(customersLoading = false, customerError = it.message) }
            }
            runCatching {
                val response = api.get().feature(shopId, "quotes")
                check(response.isSuccessful) { "No se pudo cargar las cotizaciones." }
                checkNotNull(response.body()?.module) { "Respuesta vacía del servidor." }
            }
                .onSuccess { module ->
                    _state.value = _state.value.copy(
                        loading = false,
                        products = module.quoteProducts,
                        kpis = module.kpis,
                        rows = module.rows,
                        error = null
                    )
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "No se pudo cargar las cotizaciones.") }
        }
    }

    fun add(product: FeatureProductDto) {
        if (_state.value.saving) return
        val current = _state.value.cart[product] ?: 0
        // A quote does not reserve or deduct inventory. Products with zero stock
        // can still be quoted, but conversion will validate availability later.
        val max = 100000
        if (current >= max) return
        _state.value = _state.value.copy(cart = _state.value.cart + (product to current + 1), error = null, message = null)
    }

    fun remove(product: FeatureProductDto) {
        if (_state.value.saving) return
        val next = (_state.value.cart[product] ?: 0) - 1
        _state.value = _state.value.copy(cart = if (next > 0) _state.value.cart + (product to next) else _state.value.cart - product)
    }

    fun clearCart() {
        if (!_state.value.saving) _state.value = _state.value.copy(cart = emptyMap(), error = null, message = null)
    }

    fun save(customerName: String, customerPhone: String, validUntil: String, notes: String, customerId: String? = null) {
        val current = _state.value
        if (current.cart.isEmpty() || current.saving) return
        _state.value = current.copy(saving = true, error = null, message = null, convertedInvoiceNumber = null)
        viewModelScope.launch {
            runCatching {
                val shopId = checkNotNull(connection.activeShopId()) { "Selecciona una tienda para cotizar." }
                val selected = customerId?.let { id ->
                    checkNotNull(current.customers.firstOrNull { it.id == id && it.isActive != false }) { "Selecciona un cliente disponible de esta tienda." }
                }
                val response = api.get().createQuote(shopId, QuoteCreateRequestDto(
                    customerId = selected?.id,
                    customerName = selected?.displayName() ?: customerName.trim().ifBlank { null },
                    customerPhone = if (selected != null) selected.phone else customerPhone.trim().ifBlank { null },
                    validUntil = validUntil.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    items = current.cart.map { (product, quantity) -> QuoteItemRequestDto(product.id, quantity, product.price) }
                ))
                check(response.isSuccessful) { "No se pudo guardar la cotización. Revisa los datos y vuelve a intentar." }
                response
            }.onSuccess { response ->
                if (!response.isSuccessful) throw IllegalStateException("No se pudo guardar la cotización.")
                _state.value = _state.value.copy(saving = false, cart = emptyMap(), message = response.body()?.message ?: "Cotización guardada.")
                load()
            }.onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "No se pudo guardar la cotización.") }
        }
    }

    fun convert(quoteId: String) {
        if (_state.value.convertingId != null) return
        _state.value = _state.value.copy(convertingId = quoteId, error = null, message = null)
        viewModelScope.launch {
            runCatching {
                val shopId = checkNotNull(connection.activeShopId()) { "Selecciona una tienda para convertir." }
                val response = api.get().convertQuote(shopId, quoteId)
                check(response.isSuccessful) { "No se pudo convertir la cotización. Revisa las existencias." }
                response
            }
                .onSuccess { response ->
                    if (!response.isSuccessful) throw IllegalStateException("No se pudo convertir la cotización.")
                    _state.value = _state.value.copy(
                        convertingId = null,
                        convertedInvoiceNumber = response.body()?.invoiceNumber,
                        message = response.body()?.message ?: "Cotización convertida en venta."
                    )
                    load()
                }
                .onFailure { _state.value = _state.value.copy(convertingId = null, error = it.message ?: "No se pudo convertir la cotización.") }
        }
    }
}

internal fun RemoteCustomerDto.displayName(): String = name?.takeIf(String::isNotBlank)
    ?: listOfNotNull(firstName, lastName).joinToString(" ").trim().ifBlank { "Cliente" }
