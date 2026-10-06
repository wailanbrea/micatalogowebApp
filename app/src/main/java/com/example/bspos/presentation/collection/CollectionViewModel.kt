package com.example.bspos.presentation.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.PaymentMethod
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.SaleRepository
import com.example.bspos.domain.usecase.PaymentAllocationInput
import com.example.bspos.domain.usecase.RecordPaymentRequest
import com.example.bspos.domain.usecase.RecordPaymentUseCase
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.common.toUiMessage
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
    private val recordPayment: RecordPaymentUseCase
) : ViewModel() {
    val customers = customers.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sales = sales.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

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
    fun consumeMessage() { _message.value = null }
}
