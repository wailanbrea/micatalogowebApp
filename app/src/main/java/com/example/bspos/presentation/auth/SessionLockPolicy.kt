package com.example.bspos.presentation.auth

import java.util.concurrent.TimeUnit

internal val sessionLockTimeoutMillis = TimeUnit.MINUTES.toMillis(5)

internal fun shouldLockSession(lastBackgroundedAt: Long, now: Long): Boolean =
    lastBackgroundedAt > 0 && now - lastBackgroundedAt >= sessionLockTimeoutMillis
