package com.example.bspos.presentation.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.CashMovementType
import com.example.bspos.domain.usecase.CashSessionUseCases
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.common.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashViewModel @Inject constructor(
    private val useCases: CashSessionUseCases
) : ViewModel() {
    val session = useCases.observeOpen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val expected = session.flatMapLatest { current ->
        current?.let { opened ->
            useCases.observeMovements(opened.id).map { movements ->
                opened.openingAmount + movements.sumOf { if (it.type == CashMovementType.EXPENSE || it.type == CashMovementType.REFUND) -it.amount else it.amount }
            }
        } ?: flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun open(amount: Long, notes: String?) = runOperation("No se pudo abrir la caja") { useCases.open(amount, notes) }
    fun close(amount: Long, notes: String?) {
        session.value?.let { current -> runOperation("No se pudo cerrar la caja") { useCases.close(current.id, amount, notes) } }
    }

    fun consumeMessage() { _message.value = null }

    private fun runOperation(fallback: String, operation: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { operation() }.onFailure {
                val message = it.toUiMessage(fallback)
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }
}
