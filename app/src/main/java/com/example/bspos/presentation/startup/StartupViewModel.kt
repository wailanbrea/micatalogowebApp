package com.example.bspos.presentation.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.MiCatalogoPosSaleRepository
import com.example.bspos.presentation.dashboard.FirstSaleStatusReader
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

data class StartupSnapshot(
    val shops: List<MiCatalogoShop>,
    val activeShopId: String?,
    val firstSaleCompleted: Boolean?,
    val pendingOrders: List<FeatureRowDto>?,
    val ordersError: String? = null,
    val offline: Boolean = false,
    val warnings: List<String> = emptyList()
)

sealed interface StartupState {
    data object Idle : StartupState
    data class Loading(val message: String) : StartupState
    data class Ready(val snapshot: StartupSnapshot) : StartupState
    data class OfflineReady(val snapshot: StartupSnapshot) : StartupState
    data object RequiresLogin : StartupState
    data class Error(val message: String) : StartupState
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val connection: MiCatalogoConnectionRepository,
    private val catalog: MiCatalogoCatalogRepository,
    private val posSales: MiCatalogoPosSaleRepository,
    private val api: Lazy<MiCatalogoApi>,
    private val firstSaleStatus: FirstSaleStatusReader,
    private val scheduler: com.example.bspos.data.micatalogo.PosSaleSyncScheduler
) : ViewModel() {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<StartupState>(StartupState.Idle)
    val state = _state.asStateFlow()
    private var bootstrapJob: Job? = null
    private var lastBootstrapKey: String? = null

    fun bootstrap(force: Boolean = false) {
        bootstrapJob?.cancel()
        bootstrapJob = viewModelScope.launch {
            val token = connection.accessToken()
            if (token.isNullOrBlank()) {
                lastBootstrapKey = null
                _state.value = StartupState.RequiresLogin
                return@launch
            }
            val requestedShop = connection.activeShopId().orEmpty()
            val key = "$token|$requestedShop"
            if (!force && key == lastBootstrapKey && _state.value.isContentReady()) return@launch
            lastBootstrapKey = key
            _state.value = StartupState.Loading("Validando tu cuenta…")
            try {
                val snapshot = withTimeout(STARTUP_TIMEOUT_MS) { loadOnlineSnapshot() }
                _state.value = StartupState.Ready(snapshot)
            } catch (timeout: TimeoutCancellationException) {
                val fallback = loadOfflineSnapshot("La conexión tardó demasiado. Se muestran tus últimos datos guardados.")
                _state.value = fallback?.let { StartupState.OfflineReady(it) }
                    ?: StartupState.Error("No se pudo terminar la carga inicial. Revisa tu conexión y reintenta.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SessionExpiredException) {
                connection.clearConnection()
                lastBootstrapKey = null
                _state.value = StartupState.RequiresLogin
            } catch (error: Exception) {
                val fallback = loadOfflineSnapshot("Sin conexión. Se muestran tus últimos datos guardados.")
                _state.value = fallback?.let { StartupState.OfflineReady(it) }
                    ?: StartupState.Error(error.message ?: "No se pudo cargar MiCatalogo.")
            }
        }
    }

    fun retry() = bootstrap(force = true)

    fun reset() {
        bootstrapJob?.cancel()
        bootstrapJob = null
        lastBootstrapKey = null
        _state.value = StartupState.Idle
    }

    private suspend fun loadOnlineSnapshot(): StartupSnapshot {
        when (val account = connection.refreshAccount()) {
            is MiCatalogoResult.Success -> Unit
            is MiCatalogoResult.Failure -> {
                if (account.code == 401 || account.code == 403) throw SessionExpiredException()
                throw StartupException(account.message)
            }
        }

        _state.value = StartupState.Loading("Cargando tus tiendas…")
        val shops = when (val result = connection.shops()) {
            is MiCatalogoResult.Success -> result.value
            is MiCatalogoResult.Failure -> throw StartupException(result.message)
        }
        val activeShopId = selectActiveShop(shops)
        val warnings = mutableListOf<String>()

        when (val result = posSales.syncDueSales()) {
            is MiCatalogoResult.Success -> {
                if (result.value.retried > 0) scheduler.enqueue()
                if (result.value.blocked > 0) warnings += "Hay ventas pendientes de revisión."
            }
            is MiCatalogoResult.Failure -> {
                scheduler.enqueue()
                warnings += "Hay ventas pendientes de sincronizar."
            }
        }

        _state.value = StartupState.Loading("Preparando tu catálogo…")
        runCatching { catalog.archiveRemoteProductsExcept(shops.map { it.id }.toSet()) }
            .onFailure { warnings += "No se pudo limpiar el catálogo local anterior." }

        // Keep the existing all-shops synchronization behavior so switching shops
        // after startup does not reveal an empty local catalog.
        for (shop in shops) {
            when (val result = catalog.syncCatalog(shop.id)) {
                is MiCatalogoResult.Success -> Unit
                is MiCatalogoResult.Failure -> {
                    if (shop.id == activeShopId) throw StartupException("No se pudo cargar ${shop.name}: ${result.message}")
                    warnings += "${shop.name} quedó pendiente de sincronizar."
                }
            }
        }

        _state.value = StartupState.Loading("Comprobando el estado de tu negocio…")
        val firstSale = activeShopId?.let { shopId ->
            runCatching { firstSaleStatus.hasSale(shopId) }
                .onFailure { warnings += "No se pudo confirmar el estado de la primera venta." }
                .getOrNull()
        }
        val orders = activeShopId?.let { shopId -> loadPendingOrders(shopId, warnings) }
        return StartupSnapshot(
            shops = shops,
            activeShopId = activeShopId,
            firstSaleCompleted = firstSale,
            pendingOrders = orders?.first,
            ordersError = orders?.second,
            warnings = warnings
        )
    }

    private suspend fun loadOfflineSnapshot(reason: String): StartupSnapshot? {
        val shops = withTimeoutOrNull(2_000) {
            when (val result = connection.shops()) {
                is MiCatalogoResult.Success -> result.value
                is MiCatalogoResult.Failure -> emptyList()
            }
        }.orEmpty()
        if (shops.isEmpty()) return null
        return StartupSnapshot(
            shops = shops,
            activeShopId = selectCachedActiveShop(shops),
            firstSaleCompleted = null,
            pendingOrders = null,
            ordersError = reason,
            offline = true,
            warnings = listOf(reason)
        )
    }

    private suspend fun selectActiveShop(shops: List<MiCatalogoShop>): String? {
        if (shops.isEmpty()) return null
        val current = connection.activeShopId()
        val selected = shops.firstOrNull { it.id == current } ?: shops.first()
        if (selected.id != current) {
            when (val result = connection.selectShop(selected.id)) {
                is MiCatalogoResult.Success -> Unit
                is MiCatalogoResult.Failure -> throw StartupException(result.message)
            }
        }
        return selected.id
    }

    private suspend fun selectCachedActiveShop(shops: List<MiCatalogoShop>): String? {
        val current = connection.activeShopId()
        return shops.firstOrNull { it.id == current }?.id ?: shops.firstOrNull()?.id
    }

    private suspend fun loadPendingOrders(
        shopId: String,
        warnings: MutableList<String>
    ): Pair<List<FeatureRowDto>?, String?> {
        return runCatching {
            val response = api.get().feature(shopId, "encargos")
            if (!response.isSuccessful) error("No se pudieron consultar los encargos.")
            val rows = response.body()?.module?.rows ?: error("La respuesta de encargos está vacía.")
            rows.filter { it.status.lowercase() !in CLOSED_ORDER_STATUSES } to null
        }.getOrElse {
            warnings += "No se pudieron consultar los encargos."
            null to "No se pudieron consultar los encargos. Abre la agenda para reintentar."
        }
    }

    private fun StartupState.isContentReady(): Boolean = this is StartupState.Ready || this is StartupState.OfflineReady

    private class StartupException(message: String) : Exception(message)
    private class SessionExpiredException : Exception()

    private companion object {
        const val STARTUP_TIMEOUT_MS = 10_000L
        val CLOSED_ORDER_STATUSES = setOf("delivered", "cancelled", "completed", "entregado", "cancelado", "completado")
    }
}
