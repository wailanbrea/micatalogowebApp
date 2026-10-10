package com.example.bspos.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.example.bspos.core.ui.theme.BSPOSFonts as FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bspos.core.ui.theme.BSPOSTheme

object BSPOSDesign {
    val screenMargin = 12.dp
    val cardPadding = 16.dp
    val controlRadius = RoundedCornerShape(8.dp)
    val cardRadius = RoundedCornerShape(16.dp)
    val sheetRadius = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
}

@Composable
internal fun modalMaxHeight(): androidx.compose.ui.unit.Dp {
    var context = androidx.compose.ui.platform.LocalContext.current
    while (context is android.content.ContextWrapper && context !is android.app.Activity && context.baseContext !== context) context = context.baseContext
    val pixels = if (context is android.app.Activity && android.os.Build.VERSION.SDK_INT >= 30)
        context.windowManager.currentWindowMetrics.bounds.height() else context.resources.displayMetrics.heightPixels
    return with(androidx.compose.ui.platform.LocalDensity.current) { pixels.toDp() * .90f }
}

internal val LocalDialogButtonPrimary = compositionLocalOf<Boolean?> { null }
private fun controlShape(requested: Shape): Shape = if (requested == RoundedCornerShape(50)) requested else BSPOSDesign.controlRadius

@Composable
fun BSPOSButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = BSPOSDesign.controlRadius,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    Button(onClick, modifier.heightIn(min = 44.dp), enabled, controlShape(shape), colors, elevation, border, contentPadding, interactionSource) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) { content() }
    }
}

@Composable
fun BSPOSOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = BSPOSDesign.controlRadius,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = BSPOSTheme.colors.textPrimary),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.dp, BSPOSTheme.colors.outline),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(onClick, modifier.heightIn(min = 44.dp), enabled, controlShape(shape), colors, elevation, border, contentPadding, interactionSource) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) { content() }
    }
}

@Composable
fun BSPOSActionTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = BSPOSDesign.controlRadius, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    when (LocalDialogButtonPrimary.current) {
        true -> BSPOSButton(onClick, modifier, enabled, shape = shape, contentPadding = contentPadding, content = content)
        false -> BSPOSOutlinedButton(onClick, modifier, enabled, shape = shape, contentPadding = contentPadding, content = content)
        null -> TextButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
    }
}

@Composable
fun BSPOSSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = BSPOSTheme.colors.textSecondary, fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, letterSpacing = 1.5.sp, lineHeight = 16.sp)
}

@Composable
fun BSPOSMoney(text: String, modifier: Modifier = Modifier, prominent: Boolean = false) {
    Text(text, modifier, color = BSPOSTheme.colors.textPrimary, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold, fontSize = if (prominent) 20.sp else 16.sp)
}

@Composable
fun BSPOSSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, onSearch: () -> Unit = {}) {
    Surface(modifier.height(46.dp), shape = BSPOSDesign.controlRadius,
        color = BSPOSTheme.colors.surfaceVariant, border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.width(8.dp))
            BasicTextField(value, onValueChange, Modifier.weight(1f), singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = BSPOSTheme.colors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                cursorBrush = SolidColor(BSPOSTheme.colors.primary), decorationBox = { input ->
                    Box { if (value.isEmpty()) Text(placeholder, color = BSPOSTheme.colors.textSecondary, maxLines = 1); input() }
                })
        }
    }
}

@Composable
fun BSPOSCompactSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.size(48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(width = 30.dp, height = 18.dp), shape = RoundedCornerShape(50),
            color = if (checked) BSPOSTheme.colors.success else BSPOSTheme.colors.outline) {
            Box(Modifier.fillMaxSize().padding(2.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
                Surface(Modifier.size(14.dp), shape = RoundedCornerShape(50), color = BSPOSTheme.colors.textOnPrimary) {}
            }
        }
    }
}

@Composable
fun BSPOSAmountField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, readOnly: Boolean = false) {
    BasicTextField(value, onValueChange, modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = label },
        enabled = enabled, readOnly = readOnly, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontSize = 18.sp,
            fontWeight = FontWeight.Medium, color = BSPOSTheme.colors.textPrimary), cursorBrush = SolidColor(BSPOSTheme.colors.primary),
        decorationBox = { input ->
            Surface(shape = RoundedCornerShape(10.dp), color = BSPOSTheme.colors.surfaceVariant) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.example.bspos.core.money.LocalCurrency.current.symbol, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = BSPOSTheme.colors.textSecondary)
                    Spacer(Modifier.width(8.dp)); Box(Modifier.weight(1f)) { input() }
                }
            }
        })
}
