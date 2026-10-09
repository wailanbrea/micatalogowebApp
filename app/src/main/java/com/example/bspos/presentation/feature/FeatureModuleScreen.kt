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
import androidx.compose.material3.FilterChip
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
import com.example.bspos.data.micatalogo.dto.AuthorizationRequestDto
import com.example.bspos.data.micatalogo.dto.OrderConfirmRequestDto
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog

@Composable
fun FeatureModuleScreen(
    feature: String,
    modifier: Modifier = Modifier,
    viewModel: FeatureModuleViewModel = hiltViewModel(),
    showSensitiveFinance: Boolean = true,
    onAction: (String) -> Boolean = { false }
) {
    val state by viewModel.state.collectAsState()
    var commissionPeriod by remember(feature) { mutableStateOf("current_fortnight") }
    var salesPeriod by remember(feature) { mutableStateOf("today") }
    var salesStatus by remember(feature) { mutableStateOf("Todos") }
    LaunchedEffect(feature, commissionPeriod, salesPeriod, salesStatus) {
        val period = when (feature) {
            "commissions" -> commissionPeriod
            "sales" -> salesPeriod
            else -> null
        }
        val status = when (salesStatus) {
            "Pagadas" -> "paid"
            "A crédito" -> "credit"
            "Parciales" -> "partial"
            "Anuladas" -> "void"
            else -> null
        }.takeIf { feature == "sales" }
        viewModel.load(feature, period, status)
    }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BSPOSTheme.colors.primary)
            state.error != null -> FeatureError(state.error!!, onRetry = { viewModel.load(feature) })
            state.response != null -> FeatureContent(
                feature = feature,
                definition = state.response!!.feature,
                module = state.response!!.module,
                showSensitiveFinance = showSensitiveFinance,
                onAction = onAction,
                onConfirmOrder = { orderId, request -> viewModel.confirmOrder(orderId, request, feature) },
                onAuthorizationDecision = { requestId, approve -> viewModel.decideAuthorization(requestId, approve) },
                onExport = { format -> viewModel.exportReport(feature, format); true },
                onRefresh = {
                    viewModel.load(feature, when (feature) {
                        "commissions" -> commissionPeriod
                        "sales" -> salesPeriod
                        else -> null
                    }, when (salesStatus) {
                        "Pagadas" -> "paid"
                        "A crédito" -> "credit"
                        "Parciales" -> "partial"
                        "Anuladas" -> "void"
                        else -> null
                    }.takeIf { feature == "sales" })
                },
                commissionPeriod = commissionPeriod,
                onCommissionPeriodChange = { commissionPeriod = it },
                salesPeriod = salesPeriod,
                onSalesPeriodChange = { salesPeriod = it },
                salesStatus = salesStatus,
                onSalesStatusChange = { salesStatus = it }
            )
        }
    }
}

