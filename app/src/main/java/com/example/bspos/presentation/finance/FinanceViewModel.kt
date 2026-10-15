package com.example.bspos.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.dto.ExpenseCategoryDto
import com.example.bspos.data.micatalogo.dto.ExpenseCreateRequestDto
import com.example.bspos.data.micatalogo.dto.ExpenseDto
import com.example.bspos.data.micatalogo.dto.CashCurrentSessionResponseDto
import com.example.bspos.data.micatalogo.dto.DailyCloseDto
import com.example.bspos.data.micatalogo.dto.FinanceSummaryDto
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.FinanceRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject

enum class FinancePeriodFilter(val label: String) {
    THIS_MONTH("Este mes"),
    TODAY("Hoy"),
    THIS_WEEK("Esta semana"),
    LAST_MONTH("Mes anterior")
}

data class FinanceUiState(
    val access: FinanceAccess = FinanceAccess(),
    val isLoading: Boolean = false,
    val summary: FinanceSummaryDto? = null,
    val expenses: List<ExpenseDto> = emptyList(),
    val expensesLoading: Boolean = true,
    val expensesError: String? = null,
    val expenseCategories: List<ExpenseCategoryDto> = emptyList(),
    val periodFilter: FinancePeriodFilter = FinancePeriodFilter.THIS_MONTH,
    val fromDate: String = "",
    val toDate: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val shopName: String = "",
    val isRegisteringExpense: Boolean = false,
    val cashSession: CashCurrentSessionResponseDto? = null,
    val isCashOperationBusy: Boolean = false,
    val dailyClose: DailyCloseDto? = null,
    val isDailyCloseBusy: Boolean = false
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val connectionRepository: MiCatalogoConnectionRepository,
    private val readModelEvents: com.example.bspos.data.micatalogo.ShopReadModelEvents
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState = _uiState.asStateFlow()

    private var currentShopId: String? = null
    private val pendingCashOperationIds = mutableMapOf<String, String>()
    private var pendingExpenseOperation: Pair<String, String>? = null
    private val readJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private var initialJob: kotlinx.coroutines.Job? = null
    private var resolvedAccess = FinanceAccess()
    private var menuCeiling: Set<String>? = null
    private var permissionEpoch = 0L
    private var actorRole = ""

    init {
        val (from, to) = computeDates(FinancePeriodFilter.THIS_MONTH)
        _uiState.value = _uiState.value.copy(fromDate = from, toDate = to)
        viewModelScope.launch {
            connectionRepository.observeConnection().collectLatest { state ->
                val selected = state.activeShopId
                val roleChanged = actorRole != state.role
                actorRole = state.role
                if (roleChanged) {
                    resolvedAccess = FinanceAccess()
                    updateAccess(FinanceAccess())
                }
                if (selected != currentShopId) {
                    initialJob?.cancel(); readJobs.values.forEach { it.cancel() }; readJobs.clear()
                    currentShopId = selected
                    resolvedAccess = FinanceAccess()
                    permissionEpoch++
                    _uiState.value = FinanceUiState(fromDate = _uiState.value.fromDate, toDate = _uiState.value.toDate, periodFilter = _uiState.value.periodFilter)
                    pendingCashOperationIds.clear(); pendingExpenseOperation = null
                }
                if (state.isConfigured && selected != null && (_uiState.value.shopName.isBlank() || roleChanged)) {
                    loadInitialData()
                }
            }
        }
        viewModelScope.launch { readModelEvents.changes.collectLatest { shop -> if (shop == currentShopId) loadInitialData() } }
    }

    /** The shell may remove access immediately; it cannot grant beyond the authoritative shop snapshot. */
    fun setMenuCeiling(keys: Set<String>?) {
        if (menuCeiling == keys) return
        menuCeiling = keys?.toSet()
        updateAccess(resolvedAccess.limitedTo(menuCeiling))
        if (_uiState.value.shopName.isNotBlank()) {
            refresh()
            currentShopId?.let(::fetchCategories)
        }
    }

    private fun updateAccess(access: FinanceAccess) {
        if (_uiState.value.access == access) return
        permissionEpoch++
        readJobs.values.forEach { it.cancel() }; readJobs.clear()
        val old = _uiState.value
        _uiState.value = FinanceUiState(access = access, fromDate = old.fromDate, toDate = old.toDate,
            periodFilter = old.periodFilter, shopName = old.shopName, expensesLoading = access.expenses)
    }

    fun selectPeriod(filter: FinancePeriodFilter) {
        val (from, to) = computeDates(filter)
        _uiState.value = _uiState.value.copy(
            periodFilter = filter,
            fromDate = from,
            toDate = to
        )
        refresh()
    }

    fun setCustomPeriod(from: String, to: String) {
        _uiState.value = _uiState.value.copy(
            fromDate = from,
            toDate = to
        )
        refresh()
    }

    fun closeDaily(countedCash: String?, notes: String?) {
        if (!_uiState.value.access.mutateDayClose) return
        val shopId = currentShopId ?: return
        val date = _uiState.value.fromDate
        if (date.isBlank() || _uiState.value.isDailyCloseBusy) return
        _uiState.value = _uiState.value.copy(isDailyCloseBusy = true, errorMessage = null)
        viewModelScope.launch {
            if (currentShopId != shopId || !_uiState.value.access.mutateDayClose) return@launch
            val result = financeRepository.closeDay(shopId, date, countedCash?.trim()?.ifBlank { null }, notes?.trim()?.ifBlank { null })
            if (currentShopId != shopId || connectionRepository.activeShopId() != shopId || !_uiState.value.access.mutateDayClose) return@launch
            when (result) {
                is MiCatalogoResult.Success -> _uiState.value = _uiState.value.copy(
                    isDailyCloseBusy = false,
                    dailyClose = result.value,
                    successMessage = "Cierre diario guardado."
                )
                is MiCatalogoResult.Failure -> _uiState.value = _uiState.value.copy(
                    isDailyCloseBusy = false,
                    errorMessage = result.message
                )
            }
        }
    }

    fun refresh() {
        val shopId = currentShopId ?: return
        fetchSummary(shopId)
        fetchExpenses(shopId)
        fetchCashSession(shopId)
        fetchDailyClose(shopId)
    }

    fun loadInitialData() {
        initialJob?.cancel()
        initialJob = viewModelScope.launch {
            val selectedShopId = connectionRepository.activeShopId() ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val shopsResult = connectionRepository.shops()
            if (connectionRepository.activeShopId() != selectedShopId) return@launch
            when (shopsResult) {
                is MiCatalogoResult.Success -> {
                    val firstShop = shopsResult.value.firstOrNull { it.id == selectedShopId }
                    if (firstShop != null) {
                        currentShopId = firstShop.id
                        resolvedAccess = FinanceAccess.forShop(firstShop, actorRole)
                        updateAccess(resolvedAccess.limitedTo(menuCeiling))
                        _uiState.value = _uiState.value.copy(shopName = firstShop.name, isLoading = false, expensesLoading = _uiState.value.access.expenses)
                        fetchSummary(firstShop.id)
                        fetchExpenses(firstShop.id)
                        fetchCategories(firstShop.id)
                        fetchCashSession(firstShop.id)
                        fetchDailyClose(firstShop.id)
                    } else {
                        resolvedAccess = FinanceAccess()
                        updateAccess(FinanceAccess())
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "No se encontraron tiendas activas asociadas a la cuenta."
                        )
                    }
                }
                is MiCatalogoResult.Failure -> {
                    resolvedAccess = FinanceAccess()
                    updateAccess(FinanceAccess())
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = shopsResult.message
                    )
                }
            }
        }
    }

    private fun fetchCashSession(shopId: String) {
        if (!_uiState.value.access.cash) return
        val epoch = permissionEpoch
        readJobs.remove("cash")?.cancel()
        readJobs["cash"] = viewModelScope.launch {
            val result = financeRepository.getCurrentCashSession(shopId)
            if (epoch != permissionEpoch || !_uiState.value.access.cash || currentShopId != shopId || connectionRepository.activeShopId() != shopId) return@launch
            when (result) {
                is MiCatalogoResult.Success -> _uiState.value = _uiState.value.copy(cashSession = result.value)
                is MiCatalogoResult.Failure -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
            }
        }
    }

    private fun fetchDailyClose(shopId: String) {
        if (!_uiState.value.access.dayClose) return
        val epoch = permissionEpoch
        val date = _uiState.value.fromDate
        readJobs.remove("day")?.cancel()
        if (date.isBlank() || date != _uiState.value.toDate) { _uiState.value = _uiState.value.copy(dailyClose = null); return }
        readJobs["day"] = viewModelScope.launch {
            val result = financeRepository.getDailyClose(shopId, date)
            if (epoch != permissionEpoch || !_uiState.value.access.dayClose || currentShopId != shopId || _uiState.value.fromDate != date || _uiState.value.toDate != date) return@launch
            when (result) {
                is MiCatalogoResult.Success -> _uiState.value = _uiState.value.copy(dailyClose = result.value)
                is MiCatalogoResult.Failure -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
            }
        }
    }

    fun openRemoteCash(amount: String, notes: String?) {
        val shopId = currentShopId ?: return
        val normalizedNotes = notes?.trim()?.ifBlank { null }
        runCashOperation("open|${amount.trim()}|$normalizedNotes") { operationId ->
            financeRepository.openCashSession(shopId, amount.trim(), normalizedNotes, operationId)
        }
    }

    fun closeRemoteCash(amount: String?, notes: String?) {
        val shopId = currentShopId ?: return
        val sessionId = _uiState.value.cashSession?.session?.id ?: return
        val normalizedNotes = notes?.trim()?.ifBlank { null }
        runCashOperation("close|$sessionId|${amount?.trim().orEmpty()}|$normalizedNotes") { operationId ->
            financeRepository.closeCashSession(shopId, sessionId, amount?.trim()?.ifBlank { null }, normalizedNotes, operationId)
        }
    }

    fun recordRemoteCashMovement(type: String, amount: String, notes: String) {
        val shopId = currentShopId ?: return
        val sessionId = _uiState.value.cashSession?.session?.id ?: return
        runCashOperation("movement|$sessionId|$type|${amount.trim()}|${notes.trim()}") { operationId ->
            financeRepository.recordCashMovement(shopId, sessionId, type, amount.trim(), notes.trim(), operationId)
        }
    }

    private fun runCashOperation(key: String, action: suspend (String) -> MiCatalogoResult<*>) {
        if (!_uiState.value.access.mutateCash) return
        val shopId = currentShopId ?: return
        if (_uiState.value.isCashOperationBusy) return
        val operationId = pendingCashOperationIds.getOrPut(key) { UUID.randomUUID().toString() }
        _uiState.value = _uiState.value.copy(isCashOperationBusy = true, errorMessage = null)
        viewModelScope.launch {
            if (currentShopId != shopId || !_uiState.value.access.mutateCash) return@launch
            val result = runCatching { action(operationId) }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                MiCatalogoResult.Failure(it.message ?: "No se pudo completar la operación de caja.")
            }
            if (currentShopId != shopId || connectionRepository.activeShopId() != shopId || !_uiState.value.access.mutateCash) return@launch
            when (result) {
                is MiCatalogoResult.Success<*> -> {
                    pendingCashOperationIds.remove(key)
                    _uiState.value = _uiState.value.copy(isCashOperationBusy = false, successMessage = "Operación de caja registrada.")
                    refresh()
                }
                is MiCatalogoResult.Failure -> _uiState.value = _uiState.value.copy(isCashOperationBusy = false, errorMessage = result.message)
            }
        }
    }

    private fun fetchSummary(shopId: String) {
        if (!_uiState.value.access.finance) return
        val epoch = permissionEpoch
        val from = _uiState.value.fromDate; val to = _uiState.value.toDate
        readJobs.remove("summary")?.cancel()
        readJobs["summary"] = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = financeRepository.getFinanceSummary(
                shopId = shopId,
                from = from.ifBlank { null },
                to = to.ifBlank { null }
            )
            if (epoch != permissionEpoch || !_uiState.value.access.finance || currentShopId != shopId || _uiState.value.fromDate != from || _uiState.value.toDate != to) return@launch
            when (result) {
                is MiCatalogoResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        summary = result.value
                    )
                }
                is MiCatalogoResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    private fun fetchExpenses(shopId: String) {
        if (!_uiState.value.access.expenses) return
        val epoch = permissionEpoch
        _uiState.value = _uiState.value.copy(expensesLoading = true, expensesError = null)
        readJobs.remove("expenses")?.cancel()
        readJobs["expenses"] = viewModelScope.launch {
            val result = financeRepository.getExpenses(shopId, page = 1)
            if (epoch != permissionEpoch || !_uiState.value.access.expenses || currentShopId != shopId || connectionRepository.activeShopId() != shopId) return@launch
            when (result) {
                is MiCatalogoResult.Success -> {
                    _uiState.value = _uiState.value.copy(expenses = result.value.data, expensesLoading = false)
                }
                is MiCatalogoResult.Failure -> {
                    _uiState.value = _uiState.value.copy(expensesLoading = false, expensesError = result.message)
                }
            }
        }
    }

    private fun fetchCategories(shopId: String) {
        if (!_uiState.value.access.expenses) return
        val epoch = permissionEpoch
        readJobs.remove("categories")?.cancel()
        readJobs["categories"] = viewModelScope.launch {
            val result = financeRepository.getExpenseCategories(shopId)
            if (epoch != permissionEpoch || !_uiState.value.access.expenses || currentShopId != shopId || connectionRepository.activeShopId() != shopId) return@launch
            when (result) {
                is MiCatalogoResult.Success -> {
                    _uiState.value = _uiState.value.copy(expenseCategories = result.value)
                }
                is MiCatalogoResult.Failure -> {
                    // Non-blocking
                }
            }
        }
    }

    fun registerExpense(
        description: String,
        amount: String,
        categoryId: Int?,
        paymentMethod: String,
        occurredAt: String,
        notes: String? = null
    ) {
        val shopId = currentShopId ?: return
        if (!_uiState.value.access.mutateExpenses) return
        if (_uiState.value.isRegisteringExpense) return
        val normalizedDescription = description.trim()
        val normalizedAmount = amount.trim()
        val normalizedNotes = notes?.trim()?.ifBlank { null }
        val normalizedOccurredAt = occurredAt.trim().ifBlank { null }
        val operationKey = listOf(categoryId, normalizedDescription, normalizedAmount, paymentMethod, normalizedOccurredAt, normalizedNotes).joinToString("|")
        val operationId = pendingExpenseOperation
            ?.takeIf { it.first == operationKey }
            ?.second
            ?: UUID.randomUUID().toString().also { pendingExpenseOperation = operationKey to it }
        _uiState.value = _uiState.value.copy(isRegisteringExpense = true, errorMessage = null)
        viewModelScope.launch {
            if (currentShopId != shopId || !_uiState.value.access.mutateExpenses) return@launch
            val request = ExpenseCreateRequestDto(
                expenseCategoryId = categoryId,
                description = normalizedDescription,
                amount = normalizedAmount,
                paymentMethod = paymentMethod,
                occurredAt = normalizedOccurredAt,
                notes = normalizedNotes,
                clientOperationUuid = operationId
            )
            val result = financeRepository.createExpense(shopId, request)
            if (currentShopId != shopId || connectionRepository.activeShopId() != shopId || !_uiState.value.access.mutateExpenses) return@launch
            when (result) {
                is MiCatalogoResult.Success -> {
                    if (pendingExpenseOperation?.second == operationId) pendingExpenseOperation = null
                    _uiState.value = _uiState.value.copy(
                        isRegisteringExpense = false,
                        successMessage = "Gasto registrado correctamente."
                    )
                    refresh()
                }
                is MiCatalogoResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isRegisteringExpense = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun dismissMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    private fun computeDates(filter: FinancePeriodFilter): Pair<String, String> {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return when (filter) {
            FinancePeriodFilter.TODAY -> Pair(today.format(formatter), today.format(formatter))
            FinancePeriodFilter.THIS_WEEK -> {
                val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
                Pair(start.format(formatter), today.format(formatter))
            }
            FinancePeriodFilter.THIS_MONTH -> {
                val start = today.withDayOfMonth(1)
                Pair(start.format(formatter), today.format(formatter))
            }
            FinancePeriodFilter.LAST_MONTH -> {
                val lastMonth = today.minusMonths(1)
                val start = lastMonth.withDayOfMonth(1)
                val end = lastMonth.with(TemporalAdjusters.lastDayOfMonth())
                Pair(start.format(formatter), end.format(formatter))
            }
        }
    }
}
