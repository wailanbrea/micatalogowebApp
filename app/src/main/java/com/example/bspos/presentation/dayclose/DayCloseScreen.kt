package com.example.bspos.presentation.dayclose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.finance.FinancePeriodFilter
import com.example.bspos.presentation.finance.FinanceUiState
import com.example.bspos.presentation.finance.FinanceViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun DayCloseScreen(viewModel: FinanceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.selectPeriod(FinancePeriodFilter.TODAY) }
    DayCloseContent(state, { viewModel.setCustomPeriod(it, it) }, viewModel::closeDaily, viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayCloseContent(state: FinanceUiState, onDate: (String) -> Unit,
    onClose: (String?, String?) -> Unit, onRefresh: () -> Unit) {
    var dialog by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    val date = state.fromDate.ifBlank { LocalDate.now().toString() }
    // Do not display a previous day's response under the selected date.
    val close = state.dailyClose?.takeIf { it.businessDate == date }
    val summary = state.summary?.takeIf { it.from == date && it.to == date }
    val closure = close?.closure
    LaunchedEffect(state.successMessage, state.isDailyCloseBusy) {
        if (!state.isDailyCloseBusy && state.successMessage == "Cierre diario guardado.") dialog = false
    }
    LazyColumn(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).testTag("day-close-list"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cierre de día", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                Text("$date · ${if (closure != null) "cerrado" else if (close == null) "consultando" else "pendiente"}",
                    fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
                OutlinedButton(onClick = { datePicker = true }, enabled = !state.isDailyCloseBusy,
                    shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.widthIn(min = 176.dp).heightIn(min = 48.dp).testTag("day-close-date")) {
                    Text(runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(date), fontSize = 12.sp)
                    Spacer(Modifier.width(32.dp))
                    Icon(Icons.Default.CalendarToday, "Seleccionar fecha", Modifier.size(16.dp))
                }
            }
        }
        state.errorMessage?.let { error -> item {
            CloseCard { Text(error, color = BSPOSTheme.colors.error); TextButton(onClick = onRefresh) { Text("Reintentar") } }
        } }
        state.successMessage?.let { message -> item { Text(message, color = BSPOSTheme.colors.success) } }
        if (close == null) item {
            if (state.isLoading) CircularProgressIndicator(Modifier.size(24.dp))
            else Text("No hay un resumen disponible para esta fecha. Reintenta la consulta.", color = BSPOSTheme.colors.textSecondary)
        }
        if (close != null) {
            val sales = close.salesCash + close.salesCard + close.salesTransfer + close.salesOther
            item { MetricCard("Ventas cobradas", sales, if (sales == 0.0) "No hubo ventas cobradas." else "Cobrado por todos los medios de pago.") }
            item { MetricCard("Abonos recibidos", close.debtCollectionsTotal,
                if (close.debtCollectionsTotal == 0.0) "No se recibieron abonos." else "Abonos recibidos por todos los medios.") }
            item { MetricCard("Gastos", close.expensesPaidTotal,
                if (close.expensesPaidTotal == 0.0) "No hubo gastos pagados." else "Gastos pagados en la fecha seleccionada.") }
            item { MetricCard("Devoluciones", summary?.period?.returns,
                when (summary?.period?.returns) { null -> "Resumen de devoluciones no disponible."; 0.0 -> "No hubo devoluciones."; else -> "Importe de devoluciones del día." }) }
            item {
                CloseCard {
                    Caption("Efectivo en caja")
                    AmountBlock("Entró", close.salesCash + close.debtCollectionsCash + close.otherInflowsCash)
                    AmountBlock("Salió", close.expensesCash + close.cashOut)
                    AmountBlock("Deberías tener", close.expectedCash, prominent = true)
                    Text("Solo el efectivo se cuenta aquí. Transferencias y tarjeta se revisan contra el banco, no contra la gaveta.",
                        fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary)
                }
            }
            item {
                CloseCard {
                    Caption("Otros medios")
                    AmountBlock("Tarjeta", close.salesCard)
                    AmountBlock("Transferencia", close.salesTransfer)
                    AmountBlock("Otros", close.salesOther)
                }
            }
            item {
                CloseCard {
                    Surface(shape = RoundedCornerShape(20.dp), color = BSPOSTheme.colors.surfaceVariant) {
                        Text(if (closure != null) "Cerrado" else "Pendiente", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp)
                    }
                    closure?.closedAt?.let { timestamp ->
                        val local = runCatching { Instant.parse(timestamp).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")) }.getOrDefault(timestamp)
                        Text("Registrado: $local", fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
                    }
                    AmountBlock("Esperado", closure?.expectedCash ?: close.expectedCash)
                    Text("Contado", fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
                    if (closure?.countedCash != null) MoneyText(closure.countedCash, 16) else Text("Sin arqueo", fontSize = 13.sp)
                    Text("Diferencia", fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
                    if (closure?.difference != null) MoneyText(closure.difference, 16) else Text("—")
                    closure?.notes?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp) }
                    if (closure == null) Button(onClick = { dialog = true }, enabled = !state.isDailyCloseBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("day-close-save"), shape = RoundedCornerShape(12.dp)) {
                        Text("Guardar cierre diario")
                    }
                    else Text("Cierre registrado. La reapertura no está disponible en la API actual.", fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary)
                }
            }
            if (closure != null) item {
                CloseCard {
                    Caption("Cierre registrado")
                    Text(closure.businessDate, fontWeight = FontWeight.Bold)
                    AmountBlock("Esperado", closure.expectedCash)
                    AmountBlock("Contado", closure.countedCash)
                    AmountBlock("Diferencia", closure.difference)
                }
            }
        }
    }
    if (dialog) DailyCloseDialog(state.isDailyCloseBusy, { if (!state.isDailyCloseBusy) dialog = false }, onClose)
    if (datePicker) {
        val initial = runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; datePicker = false }) { Text("Aplicar") }
        }, dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancelar") } }) {
            DatePicker(picker, title = { Text("Selecciona el día", Modifier.padding(16.dp)) })
        }
    }
}

