package com.example.bspos.presentation.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object UiErrorBus {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun show(message: String) {
        _message.value = message
    }

    fun clear() {
        _message.value = null
    }
}

fun Throwable.toUiMessage(fallback: String): String = when {
    message?.contains("cash session", ignoreCase = true) == true -> "Abre una caja antes de registrar operaciones en efectivo."
    message?.contains("Insufficient stock", ignoreCase = true) == true -> "No hay existencias suficientes para completar la operación."
    message?.contains("Credit limit exceeded", ignoreCase = true) == true -> "El cliente no tiene crédito disponible suficiente."
    message?.contains("quantity", ignoreCase = true) == true || message?.contains("amount", ignoreCase = true) == true -> "Indica una cantidad válida mayor que cero."
    message?.contains("unitCost", ignoreCase = true) == true || message?.contains("cost", ignoreCase = true) == true -> "Indica un costo válido mayor o igual a cero."
    message?.contains("required", ignoreCase = true) == true -> "Completa los campos obligatorios."
    message?.contains("active", ignoreCase = true) == true -> "El registro seleccionado está inactivo."
    message?.contains("already exists", ignoreCase = true) == true || message?.contains("UNIQUE", ignoreCase = true) == true -> "Ya existe un registro con esos datos."
    message?.contains("not found", ignoreCase = true) == true -> "El registro seleccionado ya no está disponible."
    else -> message?.takeIf { it.isNotBlank() } ?: fallback
}
