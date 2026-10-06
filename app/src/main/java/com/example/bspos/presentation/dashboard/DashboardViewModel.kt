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

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    customersRepository: CustomerRepository,
    inventory: InventoryRepository,
    productsRepository: ProductRepository,
    routesRepository: RouteRepository
) : ViewModel() {
    val sales = saleRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val costTotals = saleRepository.observeCostTotals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val customers = customersRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stock = inventory.observeStock(InventoryLocation.MAIN).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val products = productsRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val routes = routesRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedSale = MutableStateFlow<Sale?>(null)
    val selectedSale = _selectedSale.asStateFlow()
    val selectedItems = _selectedSale.flatMapLatest { sale ->
        sale?.let { saleRepository.observeItems(it.id) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectSale(sale: Sale?) {
        _selectedSale.value = sale
    }
}
