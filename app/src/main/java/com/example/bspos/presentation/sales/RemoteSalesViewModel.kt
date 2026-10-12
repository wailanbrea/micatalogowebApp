package com.example.bspos.presentation.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.micatalogo.ShopReadModelEvents
import com.example.bspos.data.micatalogo.api.MiCatalogoSalesApi
import com.example.bspos.data.micatalogo.apiErrorMessage
import com.example.bspos.data.micatalogo.dto.FeatureModuleDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SalesReadFilters(val period: String = "today", val status: String = "all", val query: String? = null,
    val origin: String = "all", val minimum: String? = null, val maximum: String? = null)
data class RemoteSalesState(val loading: Boolean = true, val module: FeatureModuleDto? = null, val error: String? = null)

@HiltViewModel
class RemoteSalesViewModel @Inject constructor(private val api: MiCatalogoSalesApi, private val shops: ShopScope,
    private val connection: MiCatalogoConnectionRepository, queue: PosSaleOutboxDao, events: ShopReadModelEvents) : ViewModel() {
    private val _state = MutableStateFlow(RemoteSalesState())
    val state = _state.asStateFlow()
    val pending = shops.observe { shop -> queue.observeOutstanding().map { rows -> rows.filter { it.remoteShopId == shop } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var filters = SalesReadFilters()
    private var job: Job? = null
    private var lastShop: String? = null
    init {
        viewModelScope.launch { shops.changes.collect { _state.value = RemoteSalesState(); load(filters) } }
        viewModelScope.launch { events.changes.collect { if (connection.activeShopId() == it) load(filters) } }
    }
    fun load(next: SalesReadFilters = filters) {
        val changed = next != filters
        if (changed) _state.value = RemoteSalesState()
        filters = next; job?.cancel()
        job = viewModelScope.launch {
            val shop = connection.activeShopId()
            if (shop == null) { _state.value = RemoteSalesState(false, error = "Selecciona una tienda activa."); return@launch }
            _state.value = if (changed || lastShop != shop) RemoteSalesState() else _state.value.copy(loading = true, error = null)
            lastShop = shop
            try {
                val response = api.sales(shop, next.period, next.status, next.query, next.origin, next.minimum, next.maximum)
                check(response.isSuccessful) { response.apiErrorMessage("No se pudieron confirmar las ventas del servidor.") }
                val module = checkNotNull(response.body()?.module)
                if (connection.activeShopId() == shop) _state.value = RemoteSalesState(false, module)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (connection.activeShopId() == shop) _state.value = _state.value.copy(loading = false, error = error.message) }
        }
    }
    suspend fun export(all: Boolean): String {
        val shop = checkNotNull(connection.activeShopId())
        val f = if (all) SalesReadFilters(period = "all") else filters
        val response = api.export(shop, f.period, f.status, f.query, f.origin, f.minimum, f.maximum)
        check(response.isSuccessful) { response.apiErrorMessage("No se pudo exportar el historial de la tienda.") }
        check(connection.activeShopId() == shop) { "La tienda cambió antes de finalizar la exportación." }
        return checkNotNull(response.body()).string()
    }
}
