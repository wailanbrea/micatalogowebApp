package com.example.bspos.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.usecase.CategoryUseCases
import com.example.bspos.domain.usecase.ProductInput
import com.example.bspos.domain.usecase.ProductUseCases
import com.example.bspos.domain.usecase.RegisterInitialInventoryUseCase
import com.example.bspos.domain.usecase.UnitOfMeasureUseCases
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.common.toUiMessage
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.core.database.AppDatabaseTransactor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductCatalogViewModel @Inject constructor(
    private val productUseCases: ProductUseCases,
    categoryUseCases: CategoryUseCases,
    unitUseCases: UnitOfMeasureUseCases,
    inventory: InventoryRepository,
    private val initialInventory: RegisterInitialInventoryUseCase,
    private val connection: MiCatalogoConnectionRepository,
    private val transactor: AppDatabaseTransactor
) : ViewModel() {
    val products = productUseCases.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = categoryUseCases.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val units = unitUseCases.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stock = inventory.observeStock(InventoryLocation.MAIN).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _shops = MutableStateFlow<List<MiCatalogoShop>>(emptyList())
    val shops = _shops.asStateFlow()

    fun canEdit(product:com.example.bspos.domain.model.Product):Boolean = product.remoteShopId == null || _shops.value.any { it.id == product.remoteShopId }
    fun reportReadOnly() { _message.value = "Solo el propietario o administrador puede modificar los productos de esta tienda." }

    init {
        viewModelScope.launch {
            connection.observeConnection().collect { state ->
                if (!state.isConfigured) _shops.value = emptyList()
                else when (val result = connection.shops()) {
                    is MiCatalogoResult.Success -> _shops.value = result.value.filter { state.isAdmin || it.canManageSellers }
                    is MiCatalogoResult.Failure -> { _shops.value = emptyList(); _message.value = result.message }
                }
            }
        }
    }

    fun add(input: ProductInput, initialQuantity: Long = 0L) {
        val remoteShopId = input.remoteShopId
        if (remoteShopId != null) {
            val quota = _shops.value.firstOrNull { it.id == remoteShopId }?.quota
            if (quota != null && !quota.canAddProducts) {
                val message = "El plan ${quota.planLabel} alcanzó el límite de ${quota.productLimit} productos para esta tienda. Gestiona el catálogo desde el panel web o actualiza el plan."
                _message.value = message
                UiErrorBus.show(message)
                return
            }
        }

        viewModelScope.launch {
            runCatching {
                transactor.runInTransaction {
                    val product = productUseCases.create(input)
                    if (initialQuantity > 0) initialInventory(product.id, initialQuantity, input.purchasePrice)
                }
            }.onFailure {
                val message = it.toUiMessage("No se pudo guardar el producto")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    fun update(current: com.example.bspos.domain.model.Product, input: ProductInput) {
        if (!canEdit(current)) { reportReadOnly(); return }
        viewModelScope.launch {
            runCatching { productUseCases.update(current, input) }.onFailure {
                val message = it.toUiMessage("No se pudo actualizar el producto")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    fun delete(product: com.example.bspos.domain.model.Product) {
        if (!canEdit(product)) { reportReadOnly(); return }
        viewModelScope.launch {
            runCatching { productUseCases.delete(product.id) }.onFailure {
                val message = it.toUiMessage("No se pudo eliminar el producto")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}