@Composable
private fun FeatureContent(
    feature: String,
    definition: FeatureDefinitionDto,
    module: FeatureModuleDto,
    showSensitiveFinance: Boolean,
    onAction: (String) -> Boolean,
    onConfirmOrder: (String, OrderConfirmRequestDto) -> Unit,
    onAuthorizationDecision: (String, Boolean) -> Unit,
    onExport: (String) -> Boolean,
    onRefresh: () -> Unit,
    commissionPeriod: String,
    onCommissionPeriodChange: (String) -> Unit,
    salesPeriod: String,
    onSalesPeriodChange: (String) -> Unit,
    salesStatus: String,
    onSalesStatusChange: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember(definition.title) { mutableStateOf("") }
    var priceHealthFilter by remember(feature) { mutableStateOf("Todos") }
    var decantFilter by remember(feature) { mutableStateOf("Todas") }
    val filteredRows = remember(module.rows, query, priceHealthFilter, decantFilter, salesStatus) {
        val normalized = query.trim().lowercase()
        val healthRows = if (feature != "price_health" || priceHealthFilter == "Todos") module.rows else module.rows.filter { row ->
            when (priceHealthFilter) {
                "Bajo costo", "Margen bajo" -> row.status.contains("bajo", ignoreCase = true) || row.status.contains("revisar", ignoreCase = true)
                "Sin precio" -> row.value.contains("Precio RD$ 0", ignoreCase = true) || row.value.contains("Sin precio", ignoreCase = true)
                "Sin costo" -> row.secondary.contains("Costo pendiente", ignoreCase = true)
                "Costo dudoso" -> row.status.contains("dudoso", ignoreCase = true)
                "Sugerencias" -> row.status.contains("revisar", ignoreCase = true) || row.status.contains("suger", ignoreCase = true)
                else -> true
            }
        }
        val decantRows = if (feature != "decants" || decantFilter == "Todas") healthRows else healthRows.filter { row ->
            row.status.equals(decantFilter.removeSuffix("s"), ignoreCase = true) ||
                row.status.equals(decantFilter, ignoreCase = true)
        }
        val salesRows = if (feature != "sales" || salesStatus == "Todos") decantRows else decantRows.filter { row ->
            when (salesStatus) {
                "Pagadas" -> row.status.contains("pagad", ignoreCase = true)
                // `pending` is the API status for a sale whose full balance is
                // still on credit. Keep accepting the old server label while
                // newer responses use the normalized "A crédito" label.
                "A crédito" -> row.status.contains("crédito", ignoreCase = true) ||
                    row.status.contains("credito", ignoreCase = true) ||
                    row.status.contains("pendiente", ignoreCase = true) ||
                    row.status.contains("pending", ignoreCase = true)
                "Parciales" -> row.status.contains("parcial", ignoreCase = true)
                "Anuladas" -> row.status.contains("anulad", ignoreCase = true)
                else -> true
            }
        }
        if (normalized.isBlank()) salesRows else salesRows.filter { row ->
            listOf(row.primary, row.secondary, row.value, row.status)
                .any { it.contains(normalized, ignoreCase = true) }
        }
    }
    var activeSectionKey by remember(module.sections) {
        mutableStateOf(module.sections.firstOrNull()?.key.orEmpty())
    }
    val activeSection = module.sections.firstOrNull { it.key == activeSectionKey }
    var orderToConfirm by remember(feature) { mutableStateOf<FeatureRowDto?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    // Ventas starts directly with its period and status filters;
                    // do not show the generic explanatory label above them.
                    if (feature != "sales" && definition.description.isNotBlank()) {
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
        if (feature == "commissions") {
            item {
                CommissionPeriodSelector(
                    selected = commissionPeriod,
                    onSelect = onCommissionPeriodChange
                )
            }
        }
        if (feature == "sales") {
            item {
                SalesPeriodSelector(selected = salesPeriod, onSelect = onSalesPeriodChange)
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Todos", "Pagadas", "A crédito", "Parciales", "Anuladas").forEach { status ->
                        FilterChip(selected = salesStatus == status, onClick = { onSalesStatusChange(status) }, label = { Text(status, maxLines = 1) })
                    }
                }
            }
        }
        if (module.kpis.isNotEmpty()) {
            item {
                val kpis = module.kpis.take(if (feature in setOf("price_health", "sales")) 4 else 3)
                Row(
                    Modifier.fillMaxWidth().then(if (feature in setOf("price_health", "sales")) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    kpis.forEachIndexed { index, kpi ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(180, delayMillis = index * 55)) + slideInHorizontally { it / 10 }
                        ) {
                            FeatureKpiCard(kpi, if (feature in setOf("price_health", "sales")) Modifier.width(150.dp) else Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        module.note?.takeIf { it.isNotBlank() }?.let { note ->
            item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight), modifier = Modifier.animateContentSize()) { Text(note, Modifier.padding(14.dp), color = BSPOSTheme.colors.textPrimary) } }
        }
        if (feature == "authorizations") {
            if (module.pendingRequests.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BSPOSTheme.colors.success)
                            Text("Nada pendiente", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                            Text("Cuando alguien pida permiso te llegará al teléfono y aparecerá aquí.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Esperando tu respuesta", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                        module.pendingRequests.forEach { request ->
                            AuthorizationRequestCard(request, onDecision = { approve -> onAuthorizationDecision(request.id, approve) })
                        }
                    }
                }
            }
        }
        if (feature == "price_health") {
            item {
                PriceHealthControls(
                    selected = priceHealthFilter,
                    onSelect = { priceHealthFilter = it },
                    onOpenRules = { onAction("Administrar reglas") },
                    showSensitiveFinance = showSensitiveFinance
                )
            }
        }
        if (feature == "decants") {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Todas", "Listos", "A pedido", "Sin botella", "Se agota").forEach { filter ->
                        FilterChip(
                            selected = decantFilter == filter,
                            onClick = { decantFilter = filter },
                            label = { Text(filter, maxLines = 1) }
                        )
                    }
                }
            }
        }
        if (module.bottleSources.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Botellas fuente", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                    Text(
                        "Consulta cuánto perfume queda y cuándo las ventas de decants recuperaron el costo de cada botella.",
                        color = BSPOSTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    module.bottleSources.forEach { bottle ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(bottle.name.ifBlank { "Botella fuente" }, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                                        Text(
                                            "${bottle.volumeMl} ml de origen · ${bottle.decantsCount} presentación(es)",
                                            color = BSPOSTheme.colors.textSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        if (bottle.covered) "Recuperada" else "En seguimiento",
                                        color = if (bottle.covered) BSPOSTheme.colors.success else BSPOSTheme.colors.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Disponibles: ${bottle.availableMl?.let { "$it ml" } ?: "Sin control de ml"} · " +
                                        "Ingresos decants: RD$ ${"%.2f".format(java.util.Locale.US, bottle.revenue)}",
                                    color = BSPOSTheme.colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (bottle.cost != null) {
                                    Text(
                                        "Costo botella: RD$ ${"%.2f".format(java.util.Locale.US, bottle.cost)}",
                                        color = BSPOSTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (bottle.message.isNotBlank()) {
                                    Text(bottle.message, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
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
                            section.kpis.take(3).forEach { kpi -> FeatureKpiCard(kpi, Modifier.weight(1f)) }
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
                        AnimatedVisibility(visible = true, enter = fadeIn() + slideInHorizontally { it / 12 }) {
                            FeatureRow(row, onConfirm = { orderToConfirm = row })
                        }
                    }
                }
            }
        } else {
            if (module.rows.size >= 4 || feature in setOf("decants", "services", "photos", "attributes")) {
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
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInHorizontally { it / 12 }) {
                        FeatureRow(row, onConfirm = { orderToConfirm = row })
                    }
                }
            }
        }
    }
    if (feature == "orders" || feature == "shipments") {
        orderToConfirm?.let { row ->
            OrderConfirmationDialog(
                order = row,
                onDismiss = { orderToConfirm = null },
                onConfirm = { request ->
                    row.id?.let { onConfirmOrder(it, request) }
                    orderToConfirm = null
                }
            )
        }
    }
}

