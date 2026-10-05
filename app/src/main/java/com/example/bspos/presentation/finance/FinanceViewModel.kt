package com.example.bspos.presentation.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.dto.ExpenseCategoryDto
import com.example.bspos.data.micatalogo.dto.ExpenseCreateRequestDto
import com.example.bspos.data.micatalogo.dto.ExpenseDto
import com.example.bspos.data.micatalogo.dto.CashCurrentSessionResponseDto
import com.example.bspos.data.micatalogo.dto.FinanceSummaryDto
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.FinanceRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isLoading: Boolean = false,
    val summary: FinanceSummaryDto? = null,
    val expenses: List<ExpenseDto> = emptyList(),
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
    val canFinance: Boolean = false,
    val canCash: Boolean = false,
    val canExpenses: Boolean = false,
    val pendingFinancialOperations: List<String> = emptyList()
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val connectionRepository: MiCatalogoConnectionRepository,
    private val queue: com.example.bspos.data.local.dao.OperationOutboxDao,
    private val saleQueue: com.example.bspos.data.local.dao.PosSaleOutboxDao,
    private val paymentQueue: com.example.bspos.data.local.dao.PaymentSyncDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState = _uiState.asStateFlow()

    private var currentShopId: String? = null
    private val pendingCashOperationIds = mutableMapOf<String, String>()
    private var pendingExpenseOperation: Pair<String, String>? = null
    private var pendingExpensePayment: Pair<String, String>? = null

    init {
        val (from, to) = computeDates(FinancePeriodFilter.THIS_MONTH)
        _uiState.value = _uiState.value.copy(fromDate = from, toDate = to)
        loadInitialData()
        viewModelScope.launch {
            var previous = 0
            queue.observeOutstanding().collect { rows ->
                val financial = rows.filter { it.shopId == currentShopId && it.payload.contains("\"request\"") }
                _uiState.value = _uiState.value.copy(pendingFinancialOperations = financial.map {
                    "${it.id.take(8)} · ${it.state}: ${it.error ?: "Pendiente de sincronización"}"
                })
                if (financial.size < previous) refresh()
                previous = financial.size
            }
        }
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

    fun refresh() {
        val shopId = currentShopId ?: return
        if (_uiState.value.canFinance) fetchSummary(shopId)
        if (_uiState.value.canExpenses) fetchExpenses(shopId)
        if (_uiState.value.canCash) fetchCashSession(shopId)
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val shopsResult = connectionRepository.shops()) {
                is MiCatalogoResult.Success -> {
                    val firstShop = shopsResult.value.firstOrNull()
                    if (firstShop != null) {
                        currentShopId = firstShop.id
                        fun allowed(key: String) = firstShop.canManageSellers || key in firstShop.menuPermissions
                        _uiState.value = _uiState.value.copy(shopName = firstShop.name, isLoading = false,
                            canFinance = allowed("finance"), canExpenses = allowed("expenses"), canCash = allowed("cash"))
                        refresh()
                        if (allowed("expenses")) fetchCategories(firstShop.id)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "No se encontraron tiendas activas asociadas a la cuenta."
                        )
                    }
                }
                is MiCatalogoResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = shopsResult.message
                    )
                }
            }
        }
    }

    private fun fetchCashSession(shopId: String) {
        viewModelScope.launch {
            when (val result = financeRepository.getCurrentCashSession(shopId)) {
                is MiCatalogoResult.Success -> _uiState.value = _uiState.value.copy(cashSession = result.value)
                is MiCatalogoResult.Failure -> _uiState.value = _uiState.value.copy(cashSession = null, errorMessage = result.message)
            }
        }
    }

    fun openRemoteCash(amount: String, notes: String?) {
        val normalizedNotes = notes?.trim()?.ifBlank { null }
        runCashOperation("open|${amount.trim()}|$normalizedNotes") { operationId ->
            financeRepository.openCashSession(checkNotNull(currentShopId), amount.trim(), normalizedNotes, operationId)
        }
    }

    fun closeRemoteCash(amount: String, notes: String?) {
        val sessionId = checkNotNull(_uiState.value.cashSession?.session?.id)
        val normalizedNotes = notes?.trim()?.ifBlank { null }
        runCashOperation("close|$sessionId|${amount.trim()}|$normalizedNotes") { operationId ->
            val shopId = checkNotNull(currentShopId)
            if (queue.activeForShop(shopId).isNotEmpty() || saleQueue.findActiveForShop(shopId).isNotEmpty()
                || paymentQueue.unsentCount(shopId) > 0) {
                MiCatalogoResult.Failure("Sincroniza o resuelve las operaciones pendientes de esta tienda antes de cerrar la caja del servidor.")
            } else financeRepository.closeCashSession(shopId, sessionId, amount.trim(), normalizedNotes, operationId)
        }
    }

    fun recordRemoteCashMovement(type: String, amount: String, notes: String) {
        val sessionId = checkNotNull(_uiState.value.cashSession?.session?.id)
        runCashOperation("movement|$sessionId|$type|${amount.trim()}|${notes.trim()}") { operationId ->
            financeRepository.recordCashMovement(checkNotNull(currentShopId), sessionId, type, amount.trim(), notes.trim(), operationId)
        }
    }

    private fun runCashOperation(key: String, action: suspend (String) -> MiCatalogoResult<*>) {
        if (_uiState.value.isCashOperationBusy) return
        val operationId = pendingCashOperationIds.getOrPut(key) { UUID.randomUUID().toString() }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCashOperationBusy = true, errorMessage = null)
            when (val result = runCatching { action(operationId) }.getOrElse {
                MiCatalogoResult.Failure(it.message ?: "No se pudo completar la operación de caja.")
            }) {
                is MiCatalogoResult.Success<*> -> {
                    pendingCashOperationIds.remove(key)
                    _uiState.value = _uiState.value.copy(isCashOperationBusy = false,
                        successMessage = if (key.startsWith("movement|")) "Movimiento guardado, pendiente de sincronización." else "Operación de caja registrada.")
                    refresh()
                }
                is MiCatalogoResult.Failure -> _uiState.value = _uiState.value.copy(isCashOperationBusy = false, errorMessage = result.message)
            }
        }
    }

    private fun fetchSummary(shopId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = financeRepository.getFinanceSummary(
                shopId = shopId,
                from = _uiState.value.fromDate.ifBlank { null },
                to = _uiState.value.toDate.ifBlank { null }
            )) {
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
        viewModelScope.launch {
            when (val result = financeRepository.getExpenses(shopId, page = 1)) {
                is MiCatalogoResult.Success -> {
                    _uiState.value = _uiState.value.copy(expenses = result.value.data)
                }
                is MiCatalogoResult.Failure -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
            }
        }
    }

    private fun fetchCategories(shopId: String) {
        viewModelScope.launch {
            when (val result = financeRepository.getExpenseCategories(shopId)) {
                is MiCatalogoResult.Success -> {
                    _uiState.value = _uiState.value.copy(expenseCategories = result.value)
                }
                is MiCatalogoResult.Failure -> {
                    _uiState.value = _uiState.value.copy(errorMessage = result.message)
                }
            }
        }
    }

    fun registerExpense(
        description: String,
        amount: String,
        categoryId: Int?,
        paymentMethod: String,
        notes: String? = null,
        paidAmount: String? = null
    ) {
        val shopId = currentShopId ?: return
        if (_uiState.value.isRegisteringExpense) return
        val normalizedDescription = description.trim()
        val normalizedAmount = amount.trim()
        val normalizedNotes = notes?.trim()?.ifBlank { null }
        val operationKey = listOf(categoryId, normalizedDescription, normalizedAmount, paymentMethod, normalizedNotes, paidAmount).joinToString("|")
        val operationId = pendingExpenseOperation
            ?.takeIf { it.first == operationKey }
            ?.second
            ?: UUID.randomUUID().toString().also { pendingExpenseOperation = operationKey to it }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegisteringExpense = true, errorMessage = null)
            val request = ExpenseCreateRequestDto(
                expenseCategoryId = categoryId,
                description = normalizedDescription,
                amount = normalizedAmount,
                paidAmount = paidAmount,
                paymentMethod = paymentMethod,
                notes = normalizedNotes,
                clientOperationUuid = operationId
            )
            when (val result = financeRepository.createExpense(shopId, request)) {
                is MiCatalogoResult.Success -> {
                    if (pendingExpenseOperation?.second == operationId) pendingExpenseOperation = null
                    _uiState.value = _uiState.value.copy(
                        isRegisteringExpense = false,
                        successMessage = result.value.message
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

    fun payExpense(expense: ExpenseDto, amount: String, method: String, notes: String?) {
        val shopId = currentShopId ?: return
        if (_uiState.value.isRegisteringExpense) return
        val key = listOf(expense.id, amount, method, notes).joinToString("|")
        val id = pendingExpensePayment?.takeIf { it.first == key }?.second
            ?: UUID.randomUUID().toString().also { pendingExpensePayment = key to it }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegisteringExpense = true)
            val request = com.example.bspos.data.micatalogo.dto.ExpensePaymentRequestDto(
                amount = amount, paymentMethod = method, notes = notes,
                clientOperationUuid = id)
            when (val result = financeRepository.payExpense(shopId, expense.id, request)) {
                is MiCatalogoResult.Success -> {
                    pendingExpensePayment = null
                    _uiState.value = _uiState.value.copy(isRegisteringExpense = false,
                        successMessage = "Abono guardado, pendiente de sincronización.")
                }
                is MiCatalogoResult.Failure -> _uiState.value = _uiState.value.copy(isRegisteringExpense = false, errorMessage = result.message)
            }
        }
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
