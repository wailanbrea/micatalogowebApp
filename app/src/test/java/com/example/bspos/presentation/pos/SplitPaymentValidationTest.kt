package com.example.bspos.presentation.pos

import org.junit.Assert.*
import org.junit.Test

class SplitPaymentValidationTest {
    @Test fun theDebtIsCalculatedFromTheInitialPayment() {
        val result = validatePaymentWithAutomaticCredit(950000, "2000", "", "", true, true, 1000000, true, "")
        assertTrue(result.canSubmit)
        assertEquals(750000L, result.credit)
        assertEquals(200000L, result.sum - result.credit)
        assertTrue(validatePaymentWithAutomaticCredit(950000, "", "", "", true, true, 1000000, true, "").canSubmit)
        assertFalse(validatePaymentWithAutomaticCredit(950000, "10000", "", "", true, true, 1000000, true, "").canSubmit)
    }
    @Test fun cashAndCreditReconcileExactly() {
        val result = validateSplitPayment(950000, "2000", "", "", "7500", true, 1000000, true, "2026-11-01")
        assertTrue(result.canSubmit)
        assertEquals(950000L, result.sum)
        assertEquals(750000L, result.credit)
        assertEquals(listOf("cash", "credit"), result.payments.map { it.method })
        assertEquals("2026-11-01", result.dueDate)
    }
    @Test fun insufficientCreditMissingCustomerAndInvalidDateAreRejected() {
        assertFalse(validateSplitPayment(950000, "2000", "", "", "7500", false, null, true, "").canSubmit)
        assertFalse(validateSplitPayment(950000, "2000", "", "", "7500", true, 700000, true, "").canSubmit)
        assertFalse(validateSplitPayment(950000, "2000", "", "", "7500", true, 1000000, true, "2026-02-31").canSubmit)
        assertFalse(validateSplitPayment(950000, "2000", "", "", "7500", true, 1000000, false, "").canSubmit)
    }
    @Test fun malformedAndOverflowAmountsNeverBecomeAnAcceptedPayment() {
        for (input in listOf("-1", "1.001", "abc", "99999999999999999999")) {
            val result = validateSplitPayment(100, input, "", "", "", false, null, true, "")
            assertNotNull(result.error)
            assertFalse(result.canSubmit)
        }
        assertNotNull(validateSplitPayment(Long.MAX_VALUE, "92233720368547758.07", "1", "", "", false, null, true, "").error)
    }
}
