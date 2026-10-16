package com.example.bspos.presentation.catalog

import com.example.bspos.domain.model.canManageCatalogType
import com.example.bspos.domain.model.canManageCatalogPhotos
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
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.data.micatalogo.RemoteMutationRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProductCatalogViewModel @Inject constructor(
    private val productUseCases: ProductUseCases,
    categoryUseCases: CategoryUseCases,
    private val unitUseCases: UnitOfMeasureUseCases,
    inventory: InventoryRepository,
    private val initialInventory: RegisterInitialInventoryUseCase,
    private val connection: MiCatalogoConnectionRepository,
    productRepository: ProductRepository,
    private val transactor: AppDatabaseTransactor,
    private val remote: RemoteMutationRecorder,
    private val shopScope: com.example.bspos.core.database.ShopScope
) : ViewModel() {
    val products = connection.observeConnection()
        .flatMapLatest { state ->
            state.activeShopId?.let(productRepository::observeForShop) ?: productRepository.observeAll()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /**
     * The selected shop is persisted independently from the shop list cache.
     * Keep it available to creation forms so a temporary shop-list refresh
     * cannot silently turn a remote product or decant into a local-only item.
     */
    val activeShopId = connection.observeConnection()
        .map { it.activeShopId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories = categoryUseCases.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val units = unitUseCases.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stock = inventory.observeStock(InventoryLocation.MAIN).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _shops = MutableStateFlow<List<MiCatalogoShop>>(emptyList())
    val shops = _shops.asStateFlow()
    private var configured = false
    private var selectedShopId: String? = null
    private var actorRole = ""

    fun canEdit(product:com.example.bspos.domain.model.Product):Boolean = canCreate(product.remoteSaleUnit, product.remoteShopId)
    fun canCreate(saleUnit: String?, shopId: String?): Boolean {
        val target = shopId ?: selectedShopId
        return if (target == null) !configured else _shops.value.any { it.id == target && it.canManageCatalogType(saleUnit) }
    }
    fun reportReadOnly() { _message.value = "Solo el propietario o administrador puede modificar los productos de esta tienda." }
    fun canManagePhotos(product: com.example.bspos.domain.model.Product): Boolean = _shops.value.any { shop ->
        shop.id == product.remoteShopId && shop.canManageCatalogPhotos()
    }

    fun updatePhoto(product: com.example.bspos.domain.model.Product, path: String) {
        if (!canManagePhotos(product)) { reportReadOnly(); return }
        val shop = product.remoteShopId ?: return
        val remoteId = product.remoteProductId ?: return
        viewModelScope.launch {
            runCatching {
                shopScope.mutate { active ->
                    if (!canManagePhotos(product) || active != shop) error("El permiso o la tienda cambió.")
                    remote.transaction { remote.photo(shop, remoteId, path) }
                }
                remote.wake()
            }.onSuccess { _message.value = "Foto pendiente de sincronización." }
                .onFailure { _message.value = it.toUiMessage("No se pudo guardar la foto") }
        }
    }

    init {
        viewModelScope.launch { connection.observeShops().collectLatest { snapshot -> _shops.value = snapshot.orEmpty() } }
        viewModelScope.launch {
            connection.observeConnection().collectLatest { state ->
                if (selectedShopId != state.activeShopId || actorRole != state.role) _shops.value = emptyList()
                configured = state.isConfigured
                selectedShopId = state.activeShopId
                actorRole = state.role
                if (!state.isConfigured) _shops.value = emptyList()
                else {
                    val result = connection.shops()
                    currentCoroutineContext().ensureActive()
                    if (connection.activeShopId() != state.activeShopId) return@collectLatest
                    when (result) {
                    is MiCatalogoResult.Success -> _shops.value = result.value
                    is MiCatalogoResult.Failure -> { _shops.value = emptyList(); _message.value = result.message }
                    }
                }
            }
        }
    }

    fun add(input: ProductInput, initialQuantity: Long = 0L, onSaved: (com.example.bspos.domain.model.Product) -> Unit = {}) {
        if (!canCreate(input.remoteSaleUnit, input.remoteShopId)) { reportReadOnly(); return }
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
                if (remoteShopId != null && input.inventoryUnitName != null && input.inventoryUnitAbbreviation != null) {
                    unitUseCases.ensure(input.unitId, input.inventoryUnitName, input.inventoryUnitAbbreviation)
                }
                transactor.runInTransaction {
                    val product = productUseCases.create(input)
                    if (initialQuantity > 0) initialInventory(product.id, initialQuantity, input.purchasePrice)
                    product
                }
            }.onSuccess { product ->
                onSaved(product)
            }.onFailure {
                val message = it.toUiMessage("No se pudo guardar el producto")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    fun update(current: com.example.bspos.domain.model.Product, input: ProductInput, onSaved: () -> Unit = {}) {
        if (!canEdit(current)) { reportReadOnly(); return }
        viewModelScope.launch {
            runCatching { productUseCases.update(current, input) }.onFailure {
                val message = it.toUiMessage("No se pudo actualizar el producto")
                _message.value = message
                UiErrorBus.show(message)
            }.onSuccess { changed ->
                if (changed) {
                    onSaved()
                } else {
                    val message = "No se encontró el producto en la tienda activa; no se guardaron cambios."
                    _message.value = message
                    UiErrorBus.show(message)
                }
            }
        }
    }

    fun configureAsBottle(current: com.example.bspos.domain.model.Product, volumeMl: Int, onSaved: () -> Unit = {}) {
        if (!canEdit(current)) { reportReadOnly(); return }
        if (current.remoteShopId.isNullOrBlank() || current.remoteProductId.isNullOrBlank()) {
            val message = "Este producto todavía no está vinculado a una tienda sincronizada."
            _message.value = message
            UiErrorBus.show(message)
            return
        }
        if (volumeMl !in 1..100_000) {
            val message = "Indica un volumen válido entre 1 y 100,000 ml."
            _message.value = message
            UiErrorBus.show(message)
            return
        }
        if (current.lastPurchaseCost <= 0L) {
            val message = "Registra primero el costo de compra del producto en Inventario."
            _message.value = message
            UiErrorBus.show(message)
            return
        }

        update(current, ProductInput(
            name = current.name,
            internalCode = current.internalCode,
            categoryId = current.categoryId,
            unitId = current.unitId,
            salePrice = current.salePrice,
            purchasePrice = current.lastPurchaseCost,
            wholesalePrice = current.wholesalePrice,
            description = current.description,
            minimumStock = current.minimumStock,
            tracksExpiration = current.tracksExpiration,
            imagePath = current.imagePath,
            thumbnailPath = current.thumbnailPath,
            remoteShopId = current.remoteShopId,
            remoteSaleUnit = "bottle",
            remoteVolumeMl = volumeMl,
            remoteSourceProductId = null,
            isCombo = false,
            comboItems = emptyList(),
            isActive = current.isActive,
        ), onSaved)
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

    fun openBottle(product: com.example.bspos.domain.model.Product, quantity: Int, expectedStock: Long) {
        val shopId = product.remoteShopId
        val productId = product.remoteProductId
        if (!canEdit(product) || shopId.isNullOrBlank() || productId.isNullOrBlank()) {
            reportReadOnly()
            return
        }
        if (product.remoteSaleUnit != "bottle") {
            _message.value = "Solo puedes abrir productos configurados como botella completa."
            return
        }
        if (quantity <= 0 || quantity.toLong() > expectedStock) {
            _message.value = "La cantidad de botellas a abrir no es válida."
            return
        }

        viewModelScope.launch {
            runCatching {
                remote.transaction {
                    remote.enqueue(shopId, listOf(productId), buildJsonObject {
                        put("type", "open_bottle")
                        put("product_id", productId)
                        put("quantity", quantity)
                        put("expected_stock", expectedStock)
                        product.remoteAvailableMl?.let { put("expected_available_ml", it) }
                        put("notes", "Apertura de botella para preparar decants desde Android")
                    }, Instant.now(), UUID.randomUUID().toString())
                }
                remote.wake()
            }.onSuccess {
                _message.value = "Apertura enviada. El inventario se actualizará al sincronizar."
            }.onFailure {
                val message = it.toUiMessage("No se pudo abrir la botella")
                _message.value = message
                UiErrorBus.show(message)
            }
        }
    }

    fun consumeMessage() { _message.value = null }
}