@Composable
private fun CloseCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace, color = BSPOSTheme.colors.textSecondary)
}

@Composable
private fun MetricCard(label: String, amount: Double?, description: String) {
    CloseCard { Caption(label); if (amount != null) MoneyText(amount, 21) else Text("—", fontSize = 21.sp)
        Text(description, fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary) }
}

@Composable
private fun AmountBlock(label: String, amount: Double?, prominent: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
        if (amount != null) MoneyText(amount, if (prominent) 20 else 16) else Text("—")
    }
}

@Composable
private fun MoneyText(value: Double, size: Int) {
    Text(MoneyUtils.formatCents(BigDecimal.valueOf(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact(), LocalCurrency.current),
        fontSize = size.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, color = BSPOSTheme.colors.textPrimary)
}

internal fun validCountedCash(value: String): Boolean = value.isBlank() ||
    (Regex("[0-9]+(?:\\.[0-9]{1,2})?").matches(value) && value.toBigDecimalOrNull()?.let { it >= BigDecimal.ZERO } == true)

@Composable
private fun DailyCloseDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: (String?, String?) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Guardar cierre diario") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(amount, { amount = it.filter { char -> char in '0'..'9' || char == '.' }; attempted = false },
                modifier = Modifier.fillMaxWidth().testTag("day-close-counted"), label = { Text("Efectivo contado · opcional") }, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = attempted && !validCountedCash(amount))
            Text("Si lo dejas vacío, se guarda como cerrado sin arqueo.", fontSize = 12.sp)
            OutlinedTextField(notes, { notes = it.take(500) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Nota (opcional)") }, minLines = 2)
            if (attempted && !validCountedCash(amount)) Text("Indica un monto válido con hasta dos decimales.", color = BSPOSTheme.colors.error)
        }
    }, confirmButton = {
        TextButton(enabled = !busy, onClick = { attempted = true; if (validCountedCash(amount)) onConfirm(amount.trim().ifBlank { null }, notes.trim().ifBlank { null }) }) {
            Text(if (busy) "Guardando…" else "Guardar")
        }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}
