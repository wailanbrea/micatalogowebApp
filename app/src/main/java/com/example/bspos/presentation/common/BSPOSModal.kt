package com.example.bspos.presentation.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
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

@OptIn(ExperimentalMaterial3Api::class)
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
    BSPOSModalBottomSheet(onDismissRequest = onDismissRequest,
        properties = androidx.compose.material3.ModalBottomSheetProperties(securePolicy = properties.securePolicy,
            shouldDismissOnBackPress = properties.dismissOnBackPress, shouldDismissOnClickOutside = properties.dismissOnClickOutside)) {
        Column(modifier.fillMaxWidth().heightIn(max = modalMaxHeight()).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.invoke()
                if (icon != null) Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides titleContentColor,
                    androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                ) { title?.invoke() }
                }
                IconButton(onDismissRequest) { Icon(Icons.Default.Close, "Cerrar") }
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides textContentColor,
                    androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.MaterialTheme.typography.bodyMedium
                ) { text?.invoke() }
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                CompositionLocalProvider(LocalDialogButtonPrimary provides false) { dismissButton?.invoke() }
                Spacer(Modifier.width(8.dp))
                CompositionLocalProvider(LocalDialogButtonPrimary provides true) { confirmButton() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BSPOSModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    properties: androidx.compose.material3.ModalBottomSheetProperties = androidx.compose.material3.ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        properties = properties,
        containerColor = BSPOSTheme.colors.surface,
        shape = BSPOSDesign.sheetRadius,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = .35f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Surface(
                Modifier.padding(top = 10.dp, bottom = 6.dp).width(32.dp).height(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = BSPOSTheme.colors.outline
            ) {}
        },
        content = content
    )
}
