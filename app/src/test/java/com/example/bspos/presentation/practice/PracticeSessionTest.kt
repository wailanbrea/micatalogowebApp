package com.example.bspos.presentation.practice

import org.junit.Assert.*
import org.junit.Test

class PracticeSessionTest {
    @Test fun creditCollectionDoesNotDuplicateRevenueProfitOrStock() {
        val sold = PracticeSession().sell(2, true)
        assertEquals(8, sold.stock)
        assertEquals(60000L, sold.debtCents)
        assertEquals(0L, sold.cashCents)
        val collected = sold.collect(30000)
        assertEquals(30000L, collected.debtCents)
        assertEquals(30000L, collected.cashCents)
        assertEquals(sold.salesCents, collected.salesCents)
        assertEquals(sold.profitCents, collected.profitCents)
        assertEquals(sold.stock, collected.stock)
    }
    @Test fun cashSaleAndReceiptHaveSeparateEffects() {
        val sold = PracticeSession().sell(1, false)
        assertEquals(30000L, sold.cashCents)
        assertEquals(18000L, sold.profitCents)
        assertEquals(0L, sold.debtCents)
        assertEquals(sold.copy(stock = 14), sold.receive(5))
    }
    @Test(expected = IllegalArgumentException::class) fun oversellingIsRejected() { PracticeSession().sell(11, false) }
    @Test(expected = IllegalArgumentException::class) fun overpaymentIsRejected() { PracticeSession().sell(1, true).collect(30001) }
    @Test(expected = IllegalArgumentException::class) fun negativeReceiptIsRejected() { PracticeSession().receive(-1) }
}
