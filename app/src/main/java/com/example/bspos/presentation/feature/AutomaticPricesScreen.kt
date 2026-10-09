package com.example.bspos.presentation.feature

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto

@Composable
fun AutomaticPricesScreen(
    modifier: Modifier = Modifier,
    showSensitiveFinance: Boolean = true,
    viewModel: FeatureModuleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load("pricing") }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = BSPOSTheme.colors.primary
            )
            state.error != null -> PricingError(state.error.orEmpty()) { viewModel.load("pricing") }
            state.response != null -> AutomaticPricesContent(
                description = state.response!!.feature.description,
                kpis = state.response!!.module.kpis,
                rows = state.response!!.module.rows,
                showSensitiveFinance = showSensitiveFinance,
                onRefresh = { viewModel.load("pricing") },
                onRecalculate = viewModel::recalculatePricing,
                onSave = viewModel::savePricingRule,
                onApprove = viewModel::approvePricing
            )
        }
    }
}

@Composable
private fun AutomaticPricesContent(
    description: String,
    kpis: List<FeatureKpiDto>,
    rows: List<FeatureRowDto>,
    showSensitiveFinance: Boolean,
    onRefresh: () -> Unit,
    onRecalculate: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit,
    onApprove: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(description.ifBlank { "Reglas que recalculan tus precios cuando cambia el costo." }, color = BSPOSTheme.colors.textSecondary)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Actualizar", tint = BSPOSTheme.colors.primary) }
            }
                if (showSensitiveFinance) {
                    OutlinedButton(onClick = onRecalculate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Recalcular ahora")
                    }
                }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) {
                Text(
                    if (showSensitiveFinance) {
                        "Cada lote conserva su costo. Las subidas automáticas pueden aplicarse al recibir mercancía y las bajadas siempre esperan tu aprobación."
                    } else {
                        "La configuración de márgenes, costos y precios propuestos está reservada al propietario o al equipo financiero."
                    },
                    Modifier.padding(14.dp),
                    color = BSPOSTheme.colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (kpis.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    kpis.take(3).forEach { kpi -> PricingKpi(kpi, Modifier.weight(1f)) }
                }
            }
        }
        if (rows.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Text("Sin productos disponibles para configurar reglas.", Modifier.padding(18.dp), color = BSPOSTheme.colors.textSecondary)
                }
            }
        } else {
            items(rows, key = { row -> row.productId ?: row.id ?: row.primary }) { row ->
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInHorizontally { it / 12 }) {
                    if (showSensitiveFinance) {
                        PricingRuleCard(row, onSave, onApprove)
                    } else {
                        RestrictedPricingRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestrictedPricingRow(row: FeatureRowDto) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.primary.ifBlank { "Producto" }, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.value.isNotBlank()) Text(row.value, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PricingRuleCard(
    row: FeatureRowDto,
    onSave: (String, String, String, Boolean) -> Unit,
    onApprove: (String, String) -> Unit
) {
    val productId = row.productId ?: row.id
    var margin by remember(row.id, row.marginPercent) { mutableStateOf(row.marginPercent ?: "40") }
    var roundStep by remember(row.id, row.roundStep) { mutableStateOf(row.roundStep ?: "1.00") }
    var autoIncrease by remember(row.id, row.autoIncrease) { mutableStateOf(row.autoIncrease) }
    var error by remember(row.id) { mutableStateOf<String?>(null) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(row.primary.ifBlank { "Producto" }, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (row.secondary.isNotBlank()) Text(row.secondary, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    if (row.value.isNotBlank()) Text(row.value, color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
                Text(row.status.ifBlank { "Sin regla" }, color = if (row.pendingPrice != null) BSPOSTheme.colors.warning else BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = margin,
                    onValueChange = { margin = decimalInput(it); error = null },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Margen %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = roundStep,
                    onValueChange = { roundStep = decimalInput(it); error = null },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Redondeo RD$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Aplicar subidas automáticamente", Modifier.weight(1f), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                Switch(checked = autoIncrease, onCheckedChange = { autoIncrease = it })
            }
            error?.let { Text(it, color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    val marginValue = margin.replace(',', '.').toDoubleOrNull()
                    val roundValue = roundStep.replace(',', '.').toDoubleOrNull()
                    when {
                        productId.isNullOrBlank() -> error = "Este producto no tiene identificador remoto."
                        marginValue == null || marginValue !in 0.0..95.0 -> error = "El margen debe estar entre 0 y 95."
                        roundValue == null || roundValue <= 0.0 -> error = "El redondeo debe ser mayor que 0."
                        else -> onSave(productId, margin.replace(',', '.'), roundStep.replace(',', '.'), autoIncrease)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Guardar regla") }
            row.pendingPrice?.takeIf { it.isNotBlank() }?.let { pending ->
                OutlinedButton(onClick = { if (!productId.isNullOrBlank()) onApprove(productId, pending) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Aprobar precio propuesto · RD$ $pending")
                }
            }
        }
    }
}

private fun decimalInput(value: String): String = value.filter { it.isDigit() || it == '.' || it == ',' }.take(12)

@Composable
private fun PricingKpi(kpi: FeatureKpiDto, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(kpi.label, style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.height(5.dp))
            Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary)
        }
    }
}

@Composable
private fun PricingError(message: String, onRetry: () -> Unit) {
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
