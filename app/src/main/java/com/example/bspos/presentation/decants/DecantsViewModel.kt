package com.example.bspos.presentation.decants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.data.micatalogo.RemoteMutationRecorder
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class DecantsState(
    val loading: Boolean = true,
    val workspace: DecantWorkspaceDto? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val enqueued: Int = 0
)

@HiltViewModel
class DecantsViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository,
    private val remote: RemoteMutationRecorder,
    private val outbox: OperationOutboxDao
) : ViewModel() {
    private val _state = MutableStateFlow(DecantsState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val shop = connection.activeShopId() ?: return@launch _state.update { it.copy(loading = false, error = "Selecciona una tienda activa.") }
            _state.update { it.copy(loading = it.workspace == null, error = null) }
            try {
                val response = api.get().decants(shop)
                if (response.code() == 404) {
                    // Compatibility read model: never reinterpret derived stock as physical vials.
                    val catalog = api.get().catalog(shop)
                    check(catalog.isSuccessful) { "No se pudo consultar el catálogo." }
                    val products = catalog.body()?.products.orEmpty()
                    val groups = products.filter { it.saleUnit in setOf("bottle", "ml") }.map { source ->
                        DecantFragranceDto(source.id, source.name, source.brand, source.imageUrl, source.volumeMl ?: 0,
                            source.inventory.stockQuantity ?: 0, legacyMl = source.inventory.reservedDecantMl ?: source.inventory.availableMl,
                            sourceCostCents = source.inventory.costPrice?.let(MoneyUtils::parseDecimalToCents),
                            sizes = products.filter { it.saleUnit == "decant" && it.sourceProductId == source.id }.map {
                                DecantSizeDto(it.id, it.name, it.volumeMl ?: 0, it.price?.let(MoneyUtils::parseDecimalToCents) ?: 0,
                                    legacyCapacity = it.inventory.stockQuantity ?: 0)
                            })
                    }
                    _state.update { it.copy(loading = false, workspace = DecantWorkspaceDto(groups = groups),
                        error = "El servidor aún no habilita preparaciones físicas y envases. El stock histórico se muestra como capacidad, no como frascos listos.") }
                } else {
                    check(response.isSuccessful) { "No se pudo cargar Decants (${response.code()})." }
                    _state.update { it.copy(loading = false, workspace = response.body() ?: error("Respuesta vacía.")) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(loading = false, error = error.message ?: "No se pudo cargar Decants.") } }
        }
    }

    fun command(payload: JsonObject, productIds: List<String> = emptyList()) {
        if (_state.value.busy || _state.value.workspace?.enabled != true || _state.value.workspace?.canManage != true) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val shop = connection.activeShopId()
            if (shop.isNullOrBlank()) { _state.update { it.copy(busy = false, error = "Selecciona una tienda.") }; return@launch }
            val id = UUID.randomUUID().toString()
            try {
                val outstanding = outbox.activeForShop(shop)
                if (outstanding.isNotEmpty()) {
                    _state.update { it.copy(busy = false, error = "Hay operaciones pendientes o bloqueadas en esta tienda. Sincronízalas antes de registrar otro movimiento de Decants.") }
                    remote.wake()
                    return@launch
                }
                remote.transaction { remote.enqueue(shop, productIds, payload, Instant.now(), id) }
                _state.update { it.copy(enqueued = it.enqueued + 1, message = "Operación guardada; pendiente de sincronización.") }
                remote.wake()
                while (true) {
                    delay(700)
                    val operation = outbox.find(id) ?: break
                    if (operation.state == "SENT") {
                        _state.update { it.copy(busy = false, message = "Operación sincronizada.") }
                        load()
                        break
                    }
                    if (operation.state == "BLOCKED") {
                        _state.update { it.copy(busy = false, error = operation.error ?: "La operación requiere conciliación.", message = null) }
                        break
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(busy = false, error = error.message ?: "No se pudo guardar la operación.") } }
        }
    }
    fun refreshSync() { remote.wake(); load() }
    fun clearMessage() { _state.update { it.copy(message = null) } }
}
