package com.example.bspos.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.MiCatalogoConnectionState
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import com.example.bspos.domain.repository.MiCatalogoPosSaleRepository
import com.example.bspos.data.micatalogo.PosSaleSyncScheduler
import com.example.bspos.presentation.common.UiErrorBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val authenticated: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val connectionRepository: MiCatalogoConnectionRepository,
    private val catalogRepository: MiCatalogoCatalogRepository,
    private val posSaleRepository: MiCatalogoPosSaleRepository,
    private val posSaleSyncScheduler: PosSaleSyncScheduler
) : ViewModel() {
    val connection = connectionRepository.observeConnection()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiCatalogoConnectionState("", false))
    val uiState = MutableStateFlow(LoginUiState())
    private var syncing = false
    private var syncedToken: String? = null

    fun login(email: String, password: String, remember: Boolean) = viewModelScope.launch {
        uiState.value = LoginUiState(isSubmitting = true)
        when (val result = connectionRepository.login(email, password, remember)) {
            is MiCatalogoResult.Success -> uiState.value = LoginUiState(authenticated = true)
            is MiCatalogoResult.Failure -> uiState.value = LoginUiState(errorMessage = result.message)
        }
    }

    fun consumeAuthenticated() {
        uiState.value = uiState.value.copy(authenticated = false)
    }

    fun syncCatalogs() = viewModelScope.launch {
        val token = connectionRepository.accessToken() ?: return@launch
        if (syncing || syncedToken == token) return@launch
        syncing = true
        try {
            UiErrorBus.show("Sincronizando MiCatalogo...")
            val saleSyncMessage = when (val result = posSaleRepository.syncDueSales()) {
                is MiCatalogoResult.Success -> {
                    if (result.value.retried > 0) posSaleSyncScheduler.enqueue()
                    when {
                        result.value.sent > 0 -> "${result.value.sent} ventas enviadas"
                        result.value.blocked > 0 -> "${result.value.blocked} ventas requieren revisión"
                        else -> null
                    }
                }
                is MiCatalogoResult.Failure -> {
                    posSaleSyncScheduler.enqueue()
                    "No se pudieron reenviar las ventas pendientes"
                }
            }
            val shops = when (val result = connectionRepository.shops()) {
                is MiCatalogoResult.Success -> result.value
                is MiCatalogoResult.Failure -> {
                    UiErrorBus.show("No se pudieron cargar las tiendas: ${result.message}")
                    return@launch
                }
            }
            catalogRepository.archiveRemoteProductsExcept(shops.map { it.id }.toSet())
            val summaries = mutableListOf<com.example.bspos.domain.model.MiCatalogoCatalogSyncResult>()
            for (shop in shops) {
                when (val result = catalogRepository.syncCatalog(shop.id)) {
                    is MiCatalogoResult.Success -> summaries += result.value
                    is MiCatalogoResult.Failure -> {
                        UiErrorBus.show("No se pudo sincronizar ${shop.name}: ${result.message}")
                        return@launch
                    }
                }
            }
            syncedToken = token
            val products = summaries.sumOf { it.productsApplied }
            val images = summaries.sumOf { it.imagesDownloaded }
            val failedImages = summaries.sumOf { it.imagesFailed }
            val imageStatus = if (failedImages == 0) {
                "$images imágenes descargadas"
            } else {
                "$images imágenes descargadas, $failedImages pendientes"
            }
            val sales = saleSyncMessage?.let { " $it." }.orEmpty()
            UiErrorBus.show("MiCatalogo sincronizado: $products productos. $imageStatus.$sales")
        } finally {
            syncing = false
        }
    }
}
