package com.example.bspos.domain.usecase

import org.junit.Assert.*
import org.junit.Test

class CashRefundAmountsTest {
    @Test fun partialReturnsConserveDiscountedCents() {
        val amount = CashRefundAmounts.allocate(listOf(RefundableLine("a",3,300)),1,0).getValue("a")
        assertEquals(299L,amount)
        assertEquals(99L,CashRefundAmounts.portion(amount,3,0,1))
        assertEquals(200L,CashRefundAmounts.portion(amount,3,1,2))
    }
    @Test fun lineOrderMatchesTheOriginalSequentialDiscountAllocation() {
        val amounts=CashRefundAmounts.allocate(listOf(RefundableLine("a",2,200),RefundableLine("b",1,100)),1,1)
        assertEquals(200L,amounts.getValue("a"))
        assertEquals(100L,amounts.getValue("b"))
        assertEquals(300L,amounts.values.sum())
    }
    @Test fun fullyDiscountedLinesStillConserveGlobalTax() {
        val amounts=CashRefundAmounts.allocate(listOf(RefundableLine("a",1,300),RefundableLine("b",1,200)),500,30)
        assertEquals(18L,amounts.getValue("a")); assertEquals(12L,amounts.getValue("b"))
        assertEquals(1L,CashRefundAmounts.allocate(listOf(RefundableLine("free",2,0)),0,1).getValue("free"))
    }
    @Test fun excessiveQuantitiesAndInvalidDiscountsAreRejected() {
        assertTrue(runCatching { CashRefundAmounts.portion(100,2,1,2) }.isFailure)
        assertTrue(runCatching { CashRefundAmounts.allocate(listOf(RefundableLine("a",1,100)),101,0) }.isFailure)
        assertTrue(runCatching { CashRefundAmounts.portion(100,2,0,0) }.isFailure)
    }
    @Test fun largeAmountsDoNotOverflowDuringProration() {
        assertEquals(Long.MAX_VALUE/7,CashRefundAmounts.portion(Long.MAX_VALUE,7,0,1))
        assertEquals(Long.MAX_VALUE,CashRefundAmounts.allocate(listOf(RefundableLine("a",1,Long.MAX_VALUE)),0,0).getValue("a"))
    }
}
