package com.example.bspos.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.apiErrorMessage
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.OrderConfirmRequestDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import com.example.bspos.domain.model.Customer
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import java.time.Instant
import kotlinx.coroutines.flow.collect
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val connection: MiCatalogoConnectionRepository,
    private val customerRepository:CustomerRepository,
    private val catalog:MiCatalogoCatalogRepository,
    private val shops:ShopScope
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
    private var loadedShopId:String?=null

    init {
        viewModelScope.launch {
            customerRepository.observeAll().collect { customers ->
                _state.update { it.copy(customers=customers.map { customer -> customer.asRemoteOption() }) }
            }
        }
    }

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
            val requestKey = "$shopId|$feature|${activeSearch}|$activeStatus"
            val hasCachedModule = loadedModuleKey == requestKey && _state.value.error == null
            _state.update {
                it.copy(
                    // Keep the last list visible when returning to Pedidos and
                    // refresh it in the background instead of showing a blank spinner.
                    // ON_RESUME also runs on the first entry: refresh is not evidence of a cache.
                    loading = !hasCachedModule,
                    refreshing = hasCachedModule,
                    kpis = if (hasCachedModule) it.kpis else emptyList(),
                    rows = if (hasCachedModule) it.rows else emptyList(),
                    pageTotal = if (hasCachedModule) it.pageTotal else null,
                    note = if (hasCachedModule) it.note else null,
                    search = activeSearch,
                    status = activeStatus,
                    // Keep the confirmation until the screen closes the dialog and consumes it.
                    message = if (refresh && hasCachedModule) it.message else null,
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
                if (!moduleResponse.isSuccessful) error(moduleResponse.apiErrorMessage("No se pudieron cargar los pedidos."))
                val module = moduleResponse.body()?.module ?: error("MiCatalogo devolvió una respuesta vacía.")
                if(connection.activeShopId()!=shopId) throw CancellationException("La tienda cambió durante la descarga")
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
                loadedShopId=shopId
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
            val synchronized = try {
                catalog.syncCustomers(shopId) is com.example.bspos.domain.model.MiCatalogoResult.Success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }

            if (customersShopId == shopId && connection.activeShopId() == shopId) {
                if (synchronized) {
                    customersLoaded = true
                    _state.update { it.copy(customersLoading = false) }
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
            runCatching { shops.mutate { active ->
                require(active==shopId && loadedShopId==shopId) { "El pedido no pertenece a la tienda activa" }
                if(paymentKind in setOf("credit","mixed")) require(_state.value.customers.any { it.id==customerId && it.isActive!=false && !it.localOnly }) { "Selecciona un cliente activo de esta tienda" }
                val response = api.get().confirmOrder(
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
                // Keep HTTP/application errors inside the protected block. The
                // previous implementation threw from onSuccess, which escaped
                // runCatching and terminated the Android process on a 422.
                if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo confirmar el pedido. Revisa el cliente, el crédito y las existencias."))
                checkNotNull(response.body()) { "MiCatalogo devolvió una respuesta vacía al confirmar el pedido." }
            }
            }.onSuccess { body ->
                _state.value = _state.value.copy(
                    confirmingId = null,
                    message = body?.invoiceNumber?.let { "Venta confirmada: $it" } ?: "Pedido confirmado como venta."
                )
                load(activeFeature, refresh = true)
            }.onFailure {
                if(it is CancellationException) throw it
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
                val customer=shops.mutate { active ->
                    require(active==shopId)
                    val now=Instant.now()
                    val local=Customer(UUID.randomUUID(),name,phone=phone,whatsapp=phone.ifBlank { null },
                        creditLimit=creditCents,firstName=name,notes="Creado desde Pedidos",createdAt=now,updatedAt=now)
                    customerRepository.insert(local)
                    checkNotNull(customerRepository.findById(local.id)).asRemoteOption()
                }
                _state.update {
                    it.copy(
                        creatingCustomer = false,
                        customerCreateError = if(customer.localOnly) "Guardado sin conexión. Sincroniza antes de usarlo a crédito." else null,
                        createdCustomer = customer.takeUnless { it.localOnly }
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

    private fun Customer.asRemoteOption()=RemoteCustomerDto(
        id=remoteCustomerId ?: id.toString(),clientCustomerUuid=id.toString(),name=fullName,
        firstName=firstName,lastName=lastName,documentType=documentType,documentNumber=documentNumber,
        phone=phone,email=email,address=address,whatsapp=whatsapp,reference=reference,
        creditLimit=MiCatalogoPosSaleOutboxMapper.decimalPrice(creditLimit),
        balance=MiCatalogoPosSaleOutboxMapper.decimalPrice(balance),
        availableCredit=MiCatalogoPosSaleOutboxMapper.decimalPrice((creditLimit - balance).coerceAtLeast(0L)),
        isActive=isActive,
        updatedAt=updatedAt.toString(),localOnly=remoteCustomerId==null)

    fun consumeCustomerCreateError() {
        _state.update { it.copy(customerCreateError = null) }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
