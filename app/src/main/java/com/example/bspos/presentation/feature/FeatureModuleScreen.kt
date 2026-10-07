package com.example.bspos.presentation.feature

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureDefinitionDto
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureModuleDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.FeatureSectionDto

@Composable
fun FeatureModuleScreen(
    feature: String,
    modifier: Modifier = Modifier,
    viewModel: FeatureModuleViewModel = hiltViewModel(),
    onAction: (String) -> Boolean = { false }
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(feature) { viewModel.load(feature) }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BSPOSTheme.colors.primary)
            state.error != null -> FeatureError(state.error!!, onRetry = { viewModel.load(feature) })
            state.response != null -> FeatureContent(
                state.response!!.feature,
                state.response!!.module,
                onAction,
                onExport = { format -> viewModel.exportReport(feature, format); true },
                onRefresh = { viewModel.load(feature) }
            )
        }
    }
}

@Composable
private fun FeatureContent(
    definition: FeatureDefinitionDto,
    module: FeatureModuleDto,
    onAction: (String) -> Boolean,
    onExport: (String) -> Boolean,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var query by remember(definition.title) { mutableStateOf("") }
    val filteredRows = remember(module.rows, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) module.rows else module.rows.filter { row ->
            listOf(row.primary, row.secondary, row.value, row.status)
                .any { it.contains(normalized, ignoreCase = true) }
        }
    }
    var activeSectionKey by remember(module.sections) {
        mutableStateOf(module.sections.firstOrNull()?.key.orEmpty())
    }
    val activeSection = module.sections.firstOrNull { it.key == activeSectionKey }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    if (definition.description.isNotBlank()) {
                        Text(definition.description, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = BSPOSTheme.colors.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            StatusPill(operational = module.kind != "prepared")
        }
        if (module.kpis.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    module.kpis.take(3).forEachIndexed { index, kpi ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(180, delayMillis = index * 55)) + slideInHorizontally { it / 10 }
                        ) { KpiCard(kpi, Modifier.weight(1f)) }
                    }
                }
            }
        }
        module.note?.takeIf { it.isNotBlank() }?.let { note ->
            item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight), modifier = Modifier.animateContentSize()) { Text(note, Modifier.padding(14.dp), color = BSPOSTheme.colors.textPrimary) } }
        }
        if (module.actions.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    module.actions.forEach { action ->
                        val openAction = {
                            val exportFormat = when (action.label.trim().lowercase()) {
                                "exportar csv" -> "csv"
                                "excel" -> "xlsx"
                                else -> null
                            }
                            val handled = exportFormat != null && onExport(exportFormat)
                            if (!handled && !onAction(action.label)) {
                                action.url.toUriOrNull()?.let { uri ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        }
                        if (action.tone.equals("primary", ignoreCase = true)) {
                            Button(onClick = openAction, modifier = Modifier.fillMaxWidth()) {
                                Text(action.label)
                            }
                        } else {
                            OutlinedButton(onClick = openAction, modifier = Modifier.fillMaxWidth()) {
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
        if (module.sections.isNotEmpty()) {
            item {
                ReportSectionTabs(
                    sections = module.sections,
                    activeKey = activeSectionKey,
                    onSelect = { activeSectionKey = it }
                )
            }
            activeSection?.let { section ->
                if (section.kpis.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            section.kpis.take(3).forEach { kpi -> KpiCard(kpi, Modifier.weight(1f)) }
                        }
                    }
                }
                section.note?.takeIf { it.isNotBlank() }?.let { note ->
                    item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) { Text(note, Modifier.padding(14.dp), color = BSPOSTheme.colors.textPrimary) } }
                }
                if (section.rows.isEmpty()) {
                    item { EmptyFeatureRows("Todavía no hay registros en esta sección.") }
                } else {
                    items(section.rows, key = { row -> "${section.key}-${row.primary}-${row.id}" }) { row ->
                        AnimatedVisibility(visible = true, enter = fadeIn() + slideInHorizontally { it / 12 }) { FeatureRow(row) }
                    }
                }
            }
        } else {
            if (module.rows.size >= 4) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Buscar en ${definition.title.lowercase()}") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BSPOSTheme.colors.primary,
                            unfocusedBorderColor = BSPOSTheme.colors.outline,
                            focusedLabelColor = BSPOSTheme.colors.primary
                        )
                    )
                }
            }
            if (filteredRows.isEmpty()) {
                item { EmptyFeatureRows(when {
                    query.isNotBlank() -> "No encontramos coincidencias"
                    module.kind == "prepared" -> "Estamos preparando este módulo"
                    else -> "Todavía no hay registros"
                }, query.isNotBlank()) }
            } else {
                items(filteredRows, key = { row -> "${row.primary}-${row.id}" }) { row ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInHorizontally { it / 12 }) { FeatureRow(row) }
                }
            }
        }
    }
}

@Composable
private fun ReportSectionTabs(
    sections: List<FeatureSectionDto>,
    activeKey: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sections.forEach { section ->
            if (section.key == activeKey) {
                Button(onClick = { onSelect(section.key) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)) { Text(section.label) }
            } else {
                OutlinedButton(onClick = { onSelect(section.key) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)) { Text(section.label) }
            }
        }
    }
}

@Composable
private fun EmptyFeatureRows(title: String, hasQuery: Boolean = false) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(if (hasQuery) "Prueba con otro nombre, código o estado." else "Cuando existan datos, aparecerán aquí sin salir de la aplicación.", color = BSPOSTheme.colors.textSecondary)
        }
    }
}

private fun String.toUriOrNull(): Uri? = runCatching {
    Uri.parse(this).takeIf { it.scheme != null }
}.getOrNull()

@Composable
private fun KpiCard(kpi: FeatureKpiDto, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(kpi.label, style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.height(5.dp))
            Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = toneColor(kpi.tone))
        }
    }
}

@Composable
private fun FeatureRow(row: FeatureRowDto) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.primary.ifBlank { "Registro" }, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                if (row.secondary.isNotBlank()) Text(row.secondary, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (row.value.isNotBlank()) Text(row.value, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                if (row.status.isNotBlank()) Text(row.status, color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StatusPill(operational: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (operational) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (operational) BSPOSTheme.colors.success else BSPOSTheme.colors.warning)
        Spacer(Modifier.width(6.dp))
        Text(if (operational) "Operativo" else "En preparación", fontWeight = FontWeight.Bold, color = if (operational) BSPOSTheme.colors.success else BSPOSTheme.colors.warning)
    }
}

@Composable
private fun FeatureError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, tint = BSPOSTheme.colors.error)
            Spacer(Modifier.height(10.dp))
            Text(message, color = BSPOSTheme.colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onRetry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reintentar") }
        }
    }
}

private fun toneColor(tone: String): Color = when (tone.lowercase()) {
    "emerald", "green" -> Color(0xFF14804A)
    "amber", "orange" -> Color(0xFFB45309)
    "rose", "red" -> Color(0xFFBE123C)
    else -> Color(0xFF1D4ED8)
}
