package com.example.bspos.presentation.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.core.database.ShopScope
import kotlinx.coroutines.CancellationException
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.PaymentDao
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import com.example.bspos.data.micatalogo.api.CustomerPaymentDto
import com.example.bspos.data.micatalogo.api.CustomerPaymentResponseDto
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.collectLatest
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.canAccessMiCatalogoMenu
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
    paymentDao: PaymentDao,
    private val paymentSync: com.example.bspos.data.micatalogo.PaymentSyncRepository,
    private val mutations: com.example.bspos.data.micatalogo.RemoteMutationRecorder,
    private val permissions: com.example.bspos.data.micatalogo.CachedShopMenuPolicy
) : ViewModel() {
    val customers = customers.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = sales.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val localPayments = shops.observe(paymentDao::observeAll)
    private val syncedPayments = shops.observe { shop ->
        if (shop == null) flowOf(emptyList()) else paymentQueue.observeAllForShop(shop)
    }
    val paymentHistory = combine(localPayments, syncedPayments, customers.observeAll()) { local, queued, customerRows ->
        val customersById = customerRows.associateBy { it.id }
        val localRows = local.map { payment ->
            CollectionPaymentHistoryItem(
                id = payment.id.toString(), customerId = payment.customerId.toString(),
                customerName = customersById[payment.customerId]?.fullName ?: "Cliente",
                customerTaxId = customersById[payment.customerId]?.taxId,
                receipt = payment.receiptNumber, amount = payment.amount,
                method = payment.method.historyLabel(), at = payment.date,
                reference = payment.reference, notes = payment.notes
            )
        }
        val localIds = local.mapTo(mutableSetOf()) { it.id.toString() }
        val remoteRows = queued.asSequence().filter { it.state == "SENT" && it.id !in localIds }
            .mapNotNull { row ->
                val ack = runCatching { Json.decodeFromString<CustomerPaymentResponseDto>(row.serverResponse.orEmpty()) }.getOrNull()
                    ?: return@mapNotNull null
                val at = runCatching { Instant.parse(ack.serverTimestamp) }.getOrNull() ?: return@mapNotNull null
                val customerId = runCatching { UUID.fromString(row.customerId) }.getOrNull()
                CollectionPaymentHistoryItem(
                    id = row.id, customerId = row.customerId,
                    customerName = customersById[customerId]?.fullName ?: "Cliente",
                    customerTaxId = customersById[customerId]?.taxId,
                    receipt = "REC-${ack.paymentId}",
                    amount = MoneyUtils.parsePesosStringToCents(ack.amount),
                    method = ack.paymentMethod.historyLabel(), at = at,
                    notes = ack.allocations.takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ") { "${it.invoiceNumber}: ${it.allocatedAmount}" }
                )
            }.toList()
        (localRows + remoteRows).distinctBy { it.id }.sortedByDescending { it.at }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _collecting = MutableStateFlow(false)
    val collecting = _collecting.asStateFlow()
    private val paymentGate = PaymentAttemptGate()
    private val _canCollect = MutableStateFlow(false)
    val canCollect = _canCollect.asStateFlow()

    init {
        viewModelScope.launch {
            var previousShop: String? = null
            combine(connection.observeConnection(), connection.observeShops().onStart { emit(null) }) { state, snapshot -> state to snapshot }
                .collectLatest { (state, snapshot) ->
                    val available = if (!state.isConfigured) emptyList() else snapshot ?: when (val result = connection.shops()) {
                        is MiCatalogoResult.Success -> result.value
                        is MiCatalogoResult.Failure -> emptyList()
                    }
                    if (connection.activeShopId() != state.activeShopId) return@collectLatest
                    val shop = available.firstOrNull { it.id == state.activeShopId }
                    val allowed = shop != null && state.role != "accountant" && canAccessMiCatalogoMenu(false, false,
                        shop.menuPermissions, "collections", shop.capabilities, shop.enabledMenuKeys, shop.canManageMenuVisibility)
                    val refresh = allowed && (!_canCollect.value || previousShop != state.activeShopId)
                    if (!allowed || previousShop != state.activeShopId) { _customer.value = null; _message.value = null }
                    previousShop = state.activeShopId
                    _canCollect.value = allowed
                    if (refresh) state.activeShopId?.let { catalog.syncCustomers(it) }
                }
        }
    }

    fun select(customer: Customer?) { _customer.value = customer?.takeIf { _canCollect.value } }
    fun collect(saleId: UUID, amount: Long, method: PaymentMethod) {
        if (!_canCollect.value) { _message.value = "No tienes permiso vigente para registrar cobros."; return }
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
        if (!_canCollect.value) { _message.value = "No tienes permiso vigente para registrar cobros."; return }
        val operationKey = "remote:${customer.id}:$amount:${method.name}"
        val requestId = paymentGate.start(operationKey) ?: return
        _collecting.value = true
        viewModelScope.launch {
            var succeeded = false
            try {
                runCatching { shops.mutate { shopId ->
                    check(permissions.allows(shopId, "collections")) { "No tienes permiso vigente para registrar cobros en esta tienda." }
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
                                if (connection.activeShopId() == shopId && _canCollect.value) _message.value = "Cobro sincronizado."
                            }
                            "BLOCKED" -> error(ack.error ?: "El abono requiere revisión.")
                            else -> if (connection.activeShopId() == shopId && _canCollect.value) _message.value = "Abono pendiente de sincronización. Se conserva para reintentar sin duplicarlo."
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

data class CollectionPaymentHistoryItem(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerTaxId: String? = null,
    val receipt: String,
    val amount: Long,
    val method: String,
    val at: Instant,
    val reference: String? = null,
    val notes: String? = null
)

private fun PaymentMethod.historyLabel(): String = when (this) {
    PaymentMethod.CASH -> "Efectivo"
    PaymentMethod.TRANSFER -> "Transferencia"
    PaymentMethod.CARD -> "Tarjeta"
    PaymentMethod.CHECK -> "Cheque"
}

private fun String.historyLabel(): String = when (lowercase()) {
    "cash" -> "Efectivo"
    "bank_transfer", "transfer" -> "Transferencia"
    "card" -> "Tarjeta"
    else -> "Otro método"
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
