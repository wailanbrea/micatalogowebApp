package com.example.bspos.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta de colores oficial de BSPOS inspirada en el lenguaje visual de referencia:
 * Azul Eléctrico, Azul Marino, Fondos Claros Suaves y Tarjetas Blancas.
 */
data class BSPOSColors(
    val primary: Color = Color(0xFF1E60F4),             // Azul Eléctrico Primario
    val primaryDark: Color = Color(0xFF0F4AC9),         // Azul Eléctrico Oscuro (Pressed)
    val primaryLight: Color = Color(0xFFE8EFFF),        // Fondo Acento / Pills
    val secondaryNavy: Color = Color(0xFF0B192C),       // Azul Marino Profundo (Navigation Rail)
    val secondaryNavySurface: Color = Color(0xFF14243B),// Tarjetas en Sidebar Marino
    val secondaryNavyActive: Color = Color(0xFF1E3A5F), // Item activo en Sidebar

    val background: Color = Color(0xFFFFFFFF),          // Superficie administrativa uniforme
    val surface: Color = Color(0xFFFFFFFF),             // Tarjetas y Superficies
    val surfaceVariant: Color = Color(0xFFF1F5F9),      // Campos de Texto / Separadores
    val outline: Color = Color(0xFFE2E8F0),             // Bordes sutiles

    val textPrimary: Color = Color(0xFF0F172A),         // Títulos y Precios Principales
    val textSecondary: Color = Color(0xFF64748B),       // Subtítulos y Códigos
    val textTertiary: Color = Color(0xFF94A3B8),        // Placeholders y Deshabilitados
    val textOnPrimary: Color = Color(0xFFFFFFFF),       // Texto sobre fondos primarios
    val textOnNavy: Color = Color(0xFFE2E8F0),          // Texto sobre Navigation Rail

    val success: Color = Color(0xFF10B981),             // Verde Stock Disponible / Cobros
    val successLight: Color = Color(0xFFD1FAE5),
    val warning: Color = Color(0xFFF59E0B),             // Ámbar Stock Bajo
    val warningLight: Color = Color(0xFFFEF3C7),
    val error: Color = Color(0xFFEF4444),               // Rojo Agotado / Deuda
    val errorLight: Color = Color(0xFFFEE2E2)
)

val LocalBSPOSColors = staticCompositionLocalOf { BSPOSColors() }
