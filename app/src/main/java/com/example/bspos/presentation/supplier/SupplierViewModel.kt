package com.example.bspos.presentation.supplier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.Supplier
import com.example.bspos.domain.usecase.SupplierInput
import com.example.bspos.domain.usecase.SupplierUseCases
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.common.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val useCases: SupplierUseCases
) : ViewModel() {
    val suppliers = useCases.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun add(input: SupplierInput) = runOperation("No se pudo guardar el proveedor") { useCases.create(input) }
    fun update(supplier: Supplier, input: SupplierInput) = runOperation("No se pudo actualizar el proveedor") { useCases.update(supplier, input) }
    fun delete(supplier: Supplier) = runOperation("No se pudo eliminar el proveedor") { useCases.delete(supplier.id) }
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
