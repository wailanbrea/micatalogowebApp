package com.example.bspos.presentation.quote

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureProductDto
import com.example.bspos.data.micatalogo.dto.FeatureKpiDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import com.example.bspos.presentation.pos.PunttoSearchRow
import com.example.bspos.presentation.pos.PunttoTerminalChip
import com.example.bspos.presentation.pos.BarcodeScannerSheet
import java.time.LocalDate
import java.util.Locale
import java.math.BigDecimal

@Composable
fun QuoteScreen(
    onOpenSales: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: QuoteViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus(force = true); viewModel.load() }
    QuoteTerminalContent(state, viewModel::add, viewModel::remove, viewModel::clearCart,
        viewModel::save, viewModel::convert, onOpenSales, onOpenTerminal, onNavigateBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuoteTerminalContent(
    state: QuoteUiState,
    onAdd: (FeatureProductDto) -> Unit, onRemove: (FeatureProductDto) -> Unit, onClear: () -> Unit,
    onSave: (String, String, String, String, String?) -> Unit, onConvert: (String) -> Unit,
    onOpenSales: () -> Unit, onOpenTerminal: () -> Unit, onNavigateBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var savedTab by rememberSaveable { mutableStateOf(false) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var recentIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var customerName by rememberSaveable { mutableStateOf("") }
    var selectedCustomerId by rememberSaveable { mutableStateOf<String?>(null) }
    var customerPhone by rememberSaveable { mutableStateOf("") }
    var validUntil by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var notes by rememberSaveable { mutableStateOf("") }
    val categories = state.products.map { it.category }.filter(String::isNotBlank).distinct().sorted()
    val products = state.products.filter {
        (category == null || it.category == category) &&
            (query.isBlank() || it.name.contains(query, true) || it.code.contains(query, true) || it.brand.contains(query, true))
    }
    val total = state.cart.entries.fold(0L) { sum, (product, qty) -> Math.addExact(sum, Math.multiplyExact(parseMoney(product.price), qty.toLong())) }
    val count = state.cart.values.sumOf(Int::toLong)
    val add: (FeatureProductDto) -> Unit = {
        recentIds = (listOf(it.id) + recentIds).distinct().take(8)
        onAdd(it)
    }
    val recent = recentIds.mapNotNull { id -> state.products.firstOrNull { it.id == id } }
    LaunchedEffect(state.message, state.saving) {
        if (!state.saving && state.message != null && state.cart.isEmpty() && showCart) {
            showCart = false
            savedTab = true
            customerName = ""; customerPhone = ""; notes = ""
            selectedCustomerId = null
            validUntil = LocalDate.now().plusDays(7).toString()
        }
    }
    Scaffold(
        containerColor = BSPOSTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!savedTab) {
                Surface(color = BSPOSTheme.colors.background) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(8.dp).height(54.dp)
                            .testTag("quote-cart-open").clickable(enabled = count > 0 && !state.saving) { showCart = true },
                        shape = RoundedCornerShape(16.dp),
                        color = if (count > 0) BSPOSTheme.colors.primary else BSPOSTheme.colors.surface,
                        border = if (count == 0L) BorderStroke(1.dp, BSPOSTheme.colors.outline) else null
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (count > 0) Icons.Default.Description else Icons.Default.Add, null,
                                tint = if (count > 0) Color.White else BSPOSTheme.colors.textSecondary)
                            Spacer(Modifier.width(10.dp))
                            Text(if (count > 0) "Cotizar · $count artículos" else "Toca un producto para empezar",
                                Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (count > 0) Color.White else BSPOSTheme.colors.textSecondary)
                            if (count > 0) Text("RD$ ${money(total)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                Surface(Modifier.weight(1f).widthIn(max = 250.dp).height(42.dp), shape = RoundedCornerShape(12.dp), color = BSPOSTheme.colors.surfaceVariant) {
                    Row(Modifier.fillMaxSize()) {
                        listOf("Nueva" to false, "Guardadas" to true).forEach { (label, tab) ->
                            Surface(Modifier.weight(1f).padding(3.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable { savedTab = tab },
                                shape = RoundedCornerShape(10.dp), color = if (savedTab == tab) BSPOSTheme.colors.surface else Color.Transparent) {
                                Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, fontWeight = if (savedTab == tab) FontWeight.Bold else FontWeight.Normal) }
                            }
                        }
                    }
                }
                IconButton(onClick = { showGuide = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.HelpOutline, "Ayuda de cotizaciones") }
                IconButton(onClick = { showClear = true }, enabled = state.cart.isNotEmpty() && !state.saving, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.DeleteOutline, "Vaciar cotización") }
            }
            if (savedTab) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { QuoteSummary(state.kpis, state.rows) }
                    state.convertedInvoiceNumber?.let { invoice ->
                        item {
                            Surface(shape = RoundedCornerShape(12.dp), color = BSPOSTheme.colors.successLight) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Venta generada · $invoice", fontWeight = FontWeight.Bold)
                                    Row {
                                        TextButton(onClick = onOpenSales) { Text("Ver ventas") }
                                        TextButton(onClick = onOpenTerminal) { Text("Nueva venta") }
                                    }
                                }
                            }
                        }
                    }
                    if (state.rows.isEmpty() && !state.loading) item { Text("Todavía no hay cotizaciones guardadas.", color = BSPOSTheme.colors.textSecondary) }
                    items(state.rows, key = { it.id ?: it.primary }) { row ->
                        RecentQuoteRow(row, state.convertingId == row.id, state.convertingId == null) { row.id?.let(onConvert) }
                    }
                    state.message?.let { item { Text(it, color = BSPOSTheme.colors.success) } }
                    state.error?.let { item { Text(it, color = BSPOSTheme.colors.error) } }
                }
            } else {
                PunttoSearchRow(query, "Buscar producto, marca o código…", { query = it }, { showScanner = true }, {}, false)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PunttoTerminalChip(category == null, "Todos") { category = null }
                    categories.forEach { label -> PunttoTerminalChip(category == label, label) { category = label } }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BSPOSTheme.colors.outline)
                if (recent.isNotEmpty() && query.isBlank() && category == null) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("RECIENTES", fontSize = 10.sp, letterSpacing = 2.sp, color = BSPOSTheme.colors.textSecondary)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            recent.forEach { product ->
                                Surface(Modifier.width(160.dp).clickable(enabled = !state.saving) { add(product) }, shape = RoundedCornerShape(16.dp),
                                    color = BSPOSTheme.colors.surface, border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(product.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("RD$ ${money(parseMoney(product.price))}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = BSPOSTheme.colors.outline)
                }
                if (state.loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("quote-products"), contentPadding = PaddingValues(bottom = 8.dp)) {
                    if (products.isEmpty()) item { Text("Sin productos para esta búsqueda.", Modifier.padding(20.dp), color = BSPOSTheme.colors.textSecondary) }
                    items(products, key = { it.id }) { product ->
                        QuoteCompactProductRow(product, state.cart[product] ?: 0, !state.saving, { add(product) }, { onRemove(product) })
                        HorizontalDivider(color = BSPOSTheme.colors.outline)
                    }
                    state.error?.let { item { Text(it, Modifier.padding(14.dp), color = BSPOSTheme.colors.error) } }
                }
            }
        }
    }
    if (showScanner) BarcodeScannerSheet({ showScanner = false }) { code -> query = code; showScanner = false }
    if (showGuide) AlertDialog(onDismissRequest = { showGuide = false }, title = { Text("Cómo cotizar") },
        text = { Text("Busca o filtra los productos y tócalos para agregarlos. Abre Cotizar para revisar cantidades, cliente, vigencia y notas. Guardar no cobra ni reserva inventario. En Guardadas puedes convertir la cotización en venta; en ese momento se valida la existencia.") },
        confirmButton = { TextButton(onClick = { showGuide = false }) { Text("Entendido") } })
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text("¿Vaciar la cotización?") },
        text = { Text("Solo se quitarán los productos del borrador actual. Las cotizaciones guardadas no se eliminan.") },
        confirmButton = { TextButton(onClick = { onClear(); showClear = false }) { Text("Vaciar") } },
        dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancelar") } })
    if (showCart) QuoteCartSheet(state.cart, total, customerName, customerPhone, validUntil, notes,
        state.saving, state.error, { if (!state.saving) showCart = false },
        { customerName = it }, { customerPhone = it }, { validUntil = it }, { notes = it },
        onRemove, add, { onSave(customerName, customerPhone, validUntil, notes, selectedCustomerId) },
        state.customers, selectedCustomerId, { selectedCustomerId = it }, state.customersLoading, state.customerError)
}

