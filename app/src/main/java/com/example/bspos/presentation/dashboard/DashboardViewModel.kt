package com.example.bspos.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.SaleRepository
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.RouteRepository
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.InventoryLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import com.example.bspos.data.local.dao.PaymentDao
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    customersRepository: CustomerRepository,
    inventory: InventoryRepository,
    productsRepository: ProductRepository,
    routesRepository: RouteRepository,
    paymentDao: PaymentDao,
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository
) : ViewModel() {
    val sales = saleRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val saleItems = saleRepository.observeAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val costTotals = saleRepository.observeCostTotals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val customers = customersRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stock = inventory.observeStock(InventoryLocation.MAIN).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val products = productsRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val routes = routesRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val payments = paymentDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _pendingOrders = MutableStateFlow<List<FeatureRowDto>?>(null)
    val pendingOrders = _pendingOrders.asStateFlow()
    private val _ordersError = MutableStateFlow<String?>(null)
    val ordersError = _ordersError.asStateFlow()

    fun loadPendingOrders() = viewModelScope.launch {
        val shopId = connection.activeShopId() ?: return@launch
        _pendingOrders.value = null
        _ordersError.value = null
        try {
            val response = api.get().feature(shopId, "encargos")
            if (!response.isSuccessful) error("No se pudieron consultar los encargos.")
            val rows = response.body()?.module?.rows ?: error("La respuesta de encargos está vacía.")
            _pendingOrders.value = rows.filter { it.status.lowercase() !in setOf("delivered", "cancelled", "completed", "entregado", "cancelado", "completado") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _ordersError.value = "No se pudieron consultar los encargos. Abre la agenda para reintentar."
        }
    }

    private val _selectedSale = MutableStateFlow<Sale?>(null)
    val selectedSale = _selectedSale.asStateFlow()
    val selectedItems = _selectedSale.flatMapLatest { sale ->
        sale?.let { saleRepository.observeItems(it.id) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectSale(sale: Sale?) {
        _selectedSale.value = sale
    }
}
