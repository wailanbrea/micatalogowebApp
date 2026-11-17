package com.example.bspos.presentation.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAttemptGateTest {
    @Test
    fun duplicateTapIsBlockedAndFailedRequestKeepsItsIdempotencyKey() {
        val gate = PaymentAttemptGate()
        val first = gate.start("remote:customer:200:cash")

        assertTrue(first != null)
        assertNull(gate.start("remote:customer:200:cash"))
        gate.finish("remote:customer:200:cash", success = false, keepIdentityOnFailure = true)

        assertEquals(first, gate.start("remote:customer:200:cash"))
        gate.finish("remote:customer:200:cash", success = true, keepIdentityOnFailure = true)
        assertNotEquals(first, gate.start("remote:customer:200:cash"))
    }

    @Test
    fun cooldownBlocksRapidSecondTapAfterSuccess() {
        var now = 1_000L
        val gate = PaymentAttemptGate(clockMs = { now }, cooldownMs = 4_000L)
        val first = gate.start("remote:customer:200:cash")
        assertTrue(first != null)
        gate.finish("remote:customer:200:cash", success = true, keepIdentityOnFailure = true)

        now = 2_500L
        assertNull(gate.start("remote:customer:200:cash"))

        now = 5_100L
        assertTrue(gate.start("remote:customer:200:cash") != null)
    }
}
