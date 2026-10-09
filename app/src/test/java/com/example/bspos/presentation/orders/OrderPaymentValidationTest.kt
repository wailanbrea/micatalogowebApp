package com.example.bspos.presentation.orders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderPaymentValidationTest {
    @Test fun mixedPaymentUsesTheInitialPaymentToCalculateCredit() {
        assertEquals(750000L, calculateMixedCredit(950000L, "2000"))
        assertEquals(750000L, calculateMixedCredit(950000L, "2,000"))
    }

    @Test fun mixedPaymentRejectsEmptyZeroFullOrInvalidInitialPayment() {
        assertNull(calculateMixedCredit(950000L, ""))
        assertNull(calculateMixedCredit(950000L, "0"))
        assertNull(calculateMixedCredit(950000L, "9500"))
        assertNull(calculateMixedCredit(950000L, "abc"))
    }
}
