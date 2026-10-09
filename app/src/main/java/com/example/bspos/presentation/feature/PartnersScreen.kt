package com.example.bspos.presentation.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog

@Composable
fun PartnersScreen(
    modifier: Modifier = Modifier,
    viewModel: FeatureModuleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var transactionPartner by remember { mutableStateOf<FeatureRowDto?>(null) }
    LaunchedEffect(Unit) { viewModel.load("partners") }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BSPOSTheme.colors.primary)
            state.error != null -> PartnerError(state.error.orEmpty()) { viewModel.load("partners") }
            state.response != null -> {
                val module = state.response!!.module
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { viewModel.load("partners") }) { Icon(Icons.Default.Refresh, "Actualizar", tint = BSPOSTheme.colors.primary) }
                        }
                    }
                    item {
                        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text("Agregar socio")
                        }
                    }
                    if (module.kpis.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                module.kpis.take(3).forEach { kpi -> PartnerKpi(kpi, Modifier.weight(1f)) }
                            }
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Cómo se calcula", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                                Text("Ganancia bruta − gastos y pérdidas del período = ganancia neta.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (module.rows.isEmpty()) {
                        item { Text("Todavía no hay socios.", color = BSPOSTheme.colors.textSecondary) }
                    } else {
                        items(module.rows, key = { it.id ?: it.primary }) { row ->
                            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(row.primary, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                                            Text(row.secondary, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(row.value, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(onClick = { transactionPartner = row }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                                        Text("Registrar movimiento")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AddPartnerDialog(
            onDismiss = { showCreate = false },
            onSave = { name, email, phone, percent ->
                viewModel.createPartner(name, email, phone, percent)
                showCreate = false
            }
        )
    }
    transactionPartner?.let { partner ->
        PartnerTransactionDialog(
            partner = partner,
            onDismiss = { transactionPartner = null },
            onSave = { type, amount, notes ->
                partner.id?.let { viewModel.recordPartnerTransaction(it, type, amount, notes) }
                transactionPartner = null
            }
        )
    }
}

@Composable
private fun AddPartnerDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var percent by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar socio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Correo (opcional)") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono (opcional)") }, singleLine = true)
                OutlinedTextField(percent, { percent = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text("Participación %") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { Button(onClick = { onSave(name, email, phone, percent) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PartnerTransactionDialog(partner: FeatureRowDto, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var type by remember { mutableStateOf("contribution") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Movimiento · ${partner.primary}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == "contribution", { type = "contribution" }, label = { Text("Aporte") })
                    FilterChip(type == "withdrawal", { type = "withdrawal" }, label = { Text("Retiro") })
                    FilterChip(type == "distribution", { type = "distribution" }, label = { Text("Reparto") })
                }
                OutlinedTextField(amount, { amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text("Monto") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(notes, { notes = it }, label = { Text("Nota (opcional)") }, minLines = 2)
                Text("La caja debe estar abierta para registrar el movimiento.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(type, amount.replace(',', '.'), notes) }, enabled = amount.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PartnerKpi(kpi: FeatureKpiDto, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(kpi.label, style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.height(5.dp))
            Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary)
        }
    }
}

@Composable
private fun PartnerError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, tint = BSPOSTheme.colors.error)
            Spacer(Modifier.height(10.dp))
            Text(message, color = BSPOSTheme.colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onRetry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.padding(horizontal = 3.dp)); Text("Reintentar") }
        }
    }
}
