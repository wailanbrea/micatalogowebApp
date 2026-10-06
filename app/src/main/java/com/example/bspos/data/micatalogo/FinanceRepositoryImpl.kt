package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.FinanceRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val api: MiCatalogoApi
) : FinanceRepository {

    override suspend fun getFinanceSummary(
        shopId: String,
        from: String?,
        to: String?,
        sort: String?,
        direction: String?
    ): MiCatalogoResult<FinanceSummaryDto> = runCatching {
        val response = api.financeSummary(shopId, from, to, sort, direction)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo cargar el resumen financiero."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo cargar el resumen financiero.")

    override suspend fun getIncomeStatement(
        shopId: String,
        from: String?,
        to: String?
    ): MiCatalogoResult<FinanceIncomeStatementDto> = runCatching {
        val response = api.incomeStatement(shopId, from, to)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo cargar el estado de resultados."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo cargar el estado de resultados.")

    override suspend fun getCashFlow(
        shopId: String,
        from: String?,
        to: String?
    ): MiCatalogoResult<FinanceCashFlowDto> = runCatching {
        val response = api.cashFlow(shopId, from, to)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo cargar el flujo de efectivo."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo cargar el flujo de efectivo.")

    override suspend fun getCurrentCashSession(shopId: String): MiCatalogoResult<CashCurrentSessionResponseDto> = runCatching {
        val response = api.currentCashSession(shopId)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo consultar la sesión de caja."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo consultar la sesión de caja.")

    override suspend fun openCashSession(
        shopId: String,
        openingAmount: String,
        notes: String?,
        clientOperationUuid: String
    ): MiCatalogoResult<CashSessionActionResponseDto> = runCatching {
        val response = api.openCashSession(shopId, CashSessionOpenRequestDto(openingAmount, notes, clientOperationUuid))
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo abrir la caja."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo abrir la caja.")

    override suspend fun closeCashSession(
        shopId: String,
        sessionId: String,
        countedAmount: String,
        notes: String?,
        clientOperationUuid: String
    ): MiCatalogoResult<CashSessionActionResponseDto> = runCatching {
        val response = api.closeCashSession(shopId, sessionId, CashSessionCloseRequestDto(countedAmount, notes, clientOperationUuid))
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo cerrar la caja."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo cerrar la caja.")

    override suspend fun recordCashMovement(
        shopId: String,
        sessionId: String,
        type: String,
        amount: String,
        notes: String,
        clientOperationUuid: String
    ): MiCatalogoResult<CashMovementActionResponseDto> = runCatching {
        val response = api.recordCashMovement(shopId, sessionId, CashMovementRequestDto(type, amount, notes, clientOperationUuid))
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo registrar el movimiento de caja."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo registrar el movimiento de caja.")

    override suspend fun getExpenses(
        shopId: String,
        page: Int
    ): MiCatalogoResult<ExpensePaginatedResponseDto> = runCatching {
        val response = api.expenses(shopId, page)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudieron cargar los gastos."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudieron cargar los gastos.")

    override suspend fun getExpenseCategories(shopId: String): MiCatalogoResult<List<ExpenseCategoryDto>> = runCatching {
        val response = api.expenseCategories(shopId)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudieron cargar las categorías de gasto."))
        response.body().orEmpty()
    }.toMiCatalogoResult("No se pudieron cargar las categorías de gasto.")

    override suspend fun createExpense(
        shopId: String,
        request: ExpenseCreateRequestDto
    ): MiCatalogoResult<ExpenseActionResponseDto> = runCatching {
        val response = api.createExpense(shopId, request)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo registrar el gasto."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo registrar el gasto.")

    private fun <T> Result<T>.toMiCatalogoResult(defaultError: String): MiCatalogoResult<T> = fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = {
            if (it is CancellationException) throw it
            MiCatalogoResult.Failure(it.message ?: defaultError)
        }
    )
}
