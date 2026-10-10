package com.example.bspos.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.dto.CustomerUploadRequestDto
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.OrderConfirmRequestDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

data class NewCreditCustomerInput(
    val name: String,
    val phone: String,
    val creditLimit: String
)

data class OrdersUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val kpis: List<FeatureKpiDto> = emptyList(),
    val rows: List<FeatureRowDto> = emptyList(),
    val pageTotal: String? = null,
    val note: String? = null,
    val customers: List<RemoteCustomerDto> = emptyList(),
    val customersLoading: Boolean = false,
    val creatingCustomer: Boolean = false,
    val customerCreateError: String? = null,
    val createdCustomer: RemoteCustomerDto? = null,
    val confirmingId: String? = null,
    val message: String? = null,
    val error: String? = null,
    val search: String = "",
    val status: String = "all"
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val customerApi: Lazy<MiCatalogoCustomerApi>,
    private val connection: MiCatalogoConnectionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(OrdersUiState())
    val state = _state.asStateFlow()
    private var activeFeature: String = "orders"
    private var activeSearch: String = ""
    private var activeStatus: String = "all"
    private var loadJob: Job? = null
    private var customersJob: Job? = null
    private var customersShopId: String? = null
    private var customersLoaded = false
    private var loadedModuleKey: String? = null

    fun load(feature: String = activeFeature, refresh: Boolean = false, search: String = activeSearch, status: String = activeStatus) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            activeFeature = feature
            activeSearch = search.trim()
            activeStatus = status
            val shopId = connection.activeShopId()
            if (shopId.isNullOrBlank()) {
                _state.value = OrdersUiState(loading = false, error = "Selecciona una tienda activa para consultar los pedidos.")
                return@launch
            }
            val requestKey = "$feature|${activeSearch}|$activeStatus"
            val hasCachedModule = loadedModuleKey == requestKey && _state.value.error == null
            _state.update {
                it.copy(
                    // Keep the last list visible when returning to Pedidos and
                    // refresh it in the background instead of showing a blank spinner.
                    loading = !refresh && !hasCachedModule,
                    refreshing = refresh || hasCachedModule,
                    message = null,
                    error = null
                )
            }

            // Customers are required only by the credit/mixed confirmation dialog.
            // Load them in parallel without delaying the orders read model.
            // Pedidos can be reopened after a customer is created from another
            // screen. Always refresh this small list here so the confirmation
            // dialog never keeps a stale customer cache.
            loadCustomers(shopId, force = feature == "orders" || refresh)

            try {
                val moduleResponse = api.get().feature(
                    shopId,
                    feature,
                    query = activeSearch.ifBlank { null },
                    status = activeStatus.takeIf { it != "all" }
                )
                if (!moduleResponse.isSuccessful) error("No se pudieron cargar los pedidos.")
                val module = moduleResponse.body()?.module ?: error("MiCatalogo devolvió una respuesta vacía.")
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        kpis = module.kpis,
                        rows = module.rows,
                        pageTotal = module.pageTotal,
                        note = module.note,
                        search = activeSearch,
                        status = activeStatus,
                        error = null
                    )
                }
                loadedModuleKey = requestKey
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = error.message ?: "No se pudieron cargar los pedidos."
                    )
                }
            }
        }
    }

    private fun loadCustomers(shopId: String, force: Boolean = false) {
        if (!force && customersShopId == shopId && (customersLoaded || customersJob?.isActive == true)) return

        customersJob?.cancel()
        customersShopId = shopId
        customersLoaded = false
        _state.update { it.copy(customersLoading = true) }
        customersJob = viewModelScope.launch {
            val customers = try {
                val response = customerApi.get().customers(shopId)
                if (!response.isSuccessful) null else response.body()?.customers.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }

            if (customersShopId == shopId && connection.activeShopId() == shopId) {
                if (customers != null) {
                    customersLoaded = true
                    _state.update { it.copy(customers = customers, customersLoading = false) }
                } else {
                    _state.update { it.copy(customersLoading = false) }
                }
            }
        }
    }

    fun confirm(
        row: FeatureRowDto,
        paymentKind: String,
        paymentMethod: String,
        customerId: String?,
        creditAmount: String?,
        reference: String?
    ) {
        val orderId = row.id?.takeIf { it.isNotBlank() } ?: return
        if (_state.value.confirmingId != null) return
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(confirmingId = orderId, error = null, message = null)
            runCatching {
                api.get().confirmOrder(
                    shopId,
                    orderId,
                    OrderConfirmRequestDto(
                        paymentKind = paymentKind,
                        paymentMethod = paymentMethod,
                        customerId = customerId,
                        creditAmount = creditAmount?.trim()?.ifBlank { null },
                        reference = reference?.trim()?.ifBlank { null }
                    )
                )
            }.onSuccess { response ->
                if (!response.isSuccessful) error("No se pudo confirmar el pedido. Revisa el cliente, el crédito y las existencias.")
                val body = response.body()
                _state.value = _state.value.copy(
                    confirmingId = null,
                    message = body?.invoiceNumber?.let { "Venta confirmada: $it" } ?: "Pedido confirmado como venta."
                )
                load(activeFeature, refresh = true)
            }.onFailure {
                _state.value = _state.value.copy(
                    confirmingId = null,
                    error = it.message ?: "No se pudo confirmar el pedido."
                )
            }
        }
    }

    fun createCreditCustomer(input: NewCreditCustomerInput) {
        if (_state.value.creatingCustomer) return
        viewModelScope.launch {
            val shopId = connection.activeShopId()
            if (shopId.isNullOrBlank()) {
                _state.update { it.copy(customerCreateError = "Selecciona una tienda activa para crear el cliente.") }
                return@launch
            }
            val name = input.name.trim()
            val phone = input.phone.trim()
            val creditCents = MoneyUtils.parseDecimalToCents(input.creditLimit)
            if (name.isBlank() || creditCents == null || creditCents <= 0L) {
                _state.update { it.copy(customerCreateError = "Indica el nombre y un límite de crédito mayor que cero.") }
                return@launch
            }

            _state.update { it.copy(creatingCustomer = true, customerCreateError = null, createdCustomer = null) }
            try {
                val response = customerApi.get().createCustomer(
                    shopId,
                    CustomerUploadRequestDto(
                        clientCustomerUuid = UUID.randomUUID().toString(),
                        firstName = name,
                        lastName = "",
                        documentType = "cedula",
                        documentNumber = "",
                        name = name,
                        phone = phone,
                        email = null,
                        address = "",
                        whatsapp = phone.ifBlank { null },
                        reference = null,
                        creditLimit = BigDecimal.valueOf(creditCents, 2).toPlainString(),
                        notes = "Creado desde Pedidos"
                    )
                )
                if (!response.isSuccessful) {
                    throw IllegalStateException("No se pudo crear el cliente. Revisa los datos y los permisos de Clientes.")
                }
                val customer = response.body()
                    ?: throw IllegalStateException("MiCatalogo no devolvió el cliente creado.")
                _state.update {
                    it.copy(
                        customers = (it.customers.filterNot { existing -> existing.id == customer.id } + customer)
                            .sortedBy { item -> (item.name ?: "").lowercase() },
                        creatingCustomer = false,
                        customerCreateError = null,
                        createdCustomer = customer
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        creatingCustomer = false,
                        customerCreateError = error.message ?: "No se pudo crear el cliente."
                    )
                }
            }
        }
    }

    fun consumeCreatedCustomer() {
        _state.update { it.copy(createdCustomer = null) }
    }

    fun consumeCustomerCreateError() {
        _state.update { it.copy(customerCreateError = null) }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
