package com.example.bspos.presentation.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import com.example.bspos.data.micatalogo.api.CustomerPaymentDto
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.apiErrorMessage
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.PaymentMethod
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.SaleRepository
import com.example.bspos.domain.usecase.PaymentAllocationInput
import com.example.bspos.domain.usecase.RecordPaymentRequest
import com.example.bspos.domain.usecase.RecordPaymentUseCase
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.common.toUiMessage
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    customers: CustomerRepository,
    sales: SaleRepository,
    private val recordPayment: RecordPaymentUseCase,
    private val customerApi: Lazy<MiCatalogoCustomerApi>,
    private val connection: MiCatalogoConnectionRepository,
    private val customerDao: CustomerRouteDao,
    private val catalog: MiCatalogoCatalogRepository
) : ViewModel() {
    val customers = customers.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = sales.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        refreshRemoteCustomers()
    }

    private fun refreshRemoteCustomers() {
        viewModelScope.launch {
            connection.activeShopId()?.let { catalog.syncCustomers(it) }
        }
    }

    fun select(customer: Customer?) { _customer.value = customer }
    fun collect(saleId: UUID, amount: Long, method: PaymentMethod) {
        val selected = _customer.value ?: return
        viewModelScope.launch {
            runCatching {
                recordPayment(RecordPaymentRequest("REC-${System.currentTimeMillis()}", selected.id, Instant.now(), method, listOf(PaymentAllocationInput(saleId, amount))))
            }.onFailure {
                val message = it.toUiMessage("No se pudo registrar el cobro")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    /**
     * A remote sale may not have a local Sale row yet. In that case the
     * customer balance is still authoritative and the server can allocate the
     * payment FIFO across its open invoices.
     */
    fun collectRemote(customer: Customer, amount: Long, method: PaymentMethod) {
        viewModelScope.launch {
            runCatching {
                require(amount > 0L && amount <= customer.balance) { "El monto supera el saldo pendiente." }
                val shopId = checkNotNull(connection.activeShopId()) { "Selecciona una tienda activa." }
                val local = checkNotNull(customerDao.findCustomer(customer.id)) { "Cliente no encontrado en este dispositivo." }
                val remoteId = checkNotNull(local.miCatalogoCustomerId) {
                    "Este cliente todavía no está vinculado a MiCatalogo. Sincroniza la tienda antes de cobrarlo."
                }
                val response = customerApi.get().payment(
                    shopId,
                    remoteId,
                    CustomerPaymentDto(
                        uuid = UUID.randomUUID().toString(),
                        amount = MiCatalogoPosSaleOutboxMapper.decimalPrice(amount),
                        paymentMethod = method.toApiValue()
                    )
                )
                if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo registrar el cobro."))
                val payload = checkNotNull(response.body()) { "MiCatalogo no devolvió el saldo actualizado." }
                val serverBalance = MoneyUtils.parsePesosStringToCents(payload.customerBalance)
                check(customerDao.updateCustomer(local.copy(balance = serverBalance, updatedAt = Instant.now())) == 1) {
                    "No se pudo actualizar el saldo local del cliente."
                }
            }.onFailure {
                val message = it.toUiMessage("No se pudo registrar el cobro")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}

private fun PaymentMethod.toApiValue(): String = when (this) {
    PaymentMethod.CASH -> "cash"
    PaymentMethod.CARD -> "card"
    PaymentMethod.TRANSFER -> "bank_transfer"
    PaymentMethod.CHECK -> "other"
}
