package com.example.bspos.presentation.orders

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.util.Locale
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.common.CheckoutStyleBottomSheet
import com.example.bspos.presentation.common.CheckoutStylePrimaryButton
import com.example.bspos.presentation.common.CheckoutStyleSectionLabel
import com.example.bspos.presentation.support.SupportFloatingActionButton
import java.math.BigDecimal

@Composable
fun OrdersScreen(
    feature: String = "orders",
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel(),
    publicStoreUrl: String? = null,
    onShareStore: () -> Unit = {},
    onOpenChat: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedRow by remember { mutableStateOf<FeatureRowDto?>(null) }
    var detailRow by remember { mutableStateOf<FeatureRowDto?>(null) }
    var newCustomerOpen by remember { mutableStateOf(false) }
    val screenTitle = when (feature) {
        "encargos" -> "Encargos"
        "shipments" -> "Envíos"
        else -> "Pedidos"
    }
    LaunchedEffect(feature) { viewModel.load(feature) }
    LaunchedEffect(state.message) {
        if (state.message != null) selectedRow = null
    }
    fun openOrderWhatsApp(row: FeatureRowDto) {
        val digits = row.customerPhone.orEmpty().filter(Char::isDigit)
        val phone = if (digits.length == 10) "1$digits" else digits
        if (phone.isBlank()) return
        val message = "Hola, sobre tu pedido ${row.primary} de MiCatalogo. Estamos revisando disponibilidad y te confirmaremos en breve."
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")))
        }
    }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BSPOSTheme.colors.primary)
            state.error != null && state.rows.isEmpty() -> OrdersError(state.error!!, onRetry = { viewModel.load() })
            else -> OrdersContent(
                state,
                screenTitle,
                showPunttoEmptyState = feature == "orders" && state.search.isBlank() && state.status == "all",
                publicStoreUrl = publicStoreUrl,
                onShareStore = onShareStore,
                onRefresh = { viewModel.load(feature, refresh = true) },
                onApplyFilters = { search, status -> viewModel.load(feature, refresh = true, search = search, status = status) },
                onConfirm = { selectedRow = it },
                onOpenDetail = { detailRow = it },
                onWhatsApp = ::openOrderWhatsApp,
                onOpenChat = onOpenChat
            )
        }
    }

    selectedRow?.let { row ->
        OrderConfirmDialog(
            row = row,
            customers = state.customers,
            customersLoading = state.customersLoading,
            creatingCustomer = state.creatingCustomer,
            customerCreateError = state.customerCreateError,
            createdCustomer = state.createdCustomer,
            busy = state.confirmingId == row.id,
            onDismiss = { if (state.confirmingId == null) selectedRow = null },
            onCreateCustomer = { newCustomerOpen = true },
            onConsumeCreatedCustomer = viewModel::consumeCreatedCustomer,
            onCreatedCustomerSelected = { newCustomerOpen = false },
            onConfirm = { kind, method, customer, credit, reference ->
                viewModel.confirm(row, kind, method, customer, credit, reference)
            }
        )
    }
    if (newCustomerOpen) {
        NewCreditCustomerDialog(
            busy = state.creatingCustomer,
            error = state.customerCreateError,
            onDismiss = {
                if (!state.creatingCustomer) {
                    newCustomerOpen = false
                    viewModel.consumeCustomerCreateError()
                }
            },
            onCreate = viewModel::createCreditCustomer
        )
    }
    detailRow?.let { row ->
        OrderDetailDialog(
            row = row,
            onDismiss = { detailRow = null },
            onWhatsApp = if (!row.customerPhone.isNullOrBlank()) { { openOrderWhatsApp(row) } } else null,
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
    showPunttoEmptyState: Boolean,
    publicStoreUrl: String?,
    onShareStore: () -> Unit,
    onRefresh: () -> Unit,
    onApplyFilters: (String, String) -> Unit,
    onConfirm: (FeatureRowDto) -> Unit,
    onOpenDetail: (FeatureRowDto) -> Unit,
    onWhatsApp: (FeatureRowDto) -> Unit,
    onOpenChat: () -> Unit
) {
    var search by remember(title) { mutableStateOf(state.search) }
    var status by remember(title) { mutableStateOf(state.status) }
    val isOrders = title == "Pedidos"
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
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (isOrders) 14.dp else 20.dp, vertical = if (isOrders) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isOrders) 10.dp else 14.dp)
        ) {
            item {
                // The parent shell is the single source of truth for the screen
                // title (for example, "Operación / Pedidos"). Keeping another
                // title here made Pedidos, Encargos and Envíos look different from
                // Inventario and produced duplicated labels on wide layouts.
                if (!isOrders) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                            if (state.refreshing) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp, color = BSPOSTheme.colors.primary)
                            else Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", tint = BSPOSTheme.colors.primary)
                        }
                    }
                }
            }

            if (state.kpis.isNotEmpty() && isOrders) {
                item {
                    OrderKpiGrid(state.kpis, state.rows)
                }
            }

            if (isOrders) {
                item {
                    OrdersSearchAndFilters(
                        search = search,
                        status = status,
                        onSearchChange = { search = it },
                        onSearch = { onApplyFilters(search, status) },
                        onStatusChange = { status = it; onApplyFilters(search, it) }
                    )
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("all" to "Todos", "today" to "Hoy", "tomorrow" to "Mañana", "overdue" to "Atrasados", "no_date" to "Sin fecha").forEach { (value, label) ->
                                    FilterChip(selected = status == value, onClick = { status = value; onApplyFilters(search, value) }, label = { Text(label, maxLines = 1) })
                                }
                            }
                        }
                    }
                }
            }

            if (state.kpis.isNotEmpty() && !isOrders) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { state.kpis.take(3).forEach { kpi -> OrderKpi(kpi, Modifier.weight(1f)) } }
                }
            }
            if (!isOrders) {
                state.note?.takeIf { it.isNotBlank() }?.let { note ->
                    item { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight), modifier = Modifier.animateContentSize()) { Text(note, Modifier.padding(14.dp), color = BSPOSTheme.colors.textPrimary) } }
                }
            }

            if (state.rows.isEmpty()) {
                item {
                    if (isOrders && showPunttoEmptyState) {
                        OrdersEmptyState(publicStoreUrl = publicStoreUrl, onShareStore = onShareStore)
                    } else {
                        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.ReceiptLong, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.width(42.dp).height(42.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(emptyTitle, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                                Text(emptyDescription, color = BSPOSTheme.colors.textSecondary)
                            }
                        }
                    }
                }
            } else if (isOrders) {
                item {
                    OrdersListCard(
                        rows = state.rows,
                        confirmingId = state.confirmingId,
                        pageTotal = state.pageTotal,
                        onOpenDetail = onOpenDetail,
                        onConfirm = onConfirm,
                        onWhatsApp = onWhatsApp
                    )
                }
            } else {
                items(state.rows, key = { row -> "${row.primary}-${row.id}" }) { row ->
                    AnimatedVisibility(true, enter = fadeIn() + slideInHorizontally { it / 12 }) {
                        OrderRow(row, busy = state.confirmingId == row.id, onClick = { onOpenDetail(row) }, onConfirm = { onConfirm(row) })
                    }
                }
            }
        }

        if (isOrders) {
            SupportFloatingActionButton(
                onClick = onOpenChat,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun OrdersSearchAndFilters(
    search: String,
    status: String,
    onSearchChange: (String) -> Unit,
    onSearch: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(.40f),
            singleLine = true,
            placeholder = { Text("Buscar por cli...", style = MaterialTheme.typography.bodySmall, maxLines = 1) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = BSPOSTheme.colors.textSecondary) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            shape = RoundedCornerShape(12.dp)
        )
        Surface(
            modifier = Modifier.weight(.60f),
            shape = RoundedCornerShape(12.dp),
            color = BSPOSTheme.colors.surface,
            border = BorderStroke(1.dp, BSPOSTheme.colors.outline)
        ) {
            Row(Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("all" to "Todos", "pending" to "Pendientes", "confirmed" to "Confirmados").forEach { (value, label) ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (status == value) BSPOSTheme.colors.surfaceVariant else BSPOSTheme.colors.surface).clickable { onStatusChange(value) }.padding(horizontal = 4.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun OrdersEmptyState(
    publicStoreUrl: String?,
    onShareStore: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
            border = BorderStroke(1.dp, BSPOSTheme.colors.outline)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                    tint = BSPOSTheme.colors.primary,
                    modifier = Modifier.width(44.dp).height(44.dp)
                )
                Text(
                    "Aún no tienes pedidos",
                    fontWeight = FontWeight.ExtraBold,
                    color = BSPOSTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Comparte tu tienda para empezar a recibir órdenes.",
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "COMPARTIR MI TIENDA",
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Envía este enlace a tus clientes para que vean tu catálogo y hagan pedidos.",
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.background),
                    border = BorderStroke(1.dp, BSPOSTheme.colors.outline)
                ) {
                    Text(
                        publicStoreUrl ?: "Configura el enlace público de tu tienda desde el panel.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        color = BSPOSTheme.colors.textPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
                Button(
                    onClick = onShareStore,
                    enabled = !publicStoreUrl.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Compartir por WhatsApp")
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
private fun OrderKpiGrid(kpis: List<FeatureKpiDto>, rows: List<FeatureRowDto>) {
    val visible = kpis.take(3).map { kpi ->
        if (kpi.label.equals("Valor recibido", ignoreCase = true)) {
            kpi.copy(
                label = "Total por confirmar",
                value = rows
                    .filter { row -> !row.status.contains("confirm", ignoreCase = true) && !row.status.contains("cancel", ignoreCase = true) }
                    .sumOf { row -> row.value.toMoneyNumber() }
                    .let(::formatCurrency)
            )
        } else kpi
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        border = BorderStroke(1.dp, BSPOSTheme.colors.outline)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                OrderKpiCell(
                    kpi = visible.getOrNull(0),
                    modifier = Modifier.weight(1f),
                    containerColor = BSPOSTheme.colors.warningLight.copy(alpha = 0.28f)
                )
                Box(Modifier.width(1.dp).height(76.dp).background(BSPOSTheme.colors.outline))
                OrderKpiCell(
                    kpi = visible.getOrNull(1),
                    modifier = Modifier.weight(1f),
                    containerColor = BSPOSTheme.colors.successLight.copy(alpha = 0.25f)
                )
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Row(Modifier.fillMaxWidth()) {
                OrderKpiCell(
                    kpi = visible.getOrNull(2),
                    modifier = Modifier.weight(1f),
                    containerColor = BSPOSTheme.colors.surfaceVariant.copy(alpha = 0.62f)
                )
                Box(Modifier.weight(1f).height(76.dp).background(BSPOSTheme.colors.surface))
            }
        }
    }
}

@Composable
private fun OrderKpiCell(
    kpi: FeatureKpiDto?,
    modifier: Modifier,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = modifier.height(76.dp).background(containerColor).padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (kpi != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    kpi.label,
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Text(
                    kpi.value,
                    color = BSPOSTheme.colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OrdersListCard(
    rows: List<FeatureRowDto>,
    confirmingId: String?,
    pageTotal: String?,
    onOpenDetail: (FeatureRowDto) -> Unit,
    onConfirm: (FeatureRowDto) -> Unit,
    onWhatsApp: (FeatureRowDto) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        border = BorderStroke(1.dp, BSPOSTheme.colors.outline)
    ) {
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                OrderRowContent(
                    row = row,
                    busy = confirmingId == row.id,
                    onClick = { onOpenDetail(row) },
                    onConfirm = { onConfirm(row) },
                    onWhatsApp = { onWhatsApp(row) }
                )
                if (index < rows.lastIndex) HorizontalDivider(color = BSPOSTheme.colors.outline)
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Row(
                modifier = Modifier.fillMaxWidth().background(BSPOSTheme.colors.surfaceVariant).padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${rows.size} de ${rows.size} pedidos",
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Total página ${pageTotal ?: rows.sumOf { it.value.toMoneyNumber() }.let(::formatCurrency)}",
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OrderRowContent(
    row: FeatureRowDto,
    busy: Boolean,
    onClick: () -> Unit,
    onConfirm: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val status = row.status.ifBlank { "Pendiente" }
    val statusLower = status.lowercase()
    val statusBackground = when {
        statusLower.contains("confirm") || statusLower.contains("pag") -> BSPOSTheme.colors.successLight
        statusLower.contains("cancel") -> BSPOSTheme.colors.errorLight
        else -> BSPOSTheme.colors.warningLight
    }
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    row.customerName?.takeIf { it.isNotBlank() } ?: "Agente",
                    color = BSPOSTheme.colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    row.primary,
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            Surface(shape = RoundedCornerShape(50), color = statusBackground) {
                Text(
                    status.uppercase(),
                    color = BSPOSTheme.colors.textPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                row.value,
                color = BSPOSTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            OrderMeta("ARTÍCULOS", row.itemCount.toString(), Modifier.weight(1f))
            OrderMeta("CONTACTO", row.customerPhone ?: "No indicado", Modifier.weight(1f))
        }

        OutlinedButton(
            onClick = onWhatsApp,
            enabled = !row.customerPhone.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BSPOSTheme.colors.outline)
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Escribir")
        }

        if (row.canConfirm) {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Confirmar pedido")
                }
            }
        }
    }
}

private fun String.toMoneyNumber(): Double {
    val number = replace(Regex("[^0-9,.-]"), "")
    return when {
        number.contains(',') && number.contains('.') -> {
            if (number.lastIndexOf(',') < number.lastIndexOf('.')) number.replace(",", "").toDoubleOrNull()
            else number.replace(".", "").replace(',', '.').toDoubleOrNull()
        }
        number.count { it == ',' } == 1 && number.substringAfter(',').length == 2 -> number.replace(',', '.').toDoubleOrNull()
        else -> number.replace(",", "").toDoubleOrNull()
    } ?: 0.0
}

private fun formatCurrency(amount: Double): String = "RD$ ${String.format(Locale.US, "%,.2f", amount)}"

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
                        else Text("Confirmar pedido")
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
    onWhatsApp: (() -> Unit)?,
    onConfirm: (() -> Unit)?
) {
    CheckoutStyleBottomSheet(
        title = "Pedido",
        badge = row.status.ifBlank { "Pendiente" }.uppercase(),
        amount = row.value,
        onDismiss = onDismiss,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onWhatsApp != null) {
                    OutlinedButton(onClick = onWhatsApp, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp)) {
                        Text("WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (onConfirm != null) {
                    CheckoutStylePrimaryButton("Confirmar pedido", onConfirm, modifier = Modifier.weight(1f))
                } else if (onWhatsApp == null) {
                    CheckoutStylePrimaryButton("Cerrar", onDismiss)
                }
            }
        }
    ) {
        Text(row.primary, color = BSPOSTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Recibido desde la tienda", color = BSPOSTheme.colors.textSecondary, fontSize = 12.sp)
        CheckoutStyleSectionLabel("CLIENTE")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OrderMeta("Nombre", row.customerName ?: "Cliente general", Modifier.weight(1f))
            OrderMeta("WhatsApp", row.customerPhone ?: "No indicado", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OrderMeta("Estado", row.status.ifBlank { "Pendiente" }, Modifier.weight(1f))
            OrderMeta("Origen", row.origin ?: "Tienda", Modifier.weight(1f))
        }
        CheckoutStyleSectionLabel("PRODUCTOS DEL PEDIDO")
        if (row.items.isEmpty()) {
            Text("Sin detalle de productos", color = BSPOSTheme.colors.textSecondary, fontSize = 12.sp)
        } else {
            Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline), color = BSPOSTheme.colors.surface) {
                Column(Modifier.fillMaxWidth()) {
                    row.items.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = BSPOSTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                                Text("${item.quantity} × ${item.unitPrice}", color = BSPOSTheme.colors.textSecondary, fontSize = 10.sp)
                            }
                            Text(item.lineTotal, color = BSPOSTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (index < row.items.lastIndex) HorizontalDivider(color = BSPOSTheme.colors.outline)
                    }
                }
            }
        }
        Text(
            if (row.canConfirm) "Confirma el pedido solo cuando hayas revisado disponibilidad y el método de pago." else "Este pedido ya fue procesado. Puedes consultar la venta generada desde Ventas.",
            color = BSPOSTheme.colors.textSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun OrderMeta(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), color = BSPOSTheme.colors.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = BSPOSTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
    }
}

@Composable
internal fun OrderConfirmDialog(
    row: FeatureRowDto,
    customers: List<RemoteCustomerDto>,
    customersLoading: Boolean,
    creatingCustomer: Boolean = false,
    customerCreateError: String? = null,
    createdCustomer: RemoteCustomerDto? = null,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreateCustomer: () -> Unit = {},
    onConsumeCreatedCustomer: () -> Unit = {},
    onCreatedCustomerSelected: () -> Unit = {},
    onConfirm: (String, String, String?, String?, String?) -> Unit
) {
    var kind by remember { mutableStateOf("paid") }
    var method by remember { mutableStateOf("cash") }
    var customer by remember { mutableStateOf<RemoteCustomerDto?>(null) }
    var downPayment by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    val needsCustomer = kind == "credit" || kind == "mixed"
    val totalCents = remember(row.id, row.value) { MoneyUtils.parsePesosStringToCents(row.value) }
    val mixedCreditCents = if (kind == "mixed") calculateMixedCredit(totalCents, downPayment) else null
    val creditValid = kind != "mixed" || mixedCreditCents != null
    LaunchedEffect(createdCustomer?.id) {
        createdCustomer?.let {
            customer = it
            onConsumeCreatedCustomer()
            onCreatedCustomerSelected()
        }
    }
    CheckoutStyleBottomSheet(
        title = "Confirmar pedido",
        badge = when (kind) { "credit" -> "CRÉDITO"; "mixed" -> "MIXTO"; else -> "CONTADO" },
        amount = row.value,
        onDismiss = onDismiss,
        dismissEnabled = !busy,
        footer = {
            CheckoutStylePrimaryButton(
                text = "Confirmar venta",
                onClick = {
                    val creditAmount = mixedCreditCents?.let { BigDecimal.valueOf(it, 2).toPlainString() }
                    onConfirm(kind, method, customer?.id, creditAmount, reference)
                },
                enabled = !busy && (!needsCustomer || customer != null) && creditValid,
                busy = busy
            )
        }
    ) {
                Text(row.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                CheckoutStyleSectionLabel("MÉTODO DE VENTA")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("paid" to "Contado", "credit" to "Crédito", "mixed" to "Mixto").forEach { (value, label) ->
                        FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(label, maxLines = 1, softWrap = false) })
                    }
                }
                CheckoutStyleSectionLabel("FORMA DE PAGO")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("cash" to "Efectivo", "card" to "Tarjeta", "bank_transfer" to "Transferencia").forEach { (value, label) ->
                        FilterChip(selected = method == value, onClick = { method = value }, label = { Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) })
                    }
                }
                if (kind == "mixed") {
                    OutlinedTextField(
                        value = downPayment,
                        onValueChange = { downPayment = it },
                        label = { Text("Abono inicial (${LocalCurrency.current.symbol})") },
                        supportingText = {
                            when {
                                mixedCreditCents != null -> Text("Saldo pendiente a crédito: ${MoneyUtils.formatCents(mixedCreditCents, LocalCurrency.current)}")
                                downPayment.isNotBlank() -> Text("El abono debe ser mayor que cero y menor que el total.")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (needsCustomer) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { CheckoutStyleSectionLabel("CLIENTE CON CRÉDITO") }
                        TextButton(onClick = onCreateCustomer, enabled = !busy && !creatingCustomer) { Text("Nuevo cliente") }
                    }
                    if (customersLoading) Text("Cargando clientes…", color = BSPOSTheme.colors.textSecondary)
                    else if (customers.isEmpty()) Text("Aún no hay clientes. Crea uno aquí para continuar a crédito.", color = BSPOSTheme.colors.textSecondary)
                    else customers.filter { it.isActive != false }.forEach { item ->
                        val name = item.name ?: listOfNotNull(item.firstName, item.lastName).joinToString(" ").ifBlank { "Cliente" }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { customer = item },
                            shape = RoundedCornerShape(10.dp),
                            color = if (customer?.id == item.id) BSPOSTheme.colors.primaryLight else BSPOSTheme.colors.surface,
                            border = BorderStroke(
                                1.dp,
                                if (customer?.id == item.id) BSPOSTheme.colors.primary else BSPOSTheme.colors.outline
                            )
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(item.phone?.takeIf { it.isNotBlank() }, item.creditLimit?.let { "Límite ${LocalCurrency.current.symbol}$it" }).joinToString(" · ").ifBlank { "Sin teléfono ni límite registrado" },
                                    color = BSPOSTheme.colors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    customerCreateError?.let { Text(it, color = BSPOSTheme.colors.error, fontSize = 12.sp) }
                }
                OutlinedTextField(reference, { reference = it }, label = { Text("Referencia (opcional)") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun NewCreditCustomerDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (NewCreditCustomerInput) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }
    val parsedCredit = MoneyUtils.parseDecimalToCents(creditLimit)
    val valid = name.trim().isNotBlank() && parsedCredit != null && parsedCredit > 0L

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.92f).heightIn(max = 620.dp),
            shape = RoundedCornerShape(26.dp),
            color = BSPOSTheme.colors.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Nuevo cliente a crédito", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                Text("Créalo y selecciónalo sin salir de este pedido.", color = BSPOSTheme.colors.textSecondary)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre completo") },
                    singleLine = true,
                    enabled = !busy,
                    isError = name.isBlank() && name.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { value -> phone = value.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Teléfono / WhatsApp (opcional)") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = creditLimit,
                    onValueChange = { value ->
                        val normalized = value.replace(',', '.')
                        if (normalized.isEmpty() || normalized.matches(Regex("[0-9]+(\\.[0-9]{0,2})?"))) creditLimit = normalized
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Límite de crédito (${LocalCurrency.current.symbol})") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = creditLimit.isNotBlank() && parsedCredit == null,
                    shape = RoundedCornerShape(12.dp)
                )
                error?.let { Text(it, color = BSPOSTheme.colors.error, fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
                    Button(
                        onClick = { onCreate(NewCreditCustomerInput(name, phone, creditLimit)) },
                        enabled = valid && !busy
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        else Text("Crear y seleccionar")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = BSPOSTheme.colors.error)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Reintentar") }
    }
}
