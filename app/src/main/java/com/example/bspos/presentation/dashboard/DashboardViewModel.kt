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
import kotlinx.coroutines.Job
import com.example.bspos.data.micatalogo.dto.SellerSummaryDto

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
    private val _sellerSummary = MutableStateFlow<SellerSummaryDto?>(null)
    val sellerSummary = _sellerSummary.asStateFlow()
    private val _sellerSummaryError = MutableStateFlow<String?>(null)
    val sellerSummaryError = _sellerSummaryError.asStateFlow()
    private val _sellerSummaryLoading = MutableStateFlow(false)
    val sellerSummaryLoading = _sellerSummaryLoading.asStateFlow()
    private var summaryJob: Job? = null

    fun loadSellerSummary(shopId: String?, period: String) {
        summaryJob?.cancel()
        _sellerSummary.value = null
        _sellerSummaryError.value = null
        if (shopId == null) {
            _sellerSummaryError.value = "Conecta tu tienda para consultar tus ventas y comisiones."
            return
        }
        summaryJob = viewModelScope.launch {
            _sellerSummaryLoading.value = true
            try {
                val response = api.get().sellerSummary(shopId, period)
                if (!response.isSuccessful) error("No se pudo cargar tu resumen (${response.code()}).")
                _sellerSummary.value = response.body() ?: error("El resumen está vacío.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _sellerSummaryError.value = "No se pudo actualizar tu resumen. Revisa la conexión y reintenta."
            } finally {
                _sellerSummaryLoading.value = false
            }
        }
    }

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
