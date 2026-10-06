package com.example.bspos.presentation.dayclose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.presentation.finance.FinancePeriodFilter
import com.example.bspos.presentation.finance.FinanceViewModel
import java.time.LocalDate
import kotlin.math.roundToLong

@Composable
fun DayCloseScreen(viewModel: FinanceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var cashDialog by remember { mutableStateOf<CashDialogMode?>(null) }

    LaunchedEffect(Unit) { viewModel.selectPeriod(FinancePeriodFilter.TODAY) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("OPERACIÓN", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Cierre de día", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, tint = BSPOSTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${state.fromDate.ifBlank { LocalDate.now().toString() }} · ${if (state.cashSession?.hasOpenSession == true) "abierto" else "sin sesión abierta"}", color = BSPOSTheme.colors.textSecondary)
                }
            }
        }
        state.errorMessage?.let { error -> item { MessageCard(error, isError = true) } }
        state.successMessage?.let { message -> item { MessageCard(message, isError = false) } }
        if (state.isLoading && state.summary == null) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = BSPOSTheme.colors.primary) } }
        }
        state.summary?.let { summary ->
            val period = summary.period
            item { DayMetricCard("VENTAS COBRADAS", money(period.collectedInPeriod), "${period.salesCount} cobro(s) · Efectivo y otros medios", Icons.Default.PointOfSale, BSPOSTheme.colors.primary) }
            item { DayMetricCard("ABONOS RECIBIDOS", money(summary.cashFlow?.inflows?.debtCollections ?: 0.0), if (period.creditGenerated > 0) "Ventas a crédito: ${money(period.creditGenerated)}" else "No se recibieron abonos.", Icons.Default.AccountBalanceWallet, BSPOSTheme.colors.success) }
            item { DayMetricCard("GASTOS", money(period.operatingExpenses), if (period.operatingExpenses > 0) "Gastos registrados en el período" else "No hubo gastos.", Icons.Default.AccountBalanceWallet, BSPOSTheme.colors.warning) }
            item { DayMetricCard("DEVOLUCIONES", money(period.returns), if (period.returns > 0) "Reembolsos del período" else "No hubo devoluciones.", Icons.Default.ErrorOutline, BSPOSTheme.colors.error) }
        }
        state.cashSession?.let { cash ->
            item {
                CashReconciliationCard(
                    cash = cash,
                    onOpen = { cashDialog = CashDialogMode.OPEN },
                    onClose = { cashDialog = CashDialogMode.CLOSE }
                )
            }
        } ?: item {
            CashReconciliationCard(cash = null, onOpen = { cashDialog = CashDialogMode.OPEN }, onClose = {})
        }
        item {
            Text("Solo el efectivo se cuenta aquí. Transferencias y tarjetas se revisan contra el banco, no contra la gaveta.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text("Después de cerrar no se deben corregir movimientos del día sin autorización del dueño.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }

    cashDialog?.let { mode ->
        CashDialog(
            mode = mode,
            busy = state.isCashOperationBusy,
            onDismiss = { if (!state.isCashOperationBusy) cashDialog = null },
            onConfirm = { amount, notes ->
                if (mode == CashDialogMode.OPEN) viewModel.openRemoteCash(amount, notes) else viewModel.closeRemoteCash(amount, notes)
                cashDialog = null
            }
        )
    }
}

private enum class CashDialogMode { OPEN, CLOSE }

@Composable
private fun DayMetricCard(title: String, amount: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent)
                Spacer(Modifier.width(10.dp))
                Text(title, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(amount, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
            Text(detail, color = BSPOSTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun CashReconciliationCard(
    cash: com.example.bspos.data.micatalogo.dto.CashCurrentSessionResponseDto?,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    val session = cash?.session
    val summary = session?.summary
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (session != null) BSPOSTheme.colors.secondaryNavy else BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (session == null) Icons.Default.LockOpen else Icons.Default.AccountBalanceWallet, null, tint = if (session == null) BSPOSTheme.colors.primary else BSPOSTheme.colors.textOnPrimary)
                Spacer(Modifier.width(10.dp))
                Text("EFECTIVO EN CAJA", color = if (session == null) BSPOSTheme.colors.textSecondary else BSPOSTheme.colors.textOnNavy, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (session == null) {
                Text("No hay una sesión abierta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("Abre caja antes de registrar operaciones en efectivo.", color = BSPOSTheme.colors.textSecondary)
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Abrir caja") }
            } else {
                Text(money(summary?.expectedClosingAmount ?: session.openingAmount), color = BSPOSTheme.colors.textOnPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Deberías tener", color = BSPOSTheme.colors.textOnNavy)
                CashRow("Entró", summary?.totalIn ?: 0.0)
                CashRow("Salió", summary?.totalOut ?: 0.0)
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BSPOSTheme.colors.primary)) { Text("Cerrar el día") }
            }
        }
    }
}

@Composable
private fun CashRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BSPOSTheme.colors.textOnNavy)
        Text(money(amount), color = BSPOSTheme.colors.textOnPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CashDialog(mode: CashDialogMode, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == CashDialogMode.OPEN) "Abrir caja" else "Cerrar el día") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(amount, { amount = it.filter { char -> char.isDigit() || char == '.' }; error = false }, label = { Text("Efectivo contado (${LocalCurrency.current.symbol})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas (opcional)") }, minLines = 2)
                if (error) Text("Indica un monto válido mayor o igual a cero.", color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { if (amount.toBigDecimalOrNull() == null || amount.toBigDecimalOrNull()!! < java.math.BigDecimal.ZERO) error = true else onConfirm(amount, notes.trim().ifBlank { null }) }) { Text(if (busy) "Guardando…" else "Confirmar") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (isError) BSPOSTheme.colors.errorLight else BSPOSTheme.colors.successLight), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null, tint = if (isError) BSPOSTheme.colors.error else BSPOSTheme.colors.success)
            Spacer(Modifier.width(8.dp))
            Text(message, color = BSPOSTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun money(value: Double): String = MoneyUtils.formatCents((value * 100).roundToLong(), LocalCurrency.current)
