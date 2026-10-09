package com.example.bspos.presentation.support

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.example.bspos.core.ui.theme.BSPOSTheme

@Composable
fun SupportFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.padding(end = 18.dp, bottom = 18.dp),
        containerColor = BSPOSTheme.colors.textPrimary,
        contentColor = BSPOSTheme.colors.surface,
        shape = RoundedCornerShape(50)
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Abrir chat con nosotros")
    }
}
