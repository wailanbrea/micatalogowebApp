package com.example.bspos.presentation.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.usecase.CustomerUseCases
import com.example.bspos.domain.usecase.RouteInput
import com.example.bspos.domain.usecase.RouteUseCases
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.common.toUiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RouteViewModel @Inject constructor(
    private val useCases: RouteUseCases,
    customers: CustomerUseCases
) : ViewModel() {
    private val selectedRouteId = MutableStateFlow<UUID?>(null)
    val routes = useCases.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customers = customers.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val assignments = selectedRouteId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else useCases.observeCustomers(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun selectRoute(route: CommercialRoute?) { selectedRouteId.value = route?.id }
    fun add(input: RouteInput) = runOperation("No se pudo guardar la ruta") { useCases.create(input) }
    fun update(route: CommercialRoute, input: RouteInput) = runOperation("No se pudo actualizar la ruta") { useCases.update(route, input) }
    fun setActive(route: CommercialRoute, active: Boolean) = runOperation("No se pudo cambiar el estado de la ruta") { useCases.update(route, RouteInput(route.name, route.code, route.description), active) }
    fun assign(route: CommercialRoute, customerId: UUID) = runOperation("No se pudo asignar el cliente") { useCases.assign(route.id, customerId, assignments.value.size) }
    fun unassign(customerId: UUID) = runOperation("No se pudo quitar el cliente") { useCases.unassign(customerId) }
    fun move(customerId: UUID, offset: Int) {
        val routeId = selectedRouteId.value ?: return
        val ids = assignments.value.map { it.customerId }.toMutableList()
        val from = ids.indexOf(customerId)
        val to = from + offset
        if (from < 0 || to !in ids.indices) return
        ids[from] = ids[to].also { ids[to] = ids[from] }
        runOperation("No se pudo reordenar la ruta") { useCases.reorder(routeId, ids) }
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
