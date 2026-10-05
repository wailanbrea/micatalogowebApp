package com.example.bspos.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bspos.core.ui.theme.BSPOSTheme

/**
 * Tarjeta base de BSPOS con esquinas redondeadas y elevación sutil.
 */
@Composable
fun BSPOSCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        shape = BSPOSTheme.shapes.cardRadius,
        colors = CardDefaults.cardColors(
            containerColor = BSPOSTheme.colors.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(BSPOSTheme.colors.outline)
        )
    ) {
        content()
    }
}

/**
 * Botón primario azul eléctrico con radio redondeado y controles táctiles amplios.
 */
@Composable
fun BSPOSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = BSPOSTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = BSPOSTheme.colors.primary,
            contentColor = BSPOSTheme.colors.textOnPrimary,
            disabledContainerColor = BSPOSTheme.colors.textTertiary
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(BSPOSTheme.spacing.space8))
        }
        Text(
            text = text,
            style = BSPOSTheme.shapes.let { androidx.compose.material3.MaterialTheme.typography.labelLarge },
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Botón secundario outline con fondo blanco y borde sutil.
 */
@Composable
fun BSPOSOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = BSPOSTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BSPOSTheme.colors.surface,
            contentColor = BSPOSTheme.colors.textPrimary
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = enabled).copy(
            brush = androidx.compose.ui.graphics.SolidColor(BSPOSTheme.colors.outline)
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BSPOSTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(BSPOSTheme.spacing.space8))
        }
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = BSPOSTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Badge de estado de existencia (Normal, Bajo, Sin stock).
 */
@Composable
fun BSPOSStockBadge(
    quantity: Int,
    minimumStock: Int = 5,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, label) = when {
        quantity <= 0 -> Triple(
            BSPOSTheme.colors.errorLight,
            BSPOSTheme.colors.error,
            "Sin stock"
        )
        quantity <= minimumStock -> Triple(
            BSPOSTheme.colors.warningLight,
            BSPOSTheme.colors.warning,
            "Pocas unidades ($quantity)"
        )
        else -> Triple(
            BSPOSTheme.colors.successLight,
            BSPOSTheme.colors.success,
            "En stock ($quantity)"
        )
    }

    Row(
        modifier = modifier
            .clip(BSPOSTheme.shapes.pill)
            .background(backgroundColor)
            .padding(horizontal = BSPOSTheme.spacing.space8, vertical = BSPOSTheme.spacing.space4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(textColor)
        )
        Spacer(modifier = Modifier.width(BSPOSTheme.spacing.space4))
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Tarjeta de KPI para Dashboard y encabezado de POS.
 */
@Composable
fun BSPOSKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    BSPOSCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(BSPOSTheme.spacing.space16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(BSPOSTheme.shapes.medium)
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(BSPOSTheme.spacing.space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = BSPOSTheme.colors.textSecondary
                )
                Text(
                    text = value,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = BSPOSTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = iconTint,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
