package com.example.bspos.presentation.returning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi
import com.example.bspos.data.micatalogo.apiErrorMessage
import com.example.bspos.data.micatalogo.dto.ReturnEligibleInvoiceDto
import com.example.bspos.data.micatalogo.dto.ReturnEligibleItemDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.model.SaleStatus
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.SaleRepository
import com.example.bspos.domain.usecase.CashReturnLineInput
import com.example.bspos.domain.usecase.CompleteCashReturnRequest
import com.example.bspos.domain.usecase.CompleteCashReturnUseCase
import com.example.bspos.data.local.dao.ReturnDao
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.common.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import dagger.Lazy
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReturnViewModel @Inject constructor(
    saleRepository: SaleRepository,
    products: ProductRepository,
    private val completeReturn: CompleteCashReturnUseCase,
    returns: ReturnDao,
    private val shops:ShopScope,
    private val connection: MiCatalogoConnectionRepository,
    private val api: Lazy<MiCatalogoApi>,
    private val operationApi: Lazy<MiCatalogoOperationApi>,
    private val mutations: com.example.bspos.data.micatalogo.RemoteMutationRecorder,
    private val queue: com.example.bspos.data.local.dao.OperationOutboxDao,
    private val sync: com.example.bspos.data.micatalogo.OperationSyncRepository,
    private val catalog: com.example.bspos.domain.repository.MiCatalogoCatalogRepository
) : ViewModel() {
    private val selected = MutableStateFlow<Sale?>(null)
    private val _message = MutableStateFlow<String?>(null)

    val message = _message.asStateFlow()
    val sales = saleRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val products = products.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val items = selected.flatMapLatest { sale -> sale?.let { saleRepository.observeItems(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val returnedQuantities = selected.flatMapLatest { sale -> sale?.let { selectedSale -> shops.observe { returns.observeQuantities(selectedSale.id,it) } } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _remoteInvoices = MutableStateFlow<List<ReturnEligibleInvoiceDto>>(emptyList())
    val remoteInvoices = _remoteInvoices.asStateFlow()
    private val _remoteLoading = MutableStateFlow(true)
    val remoteLoading = _remoteLoading.asStateFlow()
    private val _remoteError = MutableStateFlow<String?>(null)
    val remoteError = _remoteError.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private var readJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch { shops.changes.collect { _remoteInvoices.value = emptyList(); selected.value = null; refreshRemote() } }
    }

    fun select(sale: Sale?) { selected.value = sale }

    fun refreshRemote() {
        readJob?.cancel()
        _remoteLoading.value = true; _remoteError.value = null
        readJob = viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            runCatching {
                val response = api.get().eligibleReturns(shopId)
                check(response.isSuccessful) { response.apiErrorMessage("No se pudieron actualizar las ventas para devolución.") }
                response.body()?.invoices.orEmpty()
            }.onSuccess { invoices ->
                if (connection.activeShopId() == shopId) _remoteInvoices.value = invoices
            }.onFailure {
                if (it is CancellationException) throw it
                if (connection.activeShopId() == shopId) _remoteError.value = "No se pudieron confirmar las ventas elegibles. Reintenta la actualización."
            }
            if (connection.activeShopId() == shopId) _remoteLoading.value = false
        }
    }

    fun submit(item: com.example.bspos.domain.model.SaleItem, quantity: Long, restock: Boolean, reason: String) {
        if (_submitting.value) return
        val sale = selected.value ?: return
        _submitting.value = true
        viewModelScope.launch {
            try { runCatching {
                completeReturn(CompleteCashReturnRequest("DEV-${System.currentTimeMillis()}", sale.id, Instant.now(), listOf(CashReturnLineInput(item.id, item.productId, quantity, item.unitPrice, item.unitCostSnapshot, restock)), reason.ifBlank { null }, refundChargedAmount = true))
            }.onFailure {
                if (it is CancellationException) throw it
                val message = it.toUiMessage("No se pudo registrar la devolución")
                _message.value = message
                UiErrorBus.show(message)
            } } finally { _submitting.value = false }
        }
    }

    fun submitRemote(invoice: ReturnEligibleInvoiceDto, item: ReturnEligibleItemDto, quantity: Int, restock: Boolean, reason: String) {
        if (_submitting.value) return
        _submitting.value = true
        viewModelScope.launch {
            try { runCatching { shops.mutate { shopId ->
                require(quantity > 0 && quantity <= item.remainingQuantity && invoice.items.any { it.id == item.id })
                val unitPriceCents = com.example.bspos.core.money.MoneyUtils.parsePesosStringToCents(item.unitPrice)
                val totalCents = remoteRefundCents(item, quantity)
                val payload = buildJsonObject {
                    put("type", "return")
                    put("invoice_id", invoice.id)
                    put("notes", reason.trim())
                    put("items", buildJsonArray {
                        add(buildJsonObject {
                            put("product_id", item.productId)
                            put("quantity", quantity)
                            put("refund_price", MiCatalogoPosSaleOutboxMapper.decimalPrice(unitPriceCents))
                            put("refund_total", MiCatalogoPosSaleOutboxMapper.decimalPrice(totalCents))
                            put("restock", restock)
                        })
                    })
                }
                val row = mutations.transaction {
                    val previous = queue.activeForShop(shopId).firstOrNull { candidate ->
                        runCatching { kotlinx.serialization.json.Json.parseToJsonElement(candidate.payload).jsonObject["invoice_id"]?.jsonPrimitive?.longOrNull == invoice.id }.getOrDefault(false)
                    }
                    if (previous != null) {
                        val original = kotlinx.serialization.json.Json.parseToJsonElement(previous.payload).jsonObject
                        require(kotlinx.serialization.json.JsonObject(original - "client_operation_uuid") == payload) {
                            "Esta factura tiene otra devolución pendiente. Confírmala antes de registrar una distinta."
                        }
                        check(previous.state != "BLOCKED") { previous.error ?: "La devolución requiere revisión." }
                        previous
                    } else {
                        val id = UUID.randomUUID().toString()
                        mutations.enqueue(shopId, listOf(item.productId), payload, Instant.now(), id)
                        checkNotNull(queue.find(id))
                    }
                }
                mutations.wake()
                when (sync.submit(row)) {
                    com.example.bspos.data.micatalogo.OperationSubmission.SENT -> {
                        _message.value = "Devolución sincronizada."
                        catalog.syncCatalog(shopId)
                    }
                    com.example.bspos.data.micatalogo.OperationSubmission.RETRY -> _message.value = "Devolución pendiente de sincronización. Se conserva para reintentar sin duplicarla."
                    com.example.bspos.data.micatalogo.OperationSubmission.BLOCKED -> error(queue.find(row.id)?.error ?: "La devolución requiere revisión.")
                }
            } }.onSuccess {
                refreshRemote()
            }.onFailure {
                if (it is CancellationException) throw it
                val message = it.toUiMessage("No se pudo registrar la devolución")
                _message.value = message
                UiErrorBus.show(message)
            } } finally { _submitting.value = false }
        }
    }

    fun consumeMessage() { _message.value = null }
}

internal fun remoteRefundCents(item: ReturnEligibleItemDto, quantity: Int): Long = com.example.bspos.domain.usecase.CashRefundAmounts.portion(
    item.chargedLineCents ?: Math.multiplyExact(com.example.bspos.core.money.MoneyUtils.parsePesosStringToCents(item.unitPrice), item.quantity.toLong()),
    item.quantity.toLong(), item.refundedQuantity.toLong(), quantity.toLong())
