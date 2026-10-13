package com.example.bspos.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.dao.*
import com.example.bspos.data.micatalogo.PosSaleSyncScheduler
import com.example.bspos.domain.model.PosSaleOutboxState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import javax.inject.Inject

enum class SyncPhase { CHECKING, SYNCED, PENDING, RETRY, BLOCKED }
enum class SyncResolutionTarget { NONE, DECANTS, SYNC_SETTINGS }
data class SyncStatus(
    val phase: SyncPhase = SyncPhase.CHECKING,
    val count: Int = 0,
    val error: String? = null,
    val resolutionTarget: SyncResolutionTarget = SyncResolutionTarget.NONE,
    val resolutionLabel: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    private val shops: ShopScope,
    private val operations: OperationOutboxDao,
    private val sales: PosSaleOutboxDao,
    private val payments: PaymentSyncDao,
    private val scheduler: PosSaleSyncScheduler,
    private val json: Json
) : ViewModel() {
    val status = shops.changes.flatMapLatest { shop ->
        if (shop == null) flowOf(SyncStatus()) else combine(
            operations.observeOutstanding(), sales.observeOutstanding(), payments.observeOutstanding(), operations.observeRefreshes()
        ) { allOperations, allSales, allPayments, allRefreshes ->
            val ops = allOperations.filter { it.shopId == shop }
            val bills = allSales.filter { it.remoteShopId == shop }
            val collections = allPayments.filter { it.shopId == shop }
            val refreshes = allRefreshes.filter { it.shopId == shop }
            val blockedOperation = ops.firstOrNull { it.state == "BLOCKED" }
            val blockedSale = bills.firstOrNull { it.state == PosSaleOutboxState.BLOCKED }
            val blockedPayment = collections.firstOrNull { it.state == "BLOCKED" }
            val blocked = blockedOperation?.error
                ?: blockedSale?.lastError
                ?: blockedPayment?.error
            val blockedCount = ops.count { it.state == "BLOCKED" } + bills.count { it.state == PosSaleOutboxState.BLOCKED } + collections.count { it.state == "BLOCKED" }
            val count = ops.size + bills.size + collections.size + refreshes.size
            val retry = ops.any { it.error != null } || bills.any { it.lastError != null } || collections.any { it.error != null } || refreshes.any { it.error != null }
            val blockedType = blockedOperation?.let { operationType(it.payload) }
            val resolutionTarget = when {
                blockedType?.startsWith("decant", ignoreCase = true) == true -> SyncResolutionTarget.DECANTS
                blockedCount > 0 -> SyncResolutionTarget.SYNC_SETTINGS
                else -> SyncResolutionTarget.NONE
            }
            val resolutionLabel = when (resolutionTarget) {
                SyncResolutionTarget.DECANTS -> "Decants"
                SyncResolutionTarget.SYNC_SETTINGS -> "Sincronización"
                SyncResolutionTarget.NONE -> null
            }
            SyncStatus(
                phase = when { blockedCount > 0 -> SyncPhase.BLOCKED; retry -> SyncPhase.RETRY; count > 0 -> SyncPhase.PENDING; else -> SyncPhase.SYNCED },
                count = count,
                error = blocked,
                resolutionTarget = resolutionTarget,
                resolutionLabel = resolutionLabel
            )
        }.onStart { emit(SyncStatus()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus())

    fun retry() {
        viewModelScope.launch { shops.mutate { shop ->
            operations.activeForShop(shop).filter { it.state == "BLOCKED" }.forEach { operations.retryBlocked(it.id) }
            sales.observeOutstanding().first().filter { it.remoteShopId == shop && it.state == PosSaleOutboxState.BLOCKED }.forEach { sales.retryBlocked(it.saleId, Instant.now()) }
            payments.observeOutstanding().first().filter { it.shopId == shop && it.state == "BLOCKED" }.forEach { payments.retryBlocked(it.id) }
            scheduler.enqueue()
        } }
    }

    private fun operationType(payload: String): String? = runCatching {
        json.parseToJsonElement(payload).jsonObject["type"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
