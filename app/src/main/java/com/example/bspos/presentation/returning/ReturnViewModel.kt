package com.example.bspos.presentation.returning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReturnViewModel @Inject constructor(
    saleRepository: SaleRepository,
    products: ProductRepository,
    private val completeReturn: CompleteCashReturnUseCase,
    returns: ReturnDao
) : ViewModel() {
    private val selected = MutableStateFlow<Sale?>(null)
    private val _message = MutableStateFlow<String?>(null)

    val message = _message.asStateFlow()
    val sales = saleRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val products = products.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val items = selected.flatMapLatest { sale -> sale?.let { saleRepository.observeItems(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val returnedQuantities = selected.flatMapLatest { sale -> sale?.let { returns.observeQuantities(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(sale: Sale?) { selected.value = sale }

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

    fun consumeMessage() { _message.value = null }
}
