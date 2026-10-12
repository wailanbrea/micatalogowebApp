package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.FinanceRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val api: MiCatalogoApi,
    private val durable: DurableShopMutation,
    private val json: Json
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

    override suspend fun getDailyClose(shopId: String, date: String): MiCatalogoResult<DailyCloseDto> = runCatching {
        val response = api.dailyClose(shopId, date)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo cargar el cierre diario."))
        response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
    }.toMiCatalogoResult("No se pudo cargar el cierre diario.")

    override suspend fun closeDay(
        shopId: String,
        date: String,
        countedCash: String?,
        notes: String?
    ): MiCatalogoResult<DailyCloseDto> = runCatching {
        val ack = durable.submit(shopId, "day_close", json.encodeToJsonElement(DailyCloseRequestDto(date, countedCash, notes)).jsonObject)
        json.decodeFromJsonElement(DailyCloseDto.serializer(), ack)
    }.toMiCatalogoResult("No se pudo guardar el cierre diario.")

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
        val ack = durable.submit(shopId, "cash_open", json.encodeToJsonElement(CashSessionOpenRequestDto(openingAmount, notes, clientOperationUuid)).jsonObject)
        json.decodeFromJsonElement(CashSessionActionResponseDto.serializer(), ack)
    }.toMiCatalogoResult("No se pudo abrir la caja.")

    override suspend fun closeCashSession(
        shopId: String,
        sessionId: String,
        countedAmount: String?,
        notes: String?,
        clientOperationUuid: String
    ): MiCatalogoResult<CashSessionActionResponseDto> = runCatching {
        val ack = durable.submit(shopId, "cash_close", json.encodeToJsonElement(CashSessionCloseRequestDto(countedAmount, notes, clientOperationUuid)).jsonObject, sessionId)
        json.decodeFromJsonElement(CashSessionActionResponseDto.serializer(), ack)
    }.toMiCatalogoResult("No se pudo cerrar la caja.")

    override suspend fun recordCashMovement(
        shopId: String,
        sessionId: String,
        type: String,
        amount: String,
        notes: String,
        clientOperationUuid: String
    ): MiCatalogoResult<CashMovementActionResponseDto> = runCatching {
        val ack = durable.submit(shopId, "cash_movement", json.encodeToJsonElement(CashMovementRequestDto(type, amount, notes, clientOperationUuid)).jsonObject, sessionId)
        json.decodeFromJsonElement(CashMovementActionResponseDto.serializer(), ack)
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
        val ack = durable.submit(shopId, "expense_create", json.encodeToJsonElement(request).jsonObject)
        json.decodeFromJsonElement(ExpenseActionResponseDto.serializer(), ack)
    }.toMiCatalogoResult("No se pudo registrar el gasto.")

    private fun <T> Result<T>.toMiCatalogoResult(defaultError: String): MiCatalogoResult<T> = fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = {
            if (it is CancellationException) throw it
            MiCatalogoResult.Failure(it.message ?: defaultError)
        }
    )
}