@Composable
private fun AuthorizationRequestCard(request: AuthorizationRequestDto, onDecision: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(request.action.ifBlank { "Solicitud de autorización" }, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                    Text("${request.requester} · ${request.requesterEmail}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text("Pendiente", color = BSPOSTheme.colors.warning, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            if (request.context.isNotEmpty()) {
                Text(request.context.entries.joinToString(" · ") { (key, value) -> "$key: ${value}" }, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDecision(false) }, modifier = Modifier.weight(1f)) { Text("Rechazar") }
                Button(onClick = { onDecision(true) }, modifier = Modifier.weight(1f)) { Text("Aprobar") }
            }
        }
    }
}

@Composable
private fun CommissionPeriodSelector(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Período", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "current_fortnight" to "Esta quincena",
                "previous_fortnight" to "Quincena pasada",
                "month" to "Este mes"
            ).forEach { (value, label) ->
                if (selected == value) {
                    Button(onClick = { onSelect(value) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(value) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesPeriodSelector(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Período", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "today" to "Hoy",
                "month" to "Este mes",
                "last_7" to "Últimos 7 días",
                "all" to "Todo"
            ).forEach { (value, label) ->
                if (selected == value) {
                    Button(onClick = { onSelect(value) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { onSelect(value) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun PriceHealthControls(
    selected: String,
    onSelect: (String) -> Unit,
    onOpenRules: () -> Unit,
    showSensitiveFinance: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showSensitiveFinance) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "Bajo costo", "Margen bajo", "Sin precio", "Sin costo", "Costo dudoso", "Sugerencias").forEach { filter ->
                    FilterChip(selected = selected == filter, onClick = { onSelect(filter) }, label = { Text(filter, maxLines = 1) })
                }
            }
            OutlinedButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Abrir precios automáticos")
            }
        } else {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)
            ) {
                Text(
                    "La revisión de costos, márgenes y sugerencias de precio está reservada al propietario o al equipo financiero.",
                    Modifier.padding(14.dp),
                    color = BSPOSTheme.colors.textPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
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
internal fun FeatureKpiCard(kpi: FeatureKpiDto, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text(kpi.label, style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.height(5.dp))
            Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = toneColor(kpi.tone))
        }
    }
}

@Composable
private fun FeatureRow(row: FeatureRowDto, onConfirm: () -> Unit = {}) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.primary.ifBlank { "Registro" }, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                if (row.secondary.isNotBlank()) Text(row.secondary, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.value.isNotBlank()) Text(row.value, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                if (row.status.isNotBlank()) Text(row.status, color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelSmall)
                if (row.canConfirm) {
                    Button(
                        onClick = onConfirm,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Confirmar", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun OrderConfirmationDialog(
    order: FeatureRowDto,
    onDismiss: () -> Unit,
    onConfirm: (OrderConfirmRequestDto) -> Unit
) {
    var paymentKind by remember(order.id) { mutableStateOf("paid") }
    var paymentMethod by remember(order.id) { mutableStateOf("cash") }
    var reference by remember(order.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar ${order.primary}", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Registra el pedido como venta y descuenta el inventario al confirmar.", color = BSPOSTheme.colors.textSecondary)
                Text("Tipo de venta", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("paid" to "Pagada", "credit" to "Crédito", "mixed" to "Mixta").forEach { (value, label) ->
                        FilterChip(selected = paymentKind == value, onClick = { paymentKind = value }, label = { Text(label) })
                    }
                }
                Text("Método de pago", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cash" to "Efectivo", "card" to "Tarjeta", "bank_transfer" to "Transferencia").forEach { (value, label) ->
                        FilterChip(selected = paymentMethod == value, onClick = { paymentMethod = value }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Referencia (opcional)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(OrderConfirmRequestDto(paymentKind = paymentKind, paymentMethod = paymentMethod, reference = reference.trim().ifBlank { null }))
            }) { Text("Confirmar venta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
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
internal fun FeatureError(message: String, onRetry: () -> Unit) {
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
