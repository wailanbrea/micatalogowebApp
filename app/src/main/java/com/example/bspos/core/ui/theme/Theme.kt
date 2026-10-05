package com.example.bspos.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E60F4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8EFFF),
    onPrimaryContainer = Color(0xFF0F4AC9),
    secondary = Color(0xFF0B192C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF14243B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFE2E8F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFE8EFFF),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0B192C),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171),
    onError = Color(0xFF000000),
    outline = Color(0xFF475569)
)

@Composable
fun BSPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val colors = if (darkTheme) {
        BSPOSColors(
            primary = Color(0xFF3B82F6),
            primaryDark = Color(0xFF1E40AF),
            primaryLight = Color(0xFF1E3A5F),
            secondaryNavy = Color(0xFF0B192C),
            secondaryNavySurface = Color(0xFF14243B),
            secondaryNavyActive = Color(0xFF1E3A5F),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            surfaceVariant = Color(0xFF334155),
            outline = Color(0xFF475569),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            textTertiary = Color(0xFF64748B),
            textOnPrimary = Color(0xFFFFFFFF),
            textOnNavy = Color(0xFFE2E8F0)
        )
    } else {
        BSPOSColors()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    val spacing = BSPOSSpacing()
    val shapes = BSPOSShapes()

    CompositionLocalProvider(
        LocalBSPOSColors provides colors,
        LocalBSPOSSpacing provides spacing,
        LocalBSPOSShapes provides shapes
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BSPOSBaseTypography,
            content = content
        )
    }
}

object BSPOSTheme {
    val colors: BSPOSColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBSPOSColors.current

    val spacing: BSPOSSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalBSPOSSpacing.current

    val shapes: BSPOSShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalBSPOSShapes.current
}
