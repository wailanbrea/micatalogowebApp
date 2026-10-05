package com.example.bspos.presentation.routeload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.RouteRepository
import com.example.bspos.domain.usecase.CreateRouteLoadRequest
import com.example.bspos.domain.usecase.CreateRouteLoadUseCase
import com.example.bspos.domain.usecase.RouteLoadLineInput
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.common.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RouteLoadViewModel @Inject constructor(
    routes: RouteRepository,
    products: ProductRepository,
    private val createLoad: CreateRouteLoadUseCase
) : ViewModel() {
    val routes = routes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val products = products.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun create(routeId: UUID, lines: List<RouteLoadLineInput>, notes: String) {
        viewModelScope.launch {
            runCatching { createLoad(CreateRouteLoadRequest(routeId, lines = lines, notes = notes.ifBlank { null })) }
                .onFailure {
                    val message = it.toUiMessage("No se pudo crear la carga de ruta")
                    _message.value = message
                    UiErrorBus.show(message)
                }
        }
    }

    fun consumeMessage() { _message.value = null }
}