@Composable
private fun QuoteThumbnail(product: FeatureProductDto) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(BSPOSTheme.colors.surfaceVariant), contentAlignment = Alignment.Center) {
        if (!product.imageUrl.isNullOrBlank()) AsyncImage(product.imageUrl, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(product.name.split(" ").take(2).mapNotNull { it.firstOrNull() }.joinToString(""), fontSize = 10.sp, color = BSPOSTheme.colors.textSecondary)
    }
}

@Composable
private fun QuoteCompactProductRow(product: FeatureProductDto, quantity: Int, enabled: Boolean, onAdd: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(BSPOSTheme.colors.surface).clickable(enabled = enabled && quantity < 100000, onClick = onAdd)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        QuoteThumbnail(product)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(product.name, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (product.stock == 0) Text("Sin existencia · se puede cotizar", fontSize = 10.sp, lineHeight = 13.sp, color = BSPOSTheme.colors.warning)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("RD$ ${money(parseMoney(product.price))}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            if (quantity > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Remove, "Disminuir ${product.name}", Modifier.size(16.dp)) }
                Text(quantity.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onAdd, enabled = enabled && quantity < 100000, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Add, "Aumentar ${product.name}", Modifier.size(16.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuoteCartSheet(
    cart: Map<FeatureProductDto, Int>, total: Long, customerName: String, customerPhone: String,
    validUntil: String, notes: String, saving: Boolean, error: String?,
    onDismiss: () -> Unit, onCustomerNameChange: (String) -> Unit, onCustomerPhoneChange: (String) -> Unit,
    onValidUntilChange: (String) -> Unit, onNotesChange: (String) -> Unit,
    onMinus: (FeatureProductDto) -> Unit, onPlus: (FeatureProductDto) -> Unit, onSave: () -> Unit,
    customers: List<RemoteCustomerDto>, selectedCustomerId: String?, onCustomerSelected: (String?) -> Unit,
    customersLoading: Boolean, customerError: String?
) {
    val context = LocalContext.current
    var choosingCustomer by remember { mutableStateOf(false) }
    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BSPOSTheme.colors.surface, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cotizar", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Cerrar cotización") }
                }
                Text("RD$ ${money(total)}", fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("quote-cart-body"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("${cart.values.sumOf(Int::toLong)} artículos · No se cobra ni descuenta inventario", fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary) }
                items(cart.entries.toList(), key = { it.key.id }) { (product, qty) ->
                    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline), color = BSPOSTheme.colors.surface) {
                        QuoteCompactProductRow(product, qty, !saving, { onPlus(product) }, { onMinus(product) })
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CLIENTE", fontSize = 10.sp, letterSpacing = 1.sp, color = BSPOSTheme.colors.textSecondary)
                        Surface(Modifier.fillMaxWidth().height(48.dp).testTag("quote-customer-selector").clickable(enabled = !saving) { choosingCustomer = true },
                            shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline), color = BSPOSTheme.colors.surface) {
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PersonOutline, null, Modifier.size(20.dp))
                                Text(selectedCustomer?.displayName() ?: if (selectedCustomerId == null) "Cliente genérico" else "Selecciona otro cliente",
                                    Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ExpandMore, "Seleccionar cliente", Modifier.size(20.dp))
                            }
                        }
                        if (selectedCustomerId == null) {
                            OutlinedTextField(customerName, { onCustomerNameChange(it.take(120)) }, Modifier.fillMaxWidth(), enabled = !saving, singleLine = true, label = { Text("Nombre (opcional)") })
                            OutlinedTextField(customerPhone, { onCustomerPhoneChange(it.filter { char -> char.isDigit() || char == '+' }.take(30)) }, Modifier.fillMaxWidth(),
                                enabled = !saving, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), label = { Text("Teléfono (opcional)") })
                        } else selectedCustomer?.phone?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("VÁLIDA HASTA", fontSize = 10.sp, letterSpacing = 1.sp); Text(validUntil, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            TextButton(onClick = {
                                val date = runCatching { LocalDate.parse(validUntil) }.getOrDefault(LocalDate.now().plusDays(7))
                                DatePickerDialog(context, { _, year, month, day -> onValidUntilChange(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth)
                                    .apply { datePicker.minDate = System.currentTimeMillis() }.show()
                            }, enabled = !saving) { Text("Cambiar") }
                        }
                        OutlinedTextField(notes, onNotesChange, Modifier.fillMaxWidth(), enabled = !saving, minLines = 2, maxLines = 4, label = { Text("Notas") })
                    }
                }
                error?.let { item { Text(it, color = BSPOSTheme.colors.error) } }
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Button(onClick = onSave, enabled = !saving && cart.isNotEmpty() && (selectedCustomerId == null || selectedCustomer != null),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).height(48.dp).testTag("quote-save"),
                shape = RoundedCornerShape(10.dp)) { Text(if (saving) "Guardando…" else "Guardar cotización · RD$ ${money(total)}", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
    if (choosingCustomer) QuoteCustomerDialog(customers, selectedCustomerId, customersLoading, customerError,
        { choosingCustomer = false }, { onCustomerSelected(it); choosingCustomer = false })
}

@Composable
private fun QuoteCustomerDialog(customers: List<RemoteCustomerDto>, selectedId: String?, loading: Boolean,
    error: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = customers.filter { query.isBlank() || it.displayName().contains(query, true) || it.phone?.contains(query) == true || it.documentNumber?.contains(query, true) == true }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Seleccionar cliente") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Buscar nombre o teléfono") })
            Surface(Modifier.fillMaxWidth().clickable { onSelect(null) }, shape = RoundedCornerShape(10.dp), color = if (selectedId == null) BSPOSTheme.colors.primaryLight else BSPOSTheme.colors.surfaceVariant) {
                Column(Modifier.padding(12.dp)) { Text("Cliente genérico", fontWeight = FontWeight.Bold); Text("Puedes escribir un nombre opcional", fontSize = 11.sp) }
            }
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            error?.let { Text(it, color = BSPOSTheme.colors.error, fontSize = 12.sp) }
            if (!loading && error == null && filtered.isEmpty()) Text(if (customers.isEmpty()) "No hay clientes registrados en esta tienda." else "Sin clientes para esta búsqueda.", fontSize = 12.sp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                items(filtered, key = { it.id }) { customer ->
                    Column(Modifier.fillMaxWidth().clickable { onSelect(customer.id) }.padding(vertical = 12.dp)) {
                        Text(customer.displayName(), fontWeight = FontWeight.Bold)
                        customer.phone?.let { Text(it, fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary) }
                    }
                    HorizontalDivider(color = BSPOSTheme.colors.outline)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
private fun RecentQuoteRow(row: FeatureRowDto, converting: Boolean, enabled: Boolean, onConvert: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.primary.ifBlank { "Cotización" }, fontWeight = FontWeight.Bold)
                if (row.secondary.isNotBlank()) Text(row.secondary, color = BSPOSTheme.colors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                if (row.value.isNotBlank()) Text(row.value, fontWeight = FontWeight.ExtraBold)
            }
            if (row.canConvert) TextButton(onClick = onConvert, enabled = enabled && !converting) { Text(if (converting) "…" else "Convertir") }
        }
    }
}

