package com.example.bspos.presentation.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.example.bspos.presentation.common.BSPOSButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureRowDto

@Composable
fun AccountantScreen(
    modifier: Modifier = Modifier,
    viewModel: FeatureModuleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.load("accountant") }

    when {
        state.loading -> androidx.compose.foundation.layout.Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BSPOSTheme.colors.primary)
        }
        state.error != null -> FeatureError(state.error.orEmpty(), onRetry = { viewModel.load("accountant") })
        state.response != null -> {
            val response = state.response!!
            LazyColumn(
                modifier = modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Acceso de solo lectura a ventas, gastos, reportes y facturas.", Modifier.weight(1f), color = BSPOSTheme.colors.textSecondary)
                        IconButton(onClick = { viewModel.load("accountant") }) { Icon(Icons.Default.Refresh, "Actualizar", tint = BSPOSTheme.colors.primary) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        response.module.kpis.take(3).forEach { kpi -> FeatureKpiCard(kpi, Modifier.weight(1f)) }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Dar acceso", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                            Text("El contador no puede vender, editar productos ni modificar la caja. Este acceso no consume un usuario regular.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Correo del contable") })
                                Button(onClick = { viewModel.grantAccountantAccess(email); email = "" }, enabled = email.trim().contains("@")) { Text("Dar acceso") }
                            }
                        }
                    }
                }
                item { Text("Resumen financiero", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary) }
                items(response.module.rows, key = { it.id ?: "${it.primary}-${it.status}" }) { row -> AccountantRow(row) }
            }
        }
    }
}

@Composable
private fun AccountantRow(row: FeatureRowDto) {
    Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.primary.ifBlank { "Registro financiero" }, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                if (row.secondary.isNotBlank()) Text(row.secondary, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (row.value.isNotBlank()) Text(row.value, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                if (row.status.isNotBlank()) Text(row.status, color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
