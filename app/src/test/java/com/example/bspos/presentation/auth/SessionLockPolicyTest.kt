package com.example.bspos.presentation.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLockPolicyTest {
    @Test
    fun doesNotLockOnInitialLaunch() {
        assertFalse(shouldLockSession(lastBackgroundedAt = 0L, now = sessionLockTimeoutMillis * 2))
    }

    @Test
    fun locksOnlyAfterTheConfiguredBackgroundTimeout() {
        val backgroundedAt = 1_000L

        assertFalse(shouldLockSession(backgroundedAt, backgroundedAt + sessionLockTimeoutMillis - 1))
        assertTrue(shouldLockSession(backgroundedAt, backgroundedAt + sessionLockTimeoutMillis))
    }
}
