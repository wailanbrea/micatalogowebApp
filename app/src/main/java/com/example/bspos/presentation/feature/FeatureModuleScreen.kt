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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureDefinitionDto
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureModuleDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto

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
            state.response != null -> FeatureContent(state.response!!.feature, state.response!!.module, onAction)
        }
    }
}

@Composable
private fun FeatureContent(definition: FeatureDefinitionDto, module: FeatureModuleDto, onAction: (String) -> Boolean) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            if (definition.group.isNotBlank()) {
                Text(definition.group.uppercase(), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            if (definition.title.isNotBlank()) {
                Text(definition.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
            }
            Text(definition.description, color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.height(4.dp))
            StatusPill(operational = module.kind != "prepared")
        }
        if (module.kpis.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    module.kpis.take(3).forEach { kpi -> KpiCard(kpi, Modifier.weight(1f)) }
                }
            }
        }
        module.note?.takeIf { it.isNotBlank() }?.let { note ->
            item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight), modifier = Modifier.animateContentSize()) { Text(note, Modifier.padding(14.dp), color = BSPOSTheme.colors.textPrimary) } }
        }
        if (module.actions.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    module.actions.forEach { action ->
                        val openAction = {
                            if (!onAction(action.label)) {
                                action.url.toUriOrNull()?.let { uri ->
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        }
                        if (action.tone.equals("primary", ignoreCase = true)) {
                            Button(onClick = openAction, modifier = Modifier.weight(1f), enabled = action.url.isNotBlank()) {
                                Text(action.label)
                            }
                        } else {
                            OutlinedButton(onClick = openAction, modifier = Modifier.weight(1f), enabled = action.url.isNotBlank()) {
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
        if (module.rows.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(if (module.kind == "prepared") "Estamos preparando este módulo" else "Todavía no hay registros", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Cuando existan datos, aparecerán aquí sin salir de la aplicación.", color = BSPOSTheme.colors.textSecondary)
                    }
                }
            }
        } else {
            items(module.rows, key = { row -> "${row.primary}-${row.id}" }) { row ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInHorizontally { it / 12 }
                ) { FeatureRow(row) }
            }
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
