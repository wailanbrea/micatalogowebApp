package com.example.bspos.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Escala de radios de esquinas de BSPOS: 8, 12, 16, 20, 24 y Pill.
 */
data class BSPOSShapes(
    val small: Shape = RoundedCornerShape(8.dp),
    val medium: Shape = RoundedCornerShape(12.dp),
    val large: Shape = RoundedCornerShape(16.dp),
    val extraLarge: Shape = RoundedCornerShape(20.dp),
    val cardRadius: Shape = RoundedCornerShape(20.dp),
    val pill: Shape = RoundedCornerShape(50)
)

val LocalBSPOSShapes = staticCompositionLocalOf { BSPOSShapes() }
