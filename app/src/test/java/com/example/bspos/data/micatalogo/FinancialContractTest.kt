package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.data.micatalogo.api.CustomerPaymentResponseDto
import com.example.bspos.presentation.update.AppUpdatePolicy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class FinancialContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun fixture(name: String) = checkNotNull(javaClass.classLoader!!.getResourceAsStream("$name.json")).bufferedReader().use { it.readText() }

    @Test fun cashDoesNotHideMissingContractWithZeros() {
        val source = fixture("cash-current-session")
        val cash = json.decodeFromString<CashCurrentSessionResponseDto>(source).session!!.summary!!
        assertEquals(15000.0, cash.expectedClosingAmount, 0.0)
        assertEquals(2000.0, cash.debtCollectionsCash, 0.0)
        assertEquals(1500.0, cash.expensesCash, 0.0)
        assertEquals(4, cash.movementsCount)
        val summary = json.parseToJsonElement(source).jsonObject["session"]!!.jsonObject["summary"]!!.jsonObject
        val broken = JsonObject(summary.filterKeys { it != "expected_closing_amount" })
        assertThrows(SerializationException::class.java) { json.decodeFromString<CashSessionSummaryDto>(broken.toString()) }
    }

    @Test fun partialExpenseAndFinancePreserveDifferentAccrualAndCashValues() {
        val expense = json.decodeFromString<ExpenseDto>(fixture("expense"))
        assertEquals(10000.0, expense.amount, 0.0)
        assertEquals(4000.0, expense.amountPaid, 0.0)
        assertEquals(6000.0, expense.unpaidAmount, 0.0)
        val finance = json.decodeFromString<FinanceSummaryDto>(fixture("finance-summary"))
        assertEquals(18000.0, finance.period.taxCollected, 0.0)
        assertEquals(94.8, finance.period.revenueCostCoverage, 0.0)
        assertTrue(finance.period.isCostCoveragePartial)
        assertEquals(1500.0, finance.currentState.receivableTotal, 0.0)
        assertEquals(4000.0, finance.cashFlow!!.outflows.expensesPaid, 0.0)
    }

    @Test fun debtReceiptPreservesAllocationsAndAuthoritativeBalance() {
        val receipt = json.decodeFromString<CustomerPaymentResponseDto>(fixture("customer-payment"))
        assertEquals(9L, receipt.paymentId)
        assertEquals(150000L, receipt.allocations.sumOf { it.allocatedCents })
        assertEquals("1500.00", receipt.customerBalance)
        assertEquals("0.00", receipt.allocations.first().remainingInvoiceBalance)
        assertTrue(receipt.cashRegisterAffected)
    }

    @Test fun updateMinimumIsNotPromotedToLatestVersion() {
        val dto = json.decodeFromString<AndroidUpdateDto>(fixture("android-update"))
        val optional = AppUpdatePolicy.available(dto, 25)!!
        assertFalse(optional.isRequired)
        assertEquals(25, optional.minimumSupportedVersionCode)
        assertTrue(AppUpdatePolicy.available(dto, 24)!!.isRequired)
        assertNull(AppUpdatePolicy.available(dto, 30))
    }

    @Test fun catalogWholesalePriceRetainsExactDecimalString() {
        val dto = json.decodeFromString<CatalogSnapshotDto>(fixture("catalog"))
        assertEquals("1200.00", dto.products.single().wholesalePrice)
        assertEquals("1500.00", dto.products.single().price)
    }
}
