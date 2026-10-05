package com.example.bspos.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciados estricta de BSPOS: 2, 4, 8, 12, 16, 20, 24, 32, 48.
 * Prohibido el uso de valores mágicos arbitrarios.
 */
data class BSPOSSpacing(
    val space2: Dp = 2.dp,
    val space4: Dp = 4.dp,
    val space8: Dp = 8.dp,
    val space12: Dp = 12.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space32: Dp = 32.dp,
    val space48: Dp = 48.dp
)

val LocalBSPOSSpacing = staticCompositionLocalOf { BSPOSSpacing() }
