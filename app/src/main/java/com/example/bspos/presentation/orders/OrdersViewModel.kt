package com.example.bspos.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.OrderConfirmRequestDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val kpis: List<FeatureKpiDto> = emptyList(),
    val rows: List<FeatureRowDto> = emptyList(),
    val note: String? = null,
    val customers: List<RemoteCustomerDto> = emptyList(),
    val confirmingId: String? = null,
    val message: String? = null,
    val error: String? = null
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

    fun load(feature: String = activeFeature, refresh: Boolean = false) {
        viewModelScope.launch {
            activeFeature = feature
            val shopId = connection.activeShopId()
            if (shopId.isNullOrBlank()) {
                _state.value = OrdersUiState(loading = false, error = "Selecciona una tienda activa para consultar los pedidos.")
                return@launch
            }
            _state.value = _state.value.copy(
                loading = !refresh,
                refreshing = refresh,
                message = null,
                error = null
            )
            runCatching {
                val moduleResponse = api.get().feature(shopId, feature)
                if (!moduleResponse.isSuccessful) error("No se pudieron cargar los pedidos.")
                val module = moduleResponse.body()?.module ?: error("MiCatalogo devolvió una respuesta vacía.")
                val customers = runCatching {
                    customerApi.get().customers(shopId).body()?.customers.orEmpty()
                }.getOrDefault(emptyList())
                module to customers
            }.onSuccess { (module, customers) ->
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    kpis = module.kpis,
                    rows = module.rows,
                    note = module.note,
                    customers = customers,
                    error = null
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = it.message ?: "No se pudieron cargar los pedidos."
                )
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

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
