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
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.*
import com.example.bspos.data.micatalogo.asRemoteCustomerOption
import javax.inject.Inject

data class QuoteUiState(
    val shopId: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val products: List<FeatureProductDto> = emptyList(),
    val kpis: List<FeatureKpiDto> = emptyList(),
    val rows: List<FeatureRowDto> = emptyList(),
    val customers: List<RemoteCustomerDto> = emptyList(),
    val customersLoading: Boolean = true,
    val customerError: String? = null,
    val cart: Map<FeatureProductDto, Int> = emptyMap(),
    val convertingId: String? = null,
    val convertedInvoiceNumber: String? = null,
    val convertedInvoiceUrl: String? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class QuoteViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository,
    private val customerApi: Lazy<MiCatalogoCustomerApi>,
    private val catalog: Lazy<MiCatalogoCatalogRepository>,
    private val durable: com.example.bspos.domain.repository.ShopMutationGateway,
    private val shops: com.example.bspos.core.database.ShopScope,
    private val events: com.example.bspos.data.micatalogo.ShopReadModelEvents,
    private val json: Json,
    customers: com.example.bspos.domain.repository.CustomerRepository
) : ViewModel() {
    private val _state = MutableStateFlow(QuoteUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var customersJob: Job? = null
    init {
        viewModelScope.launch { shops.changes.collectLatest { shop ->
            loadJob?.cancel(); customersJob?.cancel(); _state.value = QuoteUiState(shopId = shop)
            if (shop != null) load()
            customers.observeAll().collect { rows -> _state.value = _state.value.copy(customers = rows.map { it.asRemoteCustomerOption() }) }
        } }
        viewModelScope.launch { events.changes.collect { if (connection.activeShopId() == it) load() } }
    }

    fun load() {
        loadJob?.cancel(); customersJob?.cancel()
        loadJob = viewModelScope.launch {
            val shopId = connection.activeShopId()
            if (shopId.isNullOrBlank()) {
                _state.value = QuoteUiState(loading = false, error = "Selecciona una tienda activa para cotizar.")
                return@launch
            }
            _state.value = _state.value.copy(customersLoading = true, customerError = null)
            customersJob = viewModelScope.launch {
                // Publish customers created locally before fetching the server
                // list so they can be selected in a new quote immediately.
                val result = catalog.get().syncCustomers(shopId)
                if (connection.activeShopId() == shopId) _state.value = _state.value.copy(customersLoading = false,
                    customerError = (result as? com.example.bspos.domain.model.MiCatalogoResult.Failure)?.message)
            }
            runCatching {
                val response = api.get().feature(shopId, "quotes")
                check(response.isSuccessful) { "No se pudo cargar las cotizaciones." }
                checkNotNull(response.body()?.module) { "Respuesta vacía del servidor." }
            }
                .onSuccess { module ->
                    if (connection.activeShopId() != shopId) return@onSuccess
                    _state.value = _state.value.copy(
                        shopId = shopId,
                        loading = false,
                        products = module.quoteProducts,
                        kpis = module.kpis,
                        rows = module.rows,
                        error = null
                    )
                }
                .onFailure { if (it is CancellationException) throw it; if (connection.activeShopId() == shopId) _state.value = _state.value.copy(loading = false, error = it.message ?: "No se pudo cargar las cotizaciones.") }
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
        _state.value = current.copy(
            saving = true,
            error = null,
            message = null,
            convertedInvoiceNumber = null,
            convertedInvoiceUrl = null
        )
        viewModelScope.launch {
            runCatching {
                val shopId = checkNotNull(connection.activeShopId()) { "Selecciona una tienda para cotizar." }
                require(current.shopId == shopId) { "La tienda cambió; vuelve a abrir Cotizaciones." }
                val selected = customerId?.let { id ->
                    checkNotNull(current.customers.firstOrNull { it.id == id && it.isActive != false && !it.localOnly }) { "Selecciona un cliente activo y sincronizado de esta tienda." }
                }
                val request = QuoteCreateRequestDto(
                    customerId = selected?.id,
                    customerName = selected?.displayName() ?: customerName.trim().ifBlank { null },
                    customerPhone = if (selected != null) selected.phone else customerPhone.trim().ifBlank { null },
                    validUntil = validUntil.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    items = current.cart.map { (product, quantity) -> QuoteItemRequestDto(product.id, quantity, product.price) }
                )
                val ack = durable.submit(shopId, "quote_create", json.encodeToJsonElement(QuoteCreateRequestDto.serializer(), request).jsonObject)
                json.decodeFromJsonElement(com.example.bspos.data.micatalogo.dto.QuoteResponseDto.serializer(), ack)
            }.onSuccess { response ->
                if (connection.activeShopId() != current.shopId) return@onSuccess
                _state.value = _state.value.copy(saving = false, cart = emptyMap(), message = response.message ?: "Cotización guardada.")
                load()
            }.onFailure { if (it is CancellationException) throw it; _state.value = _state.value.copy(saving = false, error = it.message ?: "No se pudo guardar la cotización.") }
        }
    }

    fun convert(quoteId: String) {
        val requestedShop = _state.value.shopId ?: return
        if (_state.value.convertingId != null) return
        _state.value = _state.value.copy(convertingId = quoteId, error = null, message = null)
        viewModelScope.launch {
            runCatching {
                val shopId = checkNotNull(connection.activeShopId()) { "Selecciona una tienda para convertir." }
                require(shopId == requestedShop) { "La tienda cambió; vuelve a seleccionar la cotización." }
                val ack = durable.submit(shopId, "quote_convert", buildJsonObject {}, quoteId)
                json.decodeFromJsonElement(com.example.bspos.data.micatalogo.dto.QuoteResponseDto.serializer(), ack)
            }
                .onSuccess { response ->
                    if (connection.activeShopId() != requestedShop) return@onSuccess
                    val activeShop = connection.activeShopId()
                    val catalogMessage = activeShop?.let { shopId ->
                        when (val sync = catalog.get().syncCatalog(shopId)) {
                            is com.example.bspos.domain.model.MiCatalogoResult.Success -> null
                            is com.example.bspos.domain.model.MiCatalogoResult.Failure ->
                                " La venta fue creada, pero el inventario local se actualizará al reintentar la sincronización."
                        }
                    }
                    _state.value = _state.value.copy(
                        convertingId = null,
                        convertedInvoiceNumber = response.invoiceNumber,
                        convertedInvoiceUrl = response.invoiceUrl,
                        message = (response.message ?: "Cotización convertida en venta.") + (catalogMessage ?: "")
                    )
                    load()
                }
                .onFailure { if (it is CancellationException) throw it; if (connection.activeShopId() == requestedShop) _state.value = _state.value.copy(convertingId = null, error = it.message ?: "No se pudo convertir la cotización.") }
        }
    }
}

internal fun RemoteCustomerDto.displayName(): String = name?.takeIf(String::isNotBlank)
    ?: listOfNotNull(firstName, lastName).joinToString(" ").trim().ifBlank { "Cliente" }
