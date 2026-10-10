package com.example.bspos.presentation.decants

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.bspos.core.ui.theme.BSPOSFonts as FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import com.example.bspos.presentation.catalog.ProductCatalogViewModel
import com.example.bspos.presentation.catalog.ProductForm
import com.example.bspos.presentation.common.*
import kotlinx.serialization.json.*
import java.math.BigDecimal

private fun operation(type: String, block: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject { put("type", type); block() }
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, CurrencyUnit.DOP)
private fun decimal(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

@Composable
fun DecantsScreen(presentation: MiCatalogoBusinessPresentation, showCost: Boolean,
    viewModel: DecantsViewModel = hiltViewModel(), catalog: ProductCatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val products by catalog.products.collectAsState()
    val categories by catalog.categories.collectAsState()
    val units by catalog.units.collectAsState()
    val shops by catalog.shops.collectAsState()
    val activeShop by catalog.activeShopId.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<DecantFragranceDto?>(null) }
    var focusSize by remember { mutableStateOf<String?>(null) }
    var createSource by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.enqueued) { if (state.enqueued > 0) sheet = null }
    val workspace = state.workspace
    if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (workspace == null) Column(Modifier.padding(20.dp)) {
        Text(state.error ?: "No se pudo cargar Decants."); BSPOSButton(viewModel::load) { Text("Reintentar") }
    } else {
        DecantsContent(workspace, state.error, state.message, state.busy, viewModel::refreshSync,
            onAction = { sheet = it; focus = null; focusSize = null }, onPrepare = { focus = it; focusSize = null; sheet = "prepare" },
            onDiscard = { group, opening -> focus = group; sheet = "discard:${opening.id}" },
            onUndo = { viewModel.command(operation("decant_undo_open") { put("opening_id", it.id); put("expected_ml", it.remainingMl) }) },
            onMode = { size, value -> viewModel.command(operation("decant_presentation_update") { put("product_id", size.id); put("offered", value) }, listOf(size.id)) },
            onNewSize = { createSource = it.id },
            onSell = { group, opening -> focus = group; sheet = "sell:${opening.id}" },
            onReconcile = { group -> focus = group; sheet = "reconcile" },
            onPrice = { group, size -> focus = group; focusSize = size.id; sheet = "price" },
            onPrepareSize = { group, size -> focus = group; focusSize = size.id; sheet = "prepare" })
        val enabled = workspace.enabled && workspace.canManage && !state.busy
        when {
            sheet == "report" -> DecantReportSheet(workspace) { sheet = null }
            sheet == "vials" -> DecantVialsSheet(workspace, enabled, { sheet = null }, { size, qty, cost ->
                viewModel.command(operation("decant_vial_receive") { put("volume_ml", size); put("quantity", qty); put("unit_cost", cost) })
            }, { vial -> viewModel.command(operation("decant_vial_update") { put("vial_id", vial.id); put("active", !vial.active) }) })
            sheet == "open" -> DecantOpenSheet(workspace.groups, enabled, { sheet = null }) { group ->
                viewModel.command(operation("open_bottle") { put("product_id", group.id); put("quantity", 1); put("expected_stock", group.sealed); put("track_decants", true) }, listOf(group.id))
            }
            sheet == "prepare" -> DecantPrepareSheet(workspace, focus, enabled, { sheet = null },
                { createSource = it.id; sheet = null }, initialSizeId = focusSize) { group, opening, size, qty, price ->
                viewModel.command(operation("decant_prepare") {
                    put("product_id", size.id)
                    if (opening != null) { put("opening_id", opening.id); put("expected_ml", opening.remainingMl) }
                    else { put("source_product_id", group.id); put("expected_stock", group.sealed) }
                    put("quantity", qty); put("price", price)
                }, listOf(group.id, size.id))
            }
            sheet == "price" -> focus?.sizes?.firstOrNull { it.id == focusSize }?.let { size ->
                DecantPriceSheet(size, enabled, { sheet = null }) { price -> viewModel.command(operation("decant_presentation_update") {
                    put("product_id", size.id); put("price", price); put("expected_price", decimal(size.priceCents))
                }, listOf(size.id)) }
            }
            sheet?.startsWith("discard:") == true -> focus?.openings?.firstOrNull { sheet == "discard:${it.id}" }?.let { opening ->
                DecantDiscardSheet(opening, enabled, { sheet = null }) { qty, notes ->
                    viewModel.command(operation("decant_discard") { put("opening_id", opening.id); put("expected_ml", opening.remainingMl);
                        put("quantity", qty); put("notes", notes) }, listOfNotNull(focus?.id))
                }
            }
            sheet?.startsWith("sell:") == true -> focus?.openings?.firstOrNull { sheet == "sell:${it.id}" }?.let { opening ->
                DecantRemainderSheet(opening, enabled, { sheet = null }) { price, method ->
                    viewModel.command(operation("decant_sell_remainder") { put("opening_id", opening.id); put("expected_ml", opening.remainingMl);
                        put("price", price); put("payment_method", method) }, listOfNotNull(focus?.id))
                }
            }
            sheet == "reconcile" -> focus?.let { group ->
                DecantReconcileSheet(group, enabled, { sheet = null }) { ml ->
                    viewModel.command(operation("decant_reconcile_opening") { put("product_id", group.id); put("expected_ml", group.untrackedOpenMl); put("quantity", ml) }, listOf(group.id))
                }
            }
        }
    }
    if (createSource != null && categories.any { it.isActive } && units.any { it.isActive }) {
        ProductForm(current = null, currentQuantity = 0, categories = categories.filter { it.isActive }, units = units.filter { it.isActive },
            presentation = presentation, showCost = showCost, showInventoryFields = false, showProductCode = true,
            onSave = { input, _ -> catalog.add(input); createSource = null }, onDismiss = { createSource = null },
            shops = shops, activeShopId = activeShop, decantMode = true,
            sourceProducts = products.filter { it.remoteSaleUnit in setOf("bottle", "ml") }, initialSourceRemoteId = createSource)
    } else if (createSource != null) BSPOSAlertDialog(onDismissRequest = { createSource = null }, title = { Text("Nueva presentación") },
        text = { Text("Configura primero una categoría y una unidad activas en Catálogo.") },
        confirmButton = { BSPOSButton({ createSource = null }) { Text("Entendido") } })
}

