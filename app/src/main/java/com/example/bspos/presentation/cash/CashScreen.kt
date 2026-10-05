package com.example.bspos.presentation.cash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.presentation.common.DialogScrollableColumn
import java.util.Locale

@Composable
fun CashScreen(
    onCashOpened: (() -> Unit)? = null,
    viewModel: CashViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState(); val expected by viewModel.expected.collectAsState(); var opening by remember { mutableStateOf(false) }; var closing by remember { mutableStateOf(false) }; var returnToSaleAfterOpening by remember { mutableStateOf(false) }
    LaunchedEffect(session, returnToSaleAfterOpening) {
        if (returnToSaleAfterOpening && session != null) {
            returnToSaleAfterOpening = false
            onCashOpened?.invoke()
        }
    }
    Column(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(BSPOSTheme.colors.successLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = BSPOSTheme.colors.success) }; Spacer(Modifier.width(12.dp)); Column { Text("Control de efectivo y arqueo", color = BSPOSTheme.colors.textSecondary) } }
        Spacer(Modifier.height(22.dp)); session?.let { current ->
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.secondaryNavy)) { Column(Modifier.padding(22.dp)) { Text("Sesion abierta", color = BSPOSTheme.colors.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(money(expected ?: current.openingAmount), color = BSPOSTheme.colors.textOnPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold); Text("Esperado actual", color = BSPOSTheme.colors.textOnNavy); Spacer(Modifier.height(16.dp)); HorizontalDivider(color = BSPOSTheme.colors.secondaryNavySurface); Spacer(Modifier.height(12.dp)); Text("Fondo inicial: ${money(current.openingAmount)}", color = BSPOSTheme.colors.textOnNavy); Button({ closing = true }, Modifier.fillMaxWidth().padding(top = 18.dp), colors = ButtonDefaults.buttonColors(containerColor = BSPOSTheme.colors.primary)) { Text("Cerrar y realizar arqueo") } } }
        } ?: Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.LockOpen, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(44.dp)); Spacer(Modifier.height(12.dp)); Text("No hay una sesion abierta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text("Abre caja antes de registrar operaciones en efectivo", color = BSPOSTheme.colors.textSecondary); Spacer(Modifier.height(18.dp)); Button({ opening = true }, Modifier.fillMaxWidth()) { Text("Abrir caja") } } }
    }
    if (opening) CashAmountDialog("Abrir caja", { amount, notes -> returnToSaleAfterOpening = onCashOpened != null; viewModel.open(amount, notes); opening = false }, { opening = false }); if (closing) CashAmountDialog("Cerrar caja", { amount, notes -> viewModel.close(amount, notes); closing = false }, { closing = false })
}

@Composable private fun CashAmountDialog(title: String, onSave: (Long, String?) -> Unit, onDismiss: () -> Unit) { var amount by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var amountError by remember { mutableStateOf(false) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { DialogScrollableColumn { OutlinedTextField(amount, { amount = it.filter(Char::isDigit); amountError = false }, label = { Text("Monto (${LocalCurrency.current.symbol})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = amountError, supportingText = { if (amountError) Text("Indica un monto válido mayor o igual a cero") }); OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }) } }, confirmButton = { TextButton({ val value = MoneyUtils.parseWholeUnitsToCents(amount); if (value == null) amountError = true else onSave(value, notes.ifBlank { null }) }) { Text("Confirmar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }) }
@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)
