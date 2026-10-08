package com.example.bspos.domain.repository

import com.example.bspos.data.micatalogo.dto.CashCurrentSessionResponseDto
import com.example.bspos.data.micatalogo.dto.CashMovementActionResponseDto
import com.example.bspos.data.micatalogo.dto.CashSessionActionResponseDto
import com.example.bspos.data.micatalogo.dto.DailyCloseDto
import com.example.bspos.data.micatalogo.dto.ExpenseActionResponseDto
import com.example.bspos.data.micatalogo.dto.ExpenseCategoryDto
import com.example.bspos.data.micatalogo.dto.ExpenseCreateRequestDto
import com.example.bspos.data.micatalogo.dto.ExpensePaginatedResponseDto
import com.example.bspos.data.micatalogo.dto.FinanceCashFlowDto
import com.example.bspos.data.micatalogo.dto.FinanceIncomeStatementDto
import com.example.bspos.data.micatalogo.dto.FinanceSummaryDto
import com.example.bspos.domain.model.MiCatalogoResult

interface FinanceRepository {
    suspend fun getFinanceSummary(
        shopId: String,
        from: String? = null,
        to: String? = null,
        sort: String? = null,
        direction: String? = null
    ): MiCatalogoResult<FinanceSummaryDto>

    suspend fun getIncomeStatement(
        shopId: String,
        from: String? = null,
        to: String? = null
    ): MiCatalogoResult<FinanceIncomeStatementDto>

    suspend fun getCashFlow(
        shopId: String,
        from: String? = null,
        to: String? = null
    ): MiCatalogoResult<FinanceCashFlowDto>

    suspend fun getDailyClose(
        shopId: String,
        date: String
    ): MiCatalogoResult<DailyCloseDto>

    suspend fun closeDay(
        shopId: String,
        date: String,
        countedCash: String? = null,
        notes: String? = null
    ): MiCatalogoResult<DailyCloseDto>

    suspend fun getCurrentCashSession(
        shopId: String
    ): MiCatalogoResult<CashCurrentSessionResponseDto>

    suspend fun openCashSession(
        shopId: String,
        openingAmount: String,
        notes: String? = null,
        clientOperationUuid: String
    ): MiCatalogoResult<CashSessionActionResponseDto>

    suspend fun closeCashSession(
        shopId: String,
        sessionId: String,
        countedAmount: String? = null,
        notes: String? = null,
        clientOperationUuid: String
    ): MiCatalogoResult<CashSessionActionResponseDto>

    suspend fun recordCashMovement(
        shopId: String,
        sessionId: String,
        type: String,
        amount: String,
        notes: String,
        clientOperationUuid: String
    ): MiCatalogoResult<CashMovementActionResponseDto>

    suspend fun getExpenses(
        shopId: String,
        page: Int = 1
    ): MiCatalogoResult<ExpensePaginatedResponseDto>

    suspend fun getExpenseCategories(
        shopId: String
    ): MiCatalogoResult<List<ExpenseCategoryDto>>

    suspend fun createExpense(
        shopId: String,
        request: ExpenseCreateRequestDto
    ): MiCatalogoResult<ExpenseActionResponseDto>
}