@Composable
internal fun DecantsContent(workspace: DecantWorkspaceDto, error: String?, message: String?, busy: Boolean,
    onRefresh: () -> Unit, onAction: (String) -> Unit, onPrepare: (DecantFragranceDto) -> Unit,
    onDiscard: (DecantFragranceDto, DecantOpeningDto) -> Unit, onUndo: (DecantOpeningDto) -> Unit,
    onMode: (DecantSizeDto, Boolean) -> Unit, onNewSize: (DecantFragranceDto) -> Unit,
    onSell: (DecantFragranceDto, DecantOpeningDto) -> Unit = { _, _ -> }, onReconcile: (DecantFragranceDto) -> Unit = {},
    onPrice: (DecantFragranceDto, DecantSizeDto) -> Unit = { _, _ -> }, onPrepareSize: (DecantFragranceDto, DecantSizeDto) -> Unit = { _, _ -> }) {
    var query by remember { mutableStateOf("") }; var filter by remember { mutableStateOf("Todas") }
    fun matches(g: DecantFragranceDto, option: String): Boolean = when (option) {
        "Listos" -> g.sizes.any { it.prepared > 0 }
        "A pedido" -> g.sizes.any { it.offered && it.state == "a_pedido" }
        "Sin botella" -> g.openings.none { it.remainingMl > 0 }
        "Se agota" -> g.sizes.any { it.low } || g.openings.any { it.remainingMl > 0 && it.remainingMl.toFloat() / it.initialMl.coerceAtLeast(1) < .2f }
        else -> true
    }
    val groups = workspace.groups.filter { it.sizes.isNotEmpty() || it.openings.isNotEmpty() }
    val visible = groups.filter { matches(it, filter) && "${it.name} ${it.brand.orEmpty()}".contains(query, true) }
    LazyColumn(Modifier.fillMaxSize().background(BSPOSTheme.colors.surface).padding(horizontal = BSPOSDesign.screenMargin),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Decants", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onRefresh) { Icon(Icons.Outlined.Refresh, "Actualizar") }
            }
            Text("${groups.size} fragancia(s) · ${workspace.opened} con botella abierta", color = BSPOSTheme.colors.textSecondary)
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BSPOSOutlinedButton({ onAction("report") }) { Icon(Icons.Outlined.Assessment, null); Spacer(Modifier.width(8.dp)); Text("Reporte") }
                BSPOSOutlinedButton({ onAction("vials") }) { Text("Frascos  ${workspace.emptyVials}") }
                BSPOSOutlinedButton({ onAction("open") }, enabled = workspace.enabled && workspace.canManage && !busy) { Text("Abrir botella") }
            }
            Spacer(Modifier.height(8.dp))
            BSPOSButton({ onAction("prepare") }, enabled = workspace.enabled && workspace.canManage && !busy) { Icon(Icons.Outlined.Science, null); Spacer(Modifier.width(8.dp)); Text("Preparar decant") }
        }
        item {
            Surface(shape = BSPOSDesign.cardRadius, border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                Column {
                    Row {
                        Metric("Listos para vender", workspace.prepared.toString(), "frascos · ${money(workspace.preparedValueCents)} en estante", Modifier.weight(1f))
                        Metric("A pedido", workspace.onDemand.toString(), "tamaños que se preparan al vender", Modifier.weight(1f))
                    }
                    HorizontalDivider(color = BSPOSTheme.colors.outline)
                    Row {
                        Metric("Botellas abiertas", workspace.opened.toString(), "${workspace.groups.flatMap { it.openings }.sumOf { it.remainingMl }} ml en total", Modifier.weight(1f))
                        Metric("Perfume perdido", "${workspace.lostMl} ml", "acumulado", Modifier.weight(1f))
                    }
                }
            }
        }
        if (error != null) item { Text(error, color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
        if (message != null) item { Text(message, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
        if (workspace.unreconciledMl > 0) item { Text("${workspace.unreconciledMl} ml históricos pendientes de conciliación. Los indicadores de preparados corresponden a lotes registrados.", fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary) }
        item {
            BSPOSSearchField(query, { query = it }, "Buscar fragancia o marca", Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("Todas", "Listos", "A pedido", "Sin botella", "Se agota").forEach { option ->
                        Surface(Modifier.clickable { filter = option }, shape = RoundedCornerShape(6.dp), color = if (filter == option) BSPOSTheme.colors.secondaryNavy else BSPOSTheme.colors.surface) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(option, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (filter == option) BSPOSTheme.colors.textOnNavy else BSPOSTheme.colors.textSecondary)
                                Text(groups.count { matches(it, option) }.toString(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = if (filter == option) BSPOSTheme.colors.textOnNavy else BSPOSTheme.colors.textTertiary)
                            }
                        }
                    }
                }
            }
        }
        if (visible.isEmpty()) item { Text(if (query.isBlank()) "No hay fragancias en este filtro." else "Sin coincidencias.", color = BSPOSTheme.colors.textSecondary) }
        items(visible, key = { it.id }) { group -> FragranceCard(group, workspace.enabled && workspace.canManage && !busy, onPrepare, onDiscard, onUndo, onMode, onNewSize, onSell, onReconcile, onPrice, onPrepareSize) }
    }
}

