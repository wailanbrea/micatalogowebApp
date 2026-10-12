package com.example.bspos.presentation.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.core.database.ShopScope
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@HiltViewModel
class CollectionViewModel @Inject constructor(
    customers: CustomerRepository,
    sales: SaleRepository,
    private val recordPayment: RecordPaymentUseCase,
    private val customerApi: Lazy<MiCatalogoCustomerApi>,
    private val connection: MiCatalogoConnectionRepository,
    private val customerDao: CustomerRouteDao,
    private val catalog: MiCatalogoCatalogRepository,
    private val shops:ShopScope,
    private val paymentQueue: com.example.bspos.data.local.dao.PaymentSyncDao,
    private val paymentSync: com.example.bspos.data.micatalogo.PaymentSyncRepository,
    private val mutations: com.example.bspos.data.micatalogo.RemoteMutationRecorder
) : ViewModel() {
    val customers = customers.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = sales.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _collecting = MutableStateFlow(false)
    val collecting = _collecting.asStateFlow()
    private val paymentGate = PaymentAttemptGate()

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
        val operationKey = "local:${selected.id}:$saleId:$amount:${method.name}"
        val requestId = paymentGate.start(operationKey) ?: return
        _collecting.value = true
        viewModelScope.launch {
            var succeeded = false
            try {
                runCatching {
                    recordPayment(RecordPaymentRequest("REC-$requestId", selected.id, Instant.now(), method, listOf(PaymentAllocationInput(saleId, amount))))
                }.onSuccess {
                    succeeded = true
                }.onFailure {
                    if (it is CancellationException) throw it
                    val message = it.toUiMessage("No se pudo registrar el cobro")
                    _message.value = message
                    UiErrorBus.show(message)
                }
            } finally {
                paymentGate.finish(operationKey, succeeded, keepIdentityOnFailure = false)
                _collecting.value = paymentGate.isBusy()
            }
        }
    }

    /**
     * A remote sale may not have a local Sale row yet. In that case the
     * customer balance is still authoritative and the server can allocate the
     * payment FIFO across its open invoices.
     */
    fun collectRemote(customer: Customer, amount: Long, method: PaymentMethod) {
        val operationKey = "remote:${customer.id}:$amount:${method.name}"
        val requestId = paymentGate.start(operationKey) ?: return
        _collecting.value = true
        viewModelScope.launch {
            var succeeded = false
            try {
                runCatching { shops.mutate { shopId ->
                    val local = checkNotNull(customerDao.findCustomer(customer.id,shopId)) { "Cliente no encontrado en la tienda activa." }
                    require(amount > 0L && amount <= local.balance) { "El monto supera el saldo pendiente." }
                    require(local.isActive && local.deletedAt==null) { "El cliente está inactivo" }
                    val remoteId = checkNotNull(local.miCatalogoCustomerId) {
                        "Este cliente todavía no está vinculado a MiCatalogo. Sincroniza la tienda antes de cobrarlo."
                    }
                    val queued = mutations.transaction {
                        val existing = paymentQueue.activeForCustomer(shopId, local.id.toString()).firstOrNull()
                        if (existing != null) {
                            val body = Json.decodeFromString<CustomerPaymentDto>(existing.payload)
                            require(existing.dependencies.isBlank() && body.amount == MiCatalogoPosSaleOutboxMapper.decimalPrice(amount) && body.paymentMethod == method.toApiValue()) {
                                "Este cliente tiene otro abono pendiente. Confírmalo antes de registrar uno distinto."
                            }
                            check(existing.state != "BLOCKED") { existing.error ?: "El abono requiere revisión." }
                            existing
                        } else {
                            val row = com.example.bspos.data.local.entity.PaymentSyncEntity(requestId, shopId, local.id.toString(),
                                Json.encodeToString(CustomerPaymentDto(requestId, MiCatalogoPosSaleOutboxMapper.decimalPrice(amount), method.toApiValue())), "")
                            paymentQueue.insert(row); row
                        }
                    }
                    mutations.wake()
                    paymentSync.sync()
                    val ack = checkNotNull(paymentQueue.find(queued.id))
                    when (ack.state) {
                            "SENT" -> {
                                succeeded = true
                                catalog.syncCustomers(shopId)
                                _message.value = "Cobro sincronizado."
                            }
                            "BLOCKED" -> error(ack.error ?: "El abono requiere revisión.")
                            else -> _message.value = "Abono pendiente de sincronización. Se conserva para reintentar sin duplicarlo."
                    }
                } }.onSuccess {
                    succeeded = true
                }.onFailure {
                    if(it is CancellationException) throw it
                    val message = it.toUiMessage("No se pudo registrar el cobro")
                    _message.value = message
                    UiErrorBus.show(message)
                }
            } finally {
                // A failed request keeps its identity for a safe retry; a
                // successful payment starts a fresh operation next time.
                paymentGate.finish(operationKey, succeeded, keepIdentityOnFailure = true)
                _collecting.value = paymentGate.isBusy()
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

/** Prevents double taps while preserving the idempotency key for a retry. */
internal class PaymentAttemptGate {
    private val inFlight = mutableSetOf<String>()
    private val identities = mutableMapOf<String, String>()

    @Synchronized
    fun start(key: String): String? {
        if (inFlight.isNotEmpty()) return null
        if (!inFlight.add(key)) return null
        return identities.getOrPut(key) { UUID.randomUUID().toString() }
    }

    @Synchronized
    fun finish(key: String, success: Boolean, keepIdentityOnFailure: Boolean) {
        inFlight.remove(key)
        if (success || !keepIdentityOnFailure) identities.remove(key)
    }

    @Synchronized
    fun isBusy(): Boolean = inFlight.isNotEmpty()
}
