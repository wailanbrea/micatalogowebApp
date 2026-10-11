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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import dagger.Lazy
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    shops:ShopScope,
    private val connection: MiCatalogoConnectionRepository,
    private val api: Lazy<MiCatalogoApi>,
    private val operationApi: Lazy<MiCatalogoOperationApi>
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

    init { refreshRemote() }

    fun select(sale: Sale?) { selected.value = sale }

    fun refreshRemote() {
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            runCatching {
                val response = api.get().eligibleReturns(shopId)
                check(response.isSuccessful) { response.apiErrorMessage("No se pudieron actualizar las ventas para devolución.") }
                response.body()?.invoices.orEmpty()
            }.onSuccess { invoices ->
                if (connection.activeShopId() == shopId) _remoteInvoices.value = invoices
            }.onFailure {
                if (it is CancellationException) throw it
                // Keep local invoices visible if the read-only remote refresh
                // is temporarily unavailable.
            }
        }
    }

    fun submit(item: com.example.bspos.domain.model.SaleItem, quantity: Long, restock: Boolean, reason: String) {
        val sale = selected.value ?: return
        viewModelScope.launch {
            runCatching {
                completeReturn(CompleteCashReturnRequest("DEV-${System.currentTimeMillis()}", sale.id, Instant.now(), listOf(CashReturnLineInput(item.id, item.productId, quantity, item.unitPrice, item.unitCostSnapshot, restock)), reason.ifBlank { null }, refundChargedAmount = true))
            }.onFailure {
                val message = it.toUiMessage("No se pudo registrar la devolución")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    fun submitRemote(invoice: ReturnEligibleInvoiceDto, item: ReturnEligibleItemDto, quantity: Int, restock: Boolean, reason: String) {
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            runCatching {
                val unitPriceCents = com.example.bspos.core.money.MoneyUtils.parsePesosStringToCents(item.unitPrice)
                val totalCents = Math.multiplyExact(unitPriceCents, quantity.toLong())
                val operationId = UUID.randomUUID().toString()
                val response = operationApi.get().submit(shopId, buildJsonObject {
                    put("type", "return")
                    put("client_operation_uuid", operationId)
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
                })
                check(response.isSuccessful) { response.apiErrorMessage("No se pudo registrar la devolución.") }
            }.onSuccess {
                _message.value = "Devolución registrada correctamente."
                refreshRemote()
            }.onFailure {
                if (it is CancellationException) throw it
                val message = it.toUiMessage("No se pudo registrar la devolución")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}
