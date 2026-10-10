package com.example.bspos.presentation.feature

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.example.bspos.presentation.common.BSPOSButton as Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog

/**
 * Native presentation of Puntto's "Marcas y atributos" workspace.
 *
 * Native presentation of the attribute workspace. Retiring is soft and never
 * deletes product values; renames reject collisions so values stay unambiguous.
 */
@Composable
fun AttributesScreen(
    modifier: Modifier = Modifier,
    viewModel: FeatureModuleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    var editTarget by remember { mutableStateOf<FeatureRowDto?>(null) }
    var retireTarget by remember { mutableStateOf<FeatureRowDto?>(null) }
    LaunchedEffect(Unit) { viewModel.load("attributes") }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = BSPOSTheme.colors.primary
            )
            state.error != null -> AttributesError(
                message = state.error.orEmpty(),
                onRetry = { viewModel.load("attributes") }
            )
            state.response != null -> {
                val response = state.response!!
                AttributesContent(
                    description = response.feature.description,
                    kpis = response.module.kpis,
                    rows = response.module.rows,
                    query = query,
                    onQueryChange = { query = it },
                    filter = filter,
                    onFilterChange = { filter = it },
                    onRefresh = { viewModel.load("attributes") },
                    onEdit = { editTarget = it },
                    onRetire = { retireTarget = it }
                )
            }
        }
    }
    editTarget?.let { row ->
        AttributeEditDialog(
            row = row,
            onDismiss = { editTarget = null },
            onSave = { name, filterable, required ->
                row.id?.let { viewModel.updateAttribute(it, name, filterable, required) }
                editTarget = null
            }
        )
    }
    retireTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { retireTarget = null },
            title = { Text("Retirar atributo") },
            text = { Text("${row.primary} dejará de mostrarse en el catálogo de atributos. Sus valores históricos se conservarán.") },
            confirmButton = {
                Button(onClick = {
                    row.id?.let { viewModel.updateAttribute(it, row.primary, row.filterable, row.status.equals("Obligatorio", true), false) }
                    retireTarget = null
                }) { Text("Retirar") }
            },
            dismissButton = { TextButton(onClick = { retireTarget = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AttributesContent(
    description: String,
    kpis: List<FeatureKpiDto>,
    rows: List<FeatureRowDto>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onEdit: (FeatureRowDto) -> Unit,
    onRetire: (FeatureRowDto) -> Unit
) {
    val filteredRows = remember(rows, query, filter) {
        val normalized = query.trim()
        rows.filter { row ->
            val matchesFilter = when (filter) {
                "Obligatorios" -> row.status.equals("Obligatorio", ignoreCase = true)
                "Filtrables" -> row.filterable
                else -> true
            }
            val matchesQuery = normalized.isBlank() || listOf(
                row.primary, row.secondary, row.value, row.status
            ).any { it.contains(normalized, ignoreCase = true) }
            matchesFilter && matchesQuery
        }
    }
    val groups = remember(filteredRows) {
        filteredRows.groupBy { it.secondary.ifBlank { "Todas las categorías" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        description.ifBlank { "Organiza los nombres que describen tus productos." },
                        color = BSPOSTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = BSPOSTheme.colors.primary)
                }
            }
        }
        if (kpis.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    kpis.take(3).forEach { kpi -> KpiCard(kpi, Modifier.weight(1f)) }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Buscar un nombre…") },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Todos", "Obligatorios", "Filtrables").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilterChange(option) },
                        label = { Text(option, maxLines = 1) }
                    )
                }
            }
        }
        if (groups.isEmpty()) {
            item {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("No encontramos atributos", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("Prueba con otro nombre o filtro.", color = BSPOSTheme.colors.textSecondary)
                    }
                }
            }
        } else {
            groups.forEach { (group, groupRows) ->
                item(key = "header-$group") {
                    Text(
                        group,
                        modifier = Modifier.padding(top = 4.dp),
                        fontWeight = FontWeight.ExtraBold,
                        color = BSPOSTheme.colors.textPrimary
                    )
                }
                items(groupRows, key = { row -> "${group}-${row.id}-${row.primary}" }) { row ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInHorizontally { it / 12 }) {
                        AttributeCard(
                            row = row,
                            onEdit = { onEdit(row) },
                            onRetire = { onRetire(row) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttributeCard(row: FeatureRowDto, onEdit: () -> Unit, onRetire: () -> Unit) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(row.primary.ifBlank { "Atributo" }, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                Text(row.secondary.ifBlank { "Todas las categorías" }, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                Text(
                    row.value.ifBlank { "Texto" },
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                row.status.ifBlank { "Opcional" },
                color = if (row.status.equals("Obligatorio", ignoreCase = true)) Color(0xFFB45309) else Color(0xFF14804A),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onEdit) { Text("Cambiar nombre") }
            TextButton(onClick = onRetire) { Text("Retirar", color = BSPOSTheme.colors.error) }
        }
    }
}

@Composable
private fun AttributeEditDialog(
    row: FeatureRowDto,
    onDismiss: () -> Unit,
    onSave: (String, Boolean, Boolean) -> Unit
) {
    var name by remember(row.id) { mutableStateOf(row.primary) }
    var filterable by remember(row.id) { mutableStateOf(row.filterable) }
    var required by remember(row.id) { mutableStateOf(row.status.equals("Obligatorio", ignoreCase = true)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar nombre") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre del atributo") }
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Filtrable en la tienda")
                    Switch(checked = filterable, onCheckedChange = { filterable = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Obligatorio al crear productos")
                    Switch(checked = required, onCheckedChange = { required = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.trim().isNotBlank()) onSave(name.trim(), filterable, required) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun KpiCard(kpi: FeatureKpiDto, modifier: Modifier = Modifier) {
    Card(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(kpi.label, style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.height(5.dp))
            Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1D4ED8))
        }
    }
}

@Composable
private fun AttributesError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = BSPOSTheme.colors.error)
            Spacer(Modifier.height(10.dp))
            Text(message, color = BSPOSTheme.colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text("Reintentar")
            }
        }
    }
}