internal fun parseMoney(value: String): Long {
    val normalized = value
        .replace(Regex("[^0-9,.-]"), "")
        .replace(",", "")
    return runCatching { BigDecimal(normalized).movePointRight(2).longValueExact() }.getOrDefault(0L)
}
private fun money(cents: Long): String = MoneyUtils.formatCentsCompact(cents)

@Composable
private fun QuoteSummary(kpis: List<FeatureKpiDto>, rows: List<FeatureRowDto>) {
    val active = kpis.firstOrNull { it.label.equals("Vigentes", ignoreCase = true) }?.value
        ?: rows.count { it.canConvert }.toString()
    val pendingTotal = kpis.firstOrNull {
        it.label.contains("monto", ignoreCase = true) && it.label.contains("convertir", ignoreCase = true)
    }?.value ?: "RD$ ${money(rows.filter { it.canConvert }.sumOf { parseMoney(it.value) })}"
    val expired = kpis.firstOrNull { it.label.equals("Vencidas", ignoreCase = true) }?.value
        ?: rows.count { it.status.contains("venc", ignoreCase = true) || it.status.contains("expired", ignoreCase = true) }.toString()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val metrics = listOf(
                Triple("Vigentes", active, BSPOSTheme.colors.textPrimary),
                Triple("Por convertir", pendingTotal, BSPOSTheme.colors.primary),
                Triple("Vencidas", expired, BSPOSTheme.colors.warning)
            )
            if (maxWidth < 520.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    metrics.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { (label, value, color) -> QuoteMetric(label, value, color, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    metrics.forEach { (label, value, color) -> QuoteMetric(label, value, color, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun QuoteMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
