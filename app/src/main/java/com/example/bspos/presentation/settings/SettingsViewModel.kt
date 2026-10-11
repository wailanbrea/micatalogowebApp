package com.example.bspos.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.InvoiceConfig
import com.example.bspos.domain.model.MiCatalogoConnectionState
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.MiCatalogoPosSaleRepository
import com.example.bspos.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MiCatalogoSettingsUiState(
    val isConnecting: Boolean = false,
    val isLoadingShops: Boolean = false,
    val syncingShopId: String? = null,
    val isSyncingPosSales: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val isCreatingSeller: Boolean = false,
    val updatingSellerMenuId: String? = null,
    val updatingMenuVisibilityShopId: String? = null,
    val shops: List<MiCatalogoShop> = emptyList(),
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val connectionRepository: MiCatalogoConnectionRepository,
    private val catalogRepository: MiCatalogoCatalogRepository,
    private val posSaleRepository: MiCatalogoPosSaleRepository,
    private val paymentSync: com.example.bspos.data.micatalogo.PaymentSyncRepository,
    private val operationQueue: com.example.bspos.data.local.dao.OperationOutboxDao,
    private val saleQueue: com.example.bspos.data.local.dao.PosSaleOutboxDao,
    private val paymentQueue: com.example.bspos.data.local.dao.PaymentSyncDao,
    private val scheduler: com.example.bspos.data.micatalogo.PosSaleSyncScheduler,
    private val catalogRefresh: com.example.bspos.data.micatalogo.CatalogRefreshSyncRepository
) : ViewModel() {
    val settings = repository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val miCatalogoConnection = connectionRepository.observeConnection()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiCatalogoConnectionState("", false))
    val miCatalogoUi = MutableStateFlow(MiCatalogoSettingsUiState())
    val pendingOperations = operationQueue.observeOutstanding().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingSales = saleQueue.observeOutstanding().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingPayments = paymentQueue.observeOutstanding().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingCatalogs = operationQueue.observeRefreshes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retryOperation(id:String) = viewModelScope.launch { operationQueue.retryBlocked(id); scheduler.enqueue() }
    fun retrySale(id:java.util.UUID) = viewModelScope.launch { saleQueue.retryBlocked(id,java.time.Instant.now()); scheduler.enqueue() }
    fun retryPayment(id:String) = viewModelScope.launch { paymentQueue.retryBlocked(id); scheduler.enqueue() }

    fun setAllowNegativeStock(enabled: Boolean) = viewModelScope.launch { repository.setAllowNegativeStock(enabled) }
    fun setAutomaticBackups(enabled: Boolean) = viewModelScope.launch { repository.setAutomaticBackupsEnabled(enabled) }
    fun setRoutesEnabled(enabled: Boolean) = viewModelScope.launch { repository.setRoutesEnabled(enabled) }
    fun setShowSupportOnDashboard(enabled: Boolean) = viewModelScope.launch { repository.setShowSupportOnDashboard(enabled) }
    fun setCurrency(currency: CurrencyUnit) = viewModelScope.launch { repository.setCurrency(currency) }
    fun saveInvoiceConfig(config: InvoiceConfig) = viewModelScope.launch { repository.setInvoiceConfig(config) }

    /** Uses the startup bootstrap snapshot without issuing a second account request. */
    fun hydrateShops(shops: List<MiCatalogoShop>) {
        miCatalogoUi.value = miCatalogoUi.value.copy(shops = shops, isLoadingShops = false)
    }

    fun connectMiCatalogo(email: String, password: String) = viewModelScope.launch {
        miCatalogoUi.value = miCatalogoUi.value.copy(isConnecting = true, successMessage = null, errorMessage = null)
        when (val result = connectionRepository.login(email, password, remember = true)) {
            is MiCatalogoResult.Success -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(isConnecting = false, successMessage = "Sesion iniciada.")
                loadShops()
                scheduler.enqueue()
            }
            is MiCatalogoResult.Failure -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(isConnecting = false, errorMessage = result.message)
            }
        }
    }

    fun logout() = viewModelScope.launch {
        connectionRepository.logout()
        miCatalogoUi.value = MiCatalogoSettingsUiState()
    }

    fun updateProfile(name: String, email: String) = viewModelScope.launch {
        miCatalogoUi.value = miCatalogoUi.value.copy(isUpdatingProfile = true, successMessage = null, errorMessage = null)
        when (val result = connectionRepository.updateProfile(name, email)) {
            is MiCatalogoResult.Success -> miCatalogoUi.value = miCatalogoUi.value.copy(isUpdatingProfile = false, successMessage = "Perfil actualizado.")
            is MiCatalogoResult.Failure -> miCatalogoUi.value = miCatalogoUi.value.copy(isUpdatingProfile = false, errorMessage = result.message)
        }
    }

    fun updateSellerMenus(shop: MiCatalogoShop, sellerId: String, permissions: List<String>) = viewModelScope.launch {
        if (miCatalogoUi.value.updatingSellerMenuId != null) return@launch
        miCatalogoUi.value = miCatalogoUi.value.copy(updatingSellerMenuId = sellerId, successMessage = null, errorMessage = null)
        when (val result = connectionRepository.updateSellerMenus(shop.id, sellerId, permissions)) {
            is MiCatalogoResult.Success -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(updatingSellerMenuId = null, successMessage = "Menús del vendedor actualizados.")
                loadShops()
            }
            is MiCatalogoResult.Failure -> miCatalogoUi.value = miCatalogoUi.value.copy(updatingSellerMenuId = null, errorMessage = result.message)
        }
    }

    fun updateShopMenuVisibility(shop: MiCatalogoShop, enabledMenuKeys: List<String>) = viewModelScope.launch {
        if (miCatalogoUi.value.updatingMenuVisibilityShopId != null) return@launch
        miCatalogoUi.value = miCatalogoUi.value.copy(updatingMenuVisibilityShopId = shop.id, successMessage = null, errorMessage = null)
        when (val result = connectionRepository.updateShopMenuVisibility(shop.id, enabledMenuKeys)) {
            is MiCatalogoResult.Success -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(updatingMenuVisibilityShopId = null, successMessage = "Menús de la tienda actualizados.")
                loadShops()
            }
            is MiCatalogoResult.Failure -> miCatalogoUi.value = miCatalogoUi.value.copy(updatingMenuVisibilityShopId = null, errorMessage = result.message)
        }
    }

    fun createSeller(shop: MiCatalogoShop, email: String, commissionType: String, commissionValue: String, permissions: List<String>) = viewModelScope.launch {
        if (miCatalogoUi.value.isCreatingSeller) return@launch
        miCatalogoUi.value = miCatalogoUi.value.copy(isCreatingSeller = true, successMessage = null, errorMessage = null)
        when (val result = connectionRepository.createSeller(shop.id, email, commissionType, commissionValue, permissions)) {
            is MiCatalogoResult.Success -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(isCreatingSeller = false, successMessage = result.value)
                loadShops()
            }
            is MiCatalogoResult.Failure -> miCatalogoUi.value = miCatalogoUi.value.copy(isCreatingSeller = false, errorMessage = result.message)
        }
    }

    fun loadShops() = viewModelScope.launch {
        if (miCatalogoUi.value.isLoadingShops) return@launch
        miCatalogoUi.value = miCatalogoUi.value.copy(isLoadingShops = true, errorMessage = null)
        when (val result = connectionRepository.refreshAccount()) {
            is MiCatalogoResult.Success -> Unit
            is MiCatalogoResult.Failure -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(isLoadingShops = false, errorMessage = result.message)
                return@launch
            }
        }
        when (val result = connectionRepository.shops()) {
            is MiCatalogoResult.Success -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(isLoadingShops = false, shops = result.value)
                val active = connectionRepository.activeShopId()
                if (result.value.isNotEmpty() && result.value.none { it.id == active }) {
                    connectionRepository.selectShop(result.value.first().id)
                }
            }
            is MiCatalogoResult.Failure -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(isLoadingShops = false, errorMessage = result.message)
            }
        }
    }

    fun selectShop(shop: MiCatalogoShop) = viewModelScope.launch {
        when (val result = connectionRepository.selectShop(shop.id)) {
            is MiCatalogoResult.Success -> miCatalogoUi.value = miCatalogoUi.value.copy(successMessage = "Tienda activa: ${shop.name}")
            is MiCatalogoResult.Failure -> miCatalogoUi.value = miCatalogoUi.value.copy(errorMessage = result.message)
        }
    }

    fun syncShop(shop: MiCatalogoShop) = viewModelScope.launch {
        if (miCatalogoUi.value.syncingShopId != null) return@launch
        miCatalogoUi.value = miCatalogoUi.value.copy(syncingShopId = shop.id, successMessage = null, errorMessage = null)
        when (val result = catalogRepository.syncCatalog(shop.id)) {
            is MiCatalogoResult.Success -> {
                val summary = result.value
                val imageMessage = if (summary.imagesFailed == 0) {
                    "${summary.imagesDownloaded} imágenes descargadas"
                } else {
                    "${summary.imagesDownloaded} imágenes descargadas, ${summary.imagesFailed} pendientes"
                }
                miCatalogoUi.value = miCatalogoUi.value.copy(
                    syncingShopId = null,
                    successMessage = "${summary.productsApplied} productos y ${summary.inventoryMovementsRecorded} movimientos importados. $imageMessage."
                )
                loadShops()
            }
            is MiCatalogoResult.Failure -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(syncingShopId = null, errorMessage = result.message)
            }
        }
    }

    fun syncPosSales() = viewModelScope.launch {
        if (miCatalogoUi.value.isSyncingPosSales) return@launch
        miCatalogoUi.value = miCatalogoUi.value.copy(isSyncingPosSales = true, successMessage = null, errorMessage = null)
        when (val result = posSaleRepository.syncDueSales()) {
            is MiCatalogoResult.Success -> {
                val summary = result.value
                val paymentsPending = paymentSync.sync()
                val catalogPending = catalogRefresh.sync()
                if (summary.retried > 0 || paymentsPending || catalogPending) scheduler.enqueue()
                val blockedPayments = paymentSync.blockedCount()
                val blockedSales = saleQueue.observeOutstanding().first().count { it.state == com.example.bspos.domain.model.PosSaleOutboxState.BLOCKED }
                val blockedOperations = operationQueue.activeShopIds().sumOf { shopId -> operationQueue.activeForShop(shopId).count { it.state == "BLOCKED" } }
                val hasIssues = summary.retried > 0 || summary.blocked > 0 || paymentsPending || blockedPayments > 0 || blockedSales > 0 || blockedOperations > 0 || catalogPending
                val recoveryMessage = summary.recoveredBottles
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = " Botella(s) recuperada(s) por decants: ")
                    .orEmpty()
                val message = "Ventas y operaciones: ${summary.sent} confirmadas en este envío; ${summary.retried} reintentos. Conflictos actuales: $blockedSales ventas, $blockedOperations operaciones y $blockedPayments abonos.$recoveryMessage ${if (paymentsPending) "Hay abonos pendientes." else "Sin abonos pendientes de envío."} ${if (catalogPending) "Falta descargar el catálogo actualizado." else "Sin descargas posconfirmación pendientes."}"
                miCatalogoUi.value = miCatalogoUi.value.copy(
                    isSyncingPosSales = false,
                    successMessage = if (!hasIssues) message else null,
                    errorMessage = if (hasIssues) message else null
                )
            }
            is MiCatalogoResult.Failure -> {
                miCatalogoUi.value = miCatalogoUi.value.copy(isSyncingPosSales = false, errorMessage = result.message)
            }
        }
    }
}