@Composable private fun Metric(label: String, value: String, detail: String, modifier: Modifier) {
    val color = when (label) { "Listos para vender" -> BSPOSTheme.colors.success; "A pedido" -> BSPOSTheme.colors.warning; "Perfume perdido" -> BSPOSTheme.colors.error; else -> BSPOSTheme.colors.primary }
    val icon = when (label) { "Listos para vender" -> Icons.Outlined.CheckCircle; "A pedido" -> Icons.Outlined.Schedule; "Perfume perdido" -> Icons.Outlined.WaterDrop; else -> Icons.Outlined.Science }
    Column(modifier.height(108.dp).background(color.copy(alpha = .035f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(Modifier.size(20.dp), shape = RoundedCornerShape(6.dp), color = color.copy(alpha = .12f)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(14.dp), tint = color) } }
            Text(label, fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
        }
        BSPOSMoney(value, prominent = true)
        Text(detail, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp, color = BSPOSTheme.colors.textTertiary)
    }
}

@Composable private fun FragranceCard(group: DecantFragranceDto, enabled: Boolean, onPrepare: (DecantFragranceDto) -> Unit,
    onDiscard: (DecantFragranceDto, DecantOpeningDto) -> Unit, onUndo: (DecantOpeningDto) -> Unit,
    onMode: (DecantSizeDto, Boolean) -> Unit, onNewSize: (DecantFragranceDto) -> Unit,
    onSell: (DecantFragranceDto, DecantOpeningDto) -> Unit, onReconcile: (DecantFragranceDto) -> Unit,
    onPrice: (DecantFragranceDto, DecantSizeDto) -> Unit, onPrepareSize: (DecantFragranceDto, DecantSizeDto) -> Unit) {
    var expanded by remember(group.id) { mutableStateOf(false) }
    Surface(shape = BSPOSDesign.cardRadius, border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(56.dp), shape = RoundedCornerShape(10.dp), color = BSPOSTheme.colors.surfaceVariant) {
                    if (group.imageUrl != null) AsyncImage(group.imageUrl, group.name) else Box(contentAlignment = Alignment.Center) { Text(group.name.take(1)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("${group.sizes.size} tamaños · ${group.sizes.sumOf { it.prepared }} listos", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary)
                }
                IconButton({ onPrepare(group) }, enabled = enabled) { Icon(Icons.Outlined.Science, "Preparar") }
                IconButton({ expanded = !expanded }) { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Contraer" else "Expandir") }
            }
            val remaining = group.openings.sumOf { it.remainingMl }
            SummaryLine(if (group.tracked) "Botella abierta" else "Inventario histórico", if (group.tracked) "$remaining / ${group.openings.sumOf { it.initialMl }} ml" else "${group.legacyMl ?: 0} ml")
            LinearProgressIndicator(progress = { remaining.toFloat() / group.openings.sumOf { it.initialMl }.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth(), color = BSPOSTheme.colors.primary, trackColor = BSPOSTheme.colors.surfaceVariant)
            if (!group.tracked) Text("Capacidad histórica sin conciliación: no representa frascos preparados.", fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary)
            if (group.untrackedOpenMl > 0) BSPOSOutlinedButton({ onReconcile(group) }, enabled = enabled) { Text("Conciliar ${group.untrackedOpenMl} ml abiertos") }
            group.sizes.forEach { size ->
                Surface(shape = RoundedCornerShape(50), color = BSPOSTheme.colors.successLight) {
                    Text("${size.volumeMl} ml  ${size.prepared} listos  ${money(size.priceCents)}", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            if (expanded) {
                HorizontalDivider(color = BSPOSTheme.colors.outline)
                Row(verticalAlignment = Alignment.CenterVertically) { BSPOSSectionLabel("Tamaños", Modifier.weight(1f)); BSPOSOutlinedButton({ onNewSize(group) }) { Text("Nueva presentación") } }
                group.sizes.forEach { size ->
                    Text("${size.volumeMl} ml · ${size.prepared} en estante", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            TextButton({ onPrice(group, size) }, enabled = enabled) { Text("${money(size.priceCents)} · ${size.priceSource}", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                            Text("${size.capacity} preparables · ${size.emptyVials} envases", fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary)
                        }
                        BSPOSCompactSwitch(size.offered, { onMode(size, it) }, enabled = enabled)
                        BSPOSOutlinedButton({ onPrepareSize(group, size) }, enabled = enabled) { Text("Preparar") }
                    }
                    if (!size.offered) Text("Oculto en tienda y Terminal", fontSize = 10.sp, color = BSPOSTheme.colors.textSecondary)
                    HorizontalDivider(color = BSPOSTheme.colors.outline)
                }
                Text("Vendidos este mes: ${group.sizes.sumOf { it.soldMonth }} frascos · ${money(group.sizes.sumOf { it.revenueMonthCents })}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                BSPOSSectionLabel("Botella abierta")
                group.openings.forEach { opening ->
                    Surface(shape = BSPOSDesign.cardRadius, border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Abierta el ${opening.openedAt.take(10)}", fontWeight = FontWeight.SemiBold); BSPOSMoney("${opening.remainingMl} ml de ${opening.initialMl}", prominent = true)
                            opening.costCents?.let { SummaryLine("Costo de la botella", money(it)) }
                            SummaryLine("Ingresos registrados", money(opening.revenueCents)); SummaryLine("Selladas en inventario", group.sealed.toString())
                            BSPOSButton({ onPrepare(group) }, Modifier.fillMaxWidth(), enabled && opening.remainingMl > 0) { Text("Preparar") }
                            BSPOSOutlinedButton({ onSell(group, opening) }, Modifier.fillMaxWidth(), enabled && opening.remainingMl > 0) { Text("Vender el resto") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BSPOSOutlinedButton({ onDiscard(group, opening) }, Modifier.weight(1f), enabled && opening.remainingMl > 0) { Text("Descartar", color = BSPOSTheme.colors.error) }
                                BSPOSOutlinedButton({ onUndo(opening) }, Modifier.weight(1f), enabled && opening.canUndo) { Text("Deshacer apertura") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun DecantPrepareSheet(workspace: DecantWorkspaceDto, focus: DecantFragranceDto?, enabled: Boolean, onDismiss: () -> Unit,
    onNewSize: (DecantFragranceDto) -> Unit, initialSizeId: String? = null,
    onPrepare: (DecantFragranceDto, DecantOpeningDto?, DecantSizeDto, Int, String) -> Unit) {
    var groupId by remember { mutableStateOf(focus?.id) }
    var openingId by remember { mutableStateOf(focus?.openings?.singleOrNull { it.remainingMl > 0 }?.id) }
    var sealedSelected by remember { mutableStateOf(false) }
    var sizeId by remember { mutableStateOf(initialSizeId ?: focus?.sizes?.firstOrNull()?.id) }
    var quantity by remember { mutableIntStateOf(1) }
    var price by remember { mutableStateOf(focus?.sizes?.firstOrNull { it.id == sizeId }?.let { decimal(it.priceCents) } ?: "") }
    val group = workspace.groups.firstOrNull { it.id == groupId }; val opening = group?.openings?.firstOrNull { it.id == openingId }; val size = group?.sizes?.firstOrNull { it.id == sizeId }
    val selected = opening != null || sealedSelected
    val remaining = opening?.remainingMl ?: if (sealedSelected) group?.volumeMl ?: 0 else 0
    val max = if (selected && size != null && size.volumeMl > 0) minOf(remaining / size.volumeMl, size.emptyVials) else 0
    val priceCents = MoneyUtils.parseDecimalToCents(price)
    val valid = enabled && selected && size != null && quantity in 1..max && priceCents != null && priceCents >= 0
    fun selectSource(source: DecantFragranceDto, bottle: DecantOpeningDto?) {
        groupId = source.id; openingId = bottle?.id; sealedSelected = bottle == null
        val first = source.sizes.firstOrNull { it.id == initialSizeId } ?: source.sizes.firstOrNull()
        sizeId = first?.id; price = first?.let { decimal(it.priceCents) } ?: ""; quantity = 1
    }
    CheckoutStyleBottomSheet("Preparar decants", onDismiss,
        subtitle = if (!selected) "Elige la botella de origen." else "${group?.name.orEmpty()} · ${if (sealedSelected) "botella nueva" else "botella abierta"}", footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BSPOSOutlinedButton({ if (selected) { openingId = null; sealedSelected = false; sizeId = null } else onDismiss() }) { Text(if (selected) "Atrás" else "Cancelar") }
            CheckoutStylePrimaryButton("${if (sealedSelected) "Abrir y preparar" else "Preparar"} $quantity frasco(s)", {
                if (group != null && size != null && valid) onPrepare(group, opening, size, quantity, decimal(priceCents!!))
            }, enabled = valid, modifier = Modifier.weight(1f))
        }
    }) {
        if (!selected) {
            BSPOSSectionLabel("Botellas abiertas · termina estas primero")
            workspace.groups.filter { focus == null || it.id == focus.id }.forEach { item ->
                item.openings.filter { it.remainingMl > 0 }.forEach { bottle ->
                    SelectionRow(item.name, "quedan ${bottle.remainingMl} de ${bottle.initialMl} ml", false,
                        imageUrl = item.imageUrl, progress = bottle.remainingMl.toFloat() / bottle.initialMl.coerceAtLeast(1)) { selectSource(item, bottle) }
                }
            }
            BSPOSSectionLabel("Abrir una botella nueva")
            workspace.groups.filter { it.sealed > 0 && it.untrackedOpenMl == 0 && (focus == null || it.id == focus.id) }.forEach { item ->
                SelectionRow(item.name, "${item.sealed} en inventario · ${item.volumeMl} ml", false, imageUrl = item.imageUrl) { selectSource(item, null) }
            }
            if (workspace.groups.none { it.sealed > 0 || it.openings.any { b -> b.remainingMl > 0 } }) Text("No hay botellas disponibles. Recibe inventario para continuar.")
        } else if (group != null) {
            BSPOSSectionLabel("Tamaño")
            group.sizes.forEach { item -> SelectionRow("${item.volumeMl} ml", "${item.emptyVials} vacíos", sizeId == item.id, trailing = money(item.priceCents)) { sizeId = item.id; quantity = 1; price = decimal(item.priceCents) } }
            BSPOSOutlinedButton({ onNewSize(group) }) { Text("Nueva presentación") }
            if (size != null) {
                BSPOSSectionLabel("Cuántos")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ quantity-- }, enabled = quantity > 1) { Icon(Icons.Outlined.Remove, "Restar frasco") }; Text(quantity.toString(), fontFamily = FontFamily.Monospace)
                    IconButton({ quantity++ }, enabled = quantity < max) { Icon(Icons.Outlined.Add, "Agregar frasco") }; TextButton({ quantity = max }, enabled = max > 0) { Text("Máximo ($max)") }
                }
                Text("Máximo limitado por ml y envases. Sobrante: ${remaining - quantity * size.volumeMl} ml.", fontSize = 11.sp)
                BSPOSSectionLabel("Precio por frasco")
                BSPOSAmountField(price, { price = it }, "Precio por frasco", readOnly = size.priceSource == "fijado")
                val sourceCost = opening?.remainingCostCents ?: if (sealedSelected) group.sourceCostCents else null
                val perfumeCost = sourceCost?.let { if (remaining > 0) it * quantity * size.volumeMl / remaining else 0L }
                val lots = workspace.vials.firstOrNull { it.volumeMl == size.volumeMl && it.active }?.lots.orEmpty()
                var envasesRemaining = quantity
                var envaseCost = 0L
                var costsKnown = lots.isNotEmpty()
                lots.forEach { lot ->
                    val used = minOf(envasesRemaining, lot.remaining)
                    if (used > 0) {
                        costsKnown = costsKnown && lot.costCents != null
                        envaseCost += used * (lot.costCents ?: 0)
                        envasesRemaining -= used
                    }
                }
                val cost = if (perfumeCost != null && costsKnown && envasesRemaining == 0) perfumeCost + envaseCost else null
                Surface(shape = BSPOSDesign.cardRadius, border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryLine("Costo estimado · FIFO al confirmar", cost?.let(::money) ?: "Sin costo consultado")
                        val revenue = (priceCents ?: 0) * quantity
                        SummaryLine("Venta proyectada", money(revenue)); cost?.let {
                            val profit = revenue - it
                            SummaryLine("Ganancia proyectada", money(profit))
                            if (revenue > 0) SummaryLine("Margen", "${java.math.BigDecimal(profit).multiply(java.math.BigDecimal(100)).divide(java.math.BigDecimal(revenue), 0, java.math.RoundingMode.HALF_UP)}%")
                        }
                        SummaryLine("En la botella", "$remaining → ${remaining - size.volumeMl * quantity} ml")
                        (opening?.costCents ?: if (sealedSelected) group.sourceCostCents else null)?.takeIf { it > 0 }?.let { total ->
                            Text("Si vendes este lote: recuperación proyectada ${(((opening?.revenueCents ?: 0) + revenue) * 100 / total)}%. Preparar no genera ingresos.", fontSize = 11.sp)
                        }
                    }
                }
                if (max == 0) Text("Recibe envases de ${size.volumeMl} ml o selecciona otra botella.", color = BSPOSTheme.colors.error)
            }
        }
    }
}

@Composable private fun DecantOpenSheet(groups: List<DecantFragranceDto>, enabled: Boolean, onDismiss: () -> Unit, onOpen: (DecantFragranceDto) -> Unit) {
    var selected by remember { mutableStateOf<DecantFragranceDto?>(null) }
    CheckoutStyleBottomSheet("Abrir botella", onDismiss, footer = { CheckoutStylePrimaryButton("Abrir 1 botella", { selected?.let(onOpen) }, enabled = enabled && selected != null) }) {
        Text("La botella dejará de estar sellada. Sus ml y costo quedarán en una apertura trazable.")
        groups.filter { it.sealed > 0 }.forEach { group -> SelectionRow(group.name, "${group.sealed} selladas · ${group.volumeMl} ml", selected?.id == group.id) { selected = group } }
        if (groups.none { it.sealed > 0 }) Text("No hay botellas selladas disponibles.")
    }
}

@Composable private fun DecantVialsSheet(workspace: DecantWorkspaceDto, enabled: Boolean, onDismiss: () -> Unit, onReceive: (Int, Int, String) -> Unit, onActive: (DecantVialDto) -> Unit) {
    var size by remember { mutableStateOf("") }; var qty by remember { mutableStateOf("") }; var cost by remember { mutableStateOf("") }
    CheckoutStyleBottomSheet("Frascos vacíos", onDismiss, footer = {
        CheckoutStylePrimaryButton("Recibir envases", { onReceive(size.toInt(), qty.toInt(), decimal(MoneyUtils.parseDecimalToCents(cost)!!)) },
            enabled = enabled && (size.toIntOrNull() ?: 0) in 1..1000 && (qty.toIntOrNull() ?: 0) in 1..100000 && MoneyUtils.parseDecimalToCents(cost)?.let { it >= 0 } == true)
    }) {
        Text("${workspace.emptyVials} vacíos · ${workspace.prepared} llenos"); workspace.vialInvestmentCents?.let { SummaryLine("Invertido en envases", money(it)) }
        workspace.vials.forEach { vial ->
            Surface(shape = BSPOSDesign.cardRadius, border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryLine("${vial.name} · ${vial.volumeMl} ml", "${vial.empty} vacíos"); vial.nextCostCents?.let { SummaryLine("Próximo costo FIFO", money(it)) }
                    vial.lots.forEach { lot -> Text("${lot.receivedAt.take(10)} · ${lot.remaining}/${lot.received} · ${lot.costCents?.let(::money) ?: "Costo reservado"}", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    TextButton({ onActive(vial) }, enabled = enabled) { Text(if (vial.active) "Archivar tamaño" else "Activar tamaño") }
                }
            }
        }
        BSPOSSectionLabel("Recibir / nuevo tamaño")
        OutlinedTextField(size, { size = it.filter(Char::isDigit) }, label = { Text("Tamaño en ml") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text("Cantidad de envases") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cost, { cost = it }, label = { Text("Costo por envase") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun DecantReportSheet(workspace: DecantWorkspaceDto, onDismiss: () -> Unit) {
    CheckoutStyleBottomSheet("Reporte de decants", onDismiss) {
        Text("Período ${workspace.period.ifBlank { "no consultado" }}"); SummaryLine("Frascos preparados", workspace.prepared.toString()); SummaryLine("Valor en estante", money(workspace.preparedValueCents)); SummaryLine("Perfume perdido", "${workspace.lostMl} ml")
        workspace.groups.forEach { group -> Text(group.name, fontWeight = FontWeight.Bold); SummaryLine("Vendidos en el período", group.sizes.sumOf { it.soldMonth }.toString()); SummaryLine("Ingresos netos", money(group.sizes.sumOf { it.revenueMonthCents })); if (group.sizes.all { it.profitMonthCents != null }) SummaryLine("Margen monetario", money(group.sizes.sumOf { it.profitMonthCents ?: 0 })) }
    }
}

@Composable private fun DecantDiscardSheet(opening: DecantOpeningDto, enabled: Boolean, onDismiss: () -> Unit, onDiscard: (Int, String) -> Unit) {
    var ml by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    CheckoutStyleBottomSheet("Registrar merma", onDismiss, footer = { CheckoutStylePrimaryButton("Descartar perfume", { onDiscard(ml.toInt(), notes.trim()) }, enabled = enabled && (ml.toIntOrNull() ?: 0) in 1..opening.remainingMl && notes.isNotBlank()) }) {
        Text("Quedan ${opening.remainingMl} ml. La merma conserva el movimiento y su costo.")
        OutlinedTextField(ml, { ml = it.filter(Char::isDigit) }, label = { Text("Ml perdidos") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(notes, { notes = it }, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun SelectionRow(title: String, detail: String, selected: Boolean, imageUrl: String? = null, trailing: String? = null,
    progress: Float? = null, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = BSPOSDesign.cardRadius,
        color = if (selected) BSPOSTheme.colors.surfaceVariant else BSPOSTheme.colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BSPOSTheme.colors.secondaryNavy else BSPOSTheme.colors.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(36.dp), shape = RoundedCornerShape(8.dp), color = BSPOSTheme.colors.surfaceVariant) {
                    if (imageUrl != null) AsyncImage(imageUrl, null) else Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Science, null, Modifier.size(20.dp), tint = BSPOSTheme.colors.textSecondary) }
                }
                Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = if (trailing != null) 16.sp else 14.sp); if (progress == null) Text(detail, fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary) }
                trailing?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = BSPOSTheme.colors.textSecondary) }
                if (selected) Icon(Icons.Outlined.CheckCircle, "Seleccionado")
            }
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = BSPOSTheme.colors.primary, trackColor = BSPOSTheme.colors.surfaceVariant)
                Text(detail, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
            }
        }
    }
}

@Composable private fun DecantRemainderSheet(opening: DecantOpeningDto, enabled: Boolean, onDismiss: () -> Unit, onSell: (String, String) -> Unit) {
    var price by remember { mutableStateOf("") }; var method by remember { mutableStateOf("cash") }
    val cents = MoneyUtils.parseDecimalToCents(price)
    CheckoutStyleBottomSheet("Vender el resto", onDismiss, footer = {
        CheckoutStylePrimaryButton("Confirmar venta", { onSell(decimal(cents!!), method) }, enabled = enabled && cents != null && cents >= 0)
    }) {
        Text("Se venderán ${opening.remainingMl} ml de esta botella. Confirmar registra la factura y su pago; no prepara envases.")
        OutlinedTextField(price, { price = it }, label = { Text("Precio total del resto") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("cash" to "Efectivo", "card" to "Tarjeta", "bank_transfer" to "Transferencia").forEach { (key, label) -> FilterChip(method == key, { method = key }, label = { Text(label) }) }
        }
        opening.remainingCostCents?.let { SummaryLine("Costo capturado", money(it)) }
    }
}

@Composable private fun DecantPriceSheet(size: DecantSizeDto, enabled: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var price by remember { mutableStateOf(decimal(size.priceCents)) }
    val cents = MoneyUtils.parseDecimalToCents(price)
    CheckoutStyleBottomSheet("Cambiar precio · ${size.volumeMl} ml", onDismiss, footer = {
        CheckoutStylePrimaryButton("Fijar precio", { onSave(decimal(cents!!)) }, enabled = enabled && cents != null && cents >= 0)
    }) {
        Text("El precio de catálogo se comparte con tienda y Terminal. Las ventas previas conservan su precio capturado.")
        BSPOSSectionLabel("Precio por frasco")
        BSPOSAmountField(price, { price = it }, "Precio por frasco", enabled = enabled)
    }
}

@Composable private fun DecantReconcileSheet(group: DecantFragranceDto, enabled: Boolean, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var ml by remember { mutableStateOf(minOf(group.untrackedOpenMl, group.volumeMl).toString()) }
    CheckoutStyleBottomSheet("Conciliar botella abierta", onDismiss, footer = {
        CheckoutStylePrimaryButton("Registrar apertura histórica", { onConfirm(ml.toInt()) }, enabled = enabled && (ml.toIntOrNull() ?: 0) in 1..minOf(group.untrackedOpenMl, group.volumeMl))
    }) {
        Text("${group.name}: ${group.untrackedOpenMl} ml abiertos sin trazabilidad. Indica el remanente de una botella. Se conserva el volumen y costo FIFO existentes; no se crean frascos ni ventas.")
        OutlinedTextField(ml, { ml = it.filter(Char::isDigit) }, label = { Text("Ml actuales de la botella") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("La fecha original y el costo inicial no se inventan; esta apertura queda identificada como conciliación.", fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
    }
}

@Composable private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary); BSPOSMoney(value) }
}
