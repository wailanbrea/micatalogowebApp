package com.example.bspos.presentation.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto

@Composable
fun OrdersScreen(
    feature: String = "orders",
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedRow by remember { mutableStateOf<FeatureRowDto?>(null) }
    var detailRow by remember { mutableStateOf<FeatureRowDto?>(null) }
    val screenTitle = when (feature) {
        "encargos" -> "Encargos"
        "shipments" -> "Envíos"
        else -> "Pedidos"
    }
    val screenDescription = when (feature) {
        "encargos" -> "Organiza encargos pendientes y revisa lo que debes entregar a cada cliente."
        "shipments" -> "Consulta los pedidos con entrega configurada y conviértelos en ventas cuando estén listos."
        else -> "Revisa pedidos recibidos y conviértelos en ventas sin salir de la aplicación."
    }
    LaunchedEffect(feature) { viewModel.load(feature) }
    LaunchedEffect(state.message) {
        if (state.message != null) selectedRow = null
    }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BSPOSTheme.colors.primary)
            state.error != null && state.rows.isEmpty() -> OrdersError(state.error!!, onRetry = { viewModel.load() })
            else -> OrdersContent(
                state,
                screenTitle,
                screenDescription,
                onRefresh = { viewModel.load(feature, refresh = true) },
                onApplyFilters = { search, status -> viewModel.load(feature, refresh = true, search = search, status = status) },
                onConfirm = { selectedRow = it },
                onOpenDetail = { detailRow = it }
            )
        }
    }

    selectedRow?.let { row ->
        OrderConfirmDialog(
            row = row,
            customers = state.customers,
            busy = state.confirmingId == row.id,
            onDismiss = { if (state.confirmingId == null) selectedRow = null },
            onConfirm = { kind, method, customer, credit, reference ->
                viewModel.confirm(row, kind, method, customer, credit, reference)
            }
        )
    }
    detailRow?.let { row ->
        OrderDetailDialog(
            row = row,
            onDismiss = { detailRow = null },
            onConfirm = if (row.canConfirm) {
                { detailRow = null; selectedRow = row }
            } else null
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            icon = { Icon(Icons.Filled.CheckCircle, null, tint = BSPOSTheme.colors.success) },
            title = { Text("Pedido actualizado") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("Cerrar") } }
        )
    }
    state.error?.takeIf { state.rows.isNotEmpty() }?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            title = { Text("No se pudo completar") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun OrdersContent(
    state: OrdersUiState,
    title: String,
    description: String,
    onRefresh: () -> Unit,
    onApplyFilters: (String, String) -> Unit,
    onConfirm: (FeatureRowDto) -> Unit,
    onOpenDetail: (FeatureRowDto) -> Unit
) {
    var search by remember(title) { mutableStateOf(state.search) }
    var status by remember(title) { mutableStateOf(state.status) }
    val emptyTitle = when (title) {
        "Encargos" -> "No tienes encargos pendientes"
        "Envíos" -> "No hay envíos pendientes"
        else -> "Aún no tienes pedidos"
    }
    val emptyDescription = when (title) {
        "Encargos" -> "Los encargos que registres para tus clientes aparecerán aquí."
        "Envíos" -> "Los pedidos con entrega configurada aparecerán aquí para darles seguimiento."
        else -> "Comparte tu tienda para empezar a recibir órdenes."
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(description, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                    if (state.refreshing) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp, color = BSPOSTheme.colors.primary)
                    else Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", tint = BSPOSTheme.colors.primary)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Buscar pedido o cliente") },
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(onClick = { onApplyFilters(search, status) }) { Text("Buscar") }
                }
                if (title == "Encargos") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "all" to "Todos",
                            "today" to "Hoy",
                            "tomorrow" to "Mañana",
                            "overdue" to "Atrasados",
                            "no_date" to "Sin fecha"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = status == value,
                                onClick = { status = value; onApplyFilters(search, value) },
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }
        if (state.kpis.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.kpis.take(3).forEach { kpi -> OrderKpi(kpi, Modifier.weight(1f)) }
                }
            }
        }
        state.note?.takeIf { it.isNotBlank() }?.let { note ->
            item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight), modifier = Modifier.animateContentSize()) { Text(note, Modifier.padding(14.dp), color = BSPOSTheme.colors.textPrimary) } }
        }
        if (state.rows.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ReceiptLong, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.width(42.dp).height(42.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(emptyTitle, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                        Text(emptyDescription, color = BSPOSTheme.colors.textSecondary)
                    }
                }
            }
        } else {
            items(state.rows, key = { row -> "${row.primary}-${row.id}" }) { row ->
                AnimatedVisibility(true, enter = fadeIn() + slideInHorizontally { it / 12 }) {
                    OrderRow(
                        row,
                        busy = state.confirmingId == row.id,
                        onClick = { onOpenDetail(row) },
                        onConfirm = { onConfirm(row) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderKpi(kpi: FeatureKpiDto, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(kpi.label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(kpi.value, color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OrderRow(row: FeatureRowDto, busy: Boolean, onClick: () -> Unit, onConfirm: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.primary, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                    if (row.secondary.isNotBlank()) Text(row.secondary, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text(row.value, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.status.ifBlank { "Pendiente" }, color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (row.canConfirm) {
                    Button(onClick = onConfirm, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        else Text("Confirmar venta")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailDialog(
    row: FeatureRowDto,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ReceiptLong, null, tint = BSPOSTheme.colors.primary) },
        title = { Text("Detalle del pedido") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.primary, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                if (row.secondary.isNotBlank()) Text(row.secondary, color = BSPOSTheme.colors.textSecondary)
                if (row.value.isNotBlank()) Text("Total: ${row.value}", fontWeight = FontWeight.ExtraBold)
                Text("Estado: ${row.status.ifBlank { "Pendiente" }}", color = BSPOSTheme.colors.primary)
                Text(
                    if (row.canConfirm) {
                        "Revisa el pedido y confirma la venta cuando la mercancía esté lista para descontar inventario."
                    } else {
                        "Este pedido ya fue procesado. Puedes consultar su estado desde Ventas."
                    },
                    color = BSPOSTheme.colors.textSecondary
                )
            }
        },
        confirmButton = {
            if (onConfirm != null) {
                Button(onClick = onConfirm) { Text("Confirmar venta") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        dismissButton = if (onConfirm != null) {
            { TextButton(onClick = onDismiss) { Text("Cancelar") } }
        } else null
    )
}

@Composable
private fun OrderConfirmDialog(
    row: FeatureRowDto,
    customers: List<RemoteCustomerDto>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?, String?) -> Unit
) {
    var kind by remember { mutableStateOf("paid") }
    var method by remember { mutableStateOf("cash") }
    var customer by remember { mutableStateOf<RemoteCustomerDto?>(null) }
    var creditAmount by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    val needsCustomer = kind == "credit" || kind == "mixed"
    val creditValid = kind == "paid" || kind == "cash" || kind == "credit" || creditAmount.toBigDecimalOrNull()?.signum() == 1
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Confirmar ${row.primary}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Total: ${row.value}", fontWeight = FontWeight.Bold)
                Text("Método de venta", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("paid" to "Contado", "credit" to "Crédito", "mixed" to "Mixto").forEach { (value, label) ->
                        FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(label) })
                    }
                }
                Text("Forma de pago", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("cash" to "Efectivo", "card" to "Tarjeta", "transfer" to "Transferencia").forEach { (value, label) ->
                        FilterChip(selected = method == value, onClick = { method = value }, label = { Text(label) })
                    }
                }
                if (kind == "mixed") OutlinedTextField(creditAmount, { creditAmount = it }, label = { Text("Monto a crédito") }, singleLine = true)
                if (needsCustomer) {
                    Text("Cliente con crédito", style = MaterialTheme.typography.labelLarge)
                    if (customers.isEmpty()) Text("No hay clientes sincronizados. Registra primero el cliente desde Clientes.", color = BSPOSTheme.colors.error)
                    else customers.filter { it.isActive != false }.take(8).forEach { item ->
                        FilterChip(
                            selected = customer?.id == item.id,
                            onClick = { customer = item },
                            label = { Text(item.name ?: listOfNotNull(item.firstName, item.lastName).joinToString(" ").ifBlank { "Cliente" }) }
                        )
                    }
                }
                OutlinedTextField(reference, { reference = it }, label = { Text("Referencia (opcional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(kind, method, customer?.id, if (kind == "credit") null else creditAmount, reference) },
                enabled = !busy && (!needsCustomer || customer != null) && creditValid
            ) { if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp) else Text("Confirmar venta") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } }
    )
}

@Composable
private fun OrdersError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = BSPOSTheme.colors.error)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Reintentar") }
    }
}
