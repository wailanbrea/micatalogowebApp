package com.example.bspos.presentation.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.bspos.core.ui.theme.BSPOSTheme

@Composable
fun BSPOSAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = BSPOSTheme.colors.surface,
    iconContentColor: Color = BSPOSTheme.colors.primary,
    titleContentColor: Color = BSPOSTheme.colors.textPrimary,
    textContentColor: Color = BSPOSTheme.colors.textSecondary,
    tonalElevation: Dp = 6.dp,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title?.let { content ->
            {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides titleContentColor,
                    androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                ) { content() }
            }
        },
        text = text?.let { content ->
            {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides textContentColor,
                    androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.MaterialTheme.typography.bodyMedium
                ) { content() }
            }
        },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BSPOSModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = BSPOSTheme.colors.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Surface(
                Modifier.padding(top = 10.dp, bottom = 6.dp).width(28.dp).height(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = BSPOSTheme.colors.outline
            ) {}
        },
        content = content
    )
}
