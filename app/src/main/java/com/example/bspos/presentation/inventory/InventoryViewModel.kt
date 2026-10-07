package com.example.bspos.presentation.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.MiCatalogoInventoryImportPreview
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.usecase.AdjustmentReasonUseCases
import com.example.bspos.domain.usecase.InventoryAdjustmentInput
import com.example.bspos.domain.usecase.RegisterInitialInventoryUseCase
import com.example.bspos.domain.usecase.RegisterInventoryAdjustmentUseCase
import com.example.bspos.domain.usecase.RegisterInventoryReceiptUseCase
import com.example.bspos.domain.usecase.RegisterPhysicalInventoryCountUseCase
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
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository,
    products: ProductRepository,
    private val initialInventory: RegisterInitialInventoryUseCase,
    private val reasonsUseCases: AdjustmentReasonUseCases,
    private val registerAdjustment: RegisterInventoryAdjustmentUseCase,
    private val receipt: RegisterInventoryReceiptUseCase,
    private val count: RegisterPhysicalInventoryCountUseCase,
    private val connection: MiCatalogoConnectionRepository,
    private val catalog: MiCatalogoCatalogRepository
) : ViewModel() {
    private val selected = MutableStateFlow<UUID?>(null)
    val stock = repository.observeStock(InventoryLocation.MAIN).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val productNames = products.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reasons = reasonsUseCases.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val movements = selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMovements(id, InventoryLocation.MAIN) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _editableShopIds = MutableStateFlow<Set<String>>(emptySet())
    val editableShopIds = _editableShopIds.asStateFlow()
    private val _importShop = MutableStateFlow<MiCatalogoShop?>(null)
    val importShop = _importShop.asStateFlow()
    private val _importPreview = MutableStateFlow<MiCatalogoInventoryImportPreview?>(null)
    val importPreview = _importPreview.asStateFlow()
    private val _importMappingPreview = MutableStateFlow<MiCatalogoInventoryImportPreview?>(null)
    val importMappingPreview = _importMappingPreview.asStateFlow()
    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()
    private val _isLoadingImport = MutableStateFlow(false)
    val isLoadingImport = _isLoadingImport.asStateFlow()
    init {
        viewModelScope.launch {
            connection.observeConnection().collect { state ->
                if (!state.isConfigured) {
                    _editableShopIds.value = emptySet()
                    _importShop.value = null
                    return@collect
                }
                when (val result = connection.shops()) {
                    is MiCatalogoResult.Success -> {
                        _editableShopIds.value = result.value.filter { state.isAdmin || it.canManageSellers }.map { it.id }.toSet()
                        _importShop.value = result.value.firstOrNull { shop ->
                            (state.isAdmin || shop.canManageSellers) &&
                                (state.isAdmin || "bulk_import" in (shop.quota?.features.orEmpty()))
                        }
                    }
                    is MiCatalogoResult.Failure -> {
                        _editableShopIds.value = emptySet()
                        _importShop.value = null
                    }
                }
            }
        }
    }

    fun select(productId: UUID?) { selected.value = productId }
    fun showMessage(text: String) { _message.value = text }
    fun initialize(productId: UUID, quantity: Long, cost: Long) = runOperation("No se pudo registrar el inventario inicial") { initialInventory(productId, quantity, cost) }
    fun receive(productId: UUID, quantity: Long, cost: Long) = runOperation("No se pudo registrar la entrada") { receipt(productId, quantity, cost) }
    fun count(productId: UUID, quantity: Long, notes: String) = runOperation("No se pudo registrar el conteo") { count.invoke(productId, quantity, notes) }
    fun addReason(name: String, direction: AdjustmentDirection) = runOperation("No se pudo guardar el motivo") { reasonsUseCases.create(name, direction) }
    fun adjust(productId: UUID, type: InventoryMovementType, quantity: Long, cost: Long, reasonId: UUID, notes: String) = runOperation("No se pudo registrar el ajuste") { registerAdjustment(InventoryAdjustmentInput(productId, type, quantity, cost, reasonId, notes)) }
    fun consumeMessage() { _message.value = null }

    fun dismissImportPreview() { _importPreview.value = null }
    fun dismissImportMapping() { _importMappingPreview.value = null }

    fun previewImport(fileName: String, mimeType: String?, bytes: ByteArray) {
        val shop = _importShop.value ?: run {
            _message.value = "Conecta una tienda con un plan que permita importar inventario."
            return
        }
        viewModelScope.launch {
            _isLoadingImport.value = true
            when (val result = connection.previewInventoryImport(shop.id, fileName, mimeType, bytes)) {
                is MiCatalogoResult.Success -> {
                    pendingImportFile = ImportFile(fileName, mimeType, bytes)
                    _importMappingPreview.value = result.value
                }
                is MiCatalogoResult.Failure -> {
                    _message.value = result.message
                    UiErrorBus.show(result.message)
                }
            }
            _isLoadingImport.value = false
        }
    }

    fun applyImportMapping(mapping: Map<String, String>) {
        val shop = _importShop.value ?: return
        val file = pendingImportFile ?: return
        viewModelScope.launch {
            _isLoadingImport.value = true
            when (val result = connection.previewInventoryImport(shop.id, file.name, file.mimeType, file.bytes, mapping)) {
                is MiCatalogoResult.Success -> {
                    _importMappingPreview.value = null
                    _importPreview.value = result.value
                }
                is MiCatalogoResult.Failure -> {
                    _message.value = result.message
                    UiErrorBus.show(result.message)
                }
            }
            _isLoadingImport.value = false
        }
    }

    fun confirmImport() {
        val shop = _importShop.value ?: return
        val preview = _importPreview.value ?: return
        if (_isImporting.value || preview.validRows == 0 || preview.validRows > preview.quota.productsRemaining) return
        viewModelScope.launch {
            _isImporting.value = true
            // Confirm the server-side preview session. The rows are deliberately
            // not sent again when a session exists, so retries are idempotent
            // and cannot create a second copy of the same inventory.
            when (val result = connection.importInventory(
                shop.id,
                preview.rows.filter { it.valid },
                preview.sessionId,
            )) {
                is MiCatalogoResult.Success -> {
                    _importPreview.value = null
                    val syncMessage = when (val sync = catalog.syncCatalog(shop.id)) {
                        is MiCatalogoResult.Success -> " Catálogo local actualizado con ${sync.value.productsApplied} productos."
                        is MiCatalogoResult.Failure -> " Importación completada; sincroniza el catálogo desde Ajustes para verla localmente."
                    }
                    _message.value = "${result.value.message}$syncMessage"
                }
                is MiCatalogoResult.Failure -> {
                    _message.value = result.message
                    UiErrorBus.show(result.message)
                }
            }
            _isImporting.value = false
        }
    }

    private fun runOperation(fallback: String, operation: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { operation() }.onFailure {
                val message = it.toUiMessage(fallback)
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    private var pendingImportFile: ImportFile? = null

    private data class ImportFile(val name: String, val mimeType: String?, val bytes: ByteArray)
}
