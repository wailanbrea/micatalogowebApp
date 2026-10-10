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
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
import com.example.bspos.presentation.common.BSPOSButton as Button
import com.example.bspos.presentation.common.BSPOSOutlinedButton as OutlinedButton
import com.example.bspos.presentation.common.BSPOSSectionLabel
import com.example.bspos.presentation.common.BSPOSMoney
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import com.example.bspos.core.ui.theme.BSPOSFonts as FontFamily
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
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.common.BSPOSModalBottomSheet
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
    viewModel: QuoteViewModel = hiltViewModel(), documents: QuoteDocumentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val focus = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) { focus.clearFocus(force = true); viewModel.load() }
    QuoteTerminalContent(state, viewModel::add, viewModel::remove, viewModel::clearCart,
        viewModel::save, viewModel::convert, onOpenSales, onOpenTerminal, onNavigateBack,
        onOpenInvoice = { url -> uriHandler.openUri(url) }, initialSavedTab = true, onDownload = documents::download)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuoteTerminalContent(
    state: QuoteUiState,
    onAdd: (FeatureProductDto) -> Unit, onRemove: (FeatureProductDto) -> Unit, onClear: () -> Unit,
    onSave: (String, String, String, String, String?) -> Unit, onConvert: (String) -> Unit,
    onOpenSales: () -> Unit, onOpenTerminal: () -> Unit, onNavigateBack: () -> Unit,
    onOpenInvoice: (String) -> Unit = {}, initialSavedTab: Boolean = false, onDownload: (String, String) -> Unit = { _, _ -> }
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var savedTab by rememberSaveable { mutableStateOf(initialSavedTab) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var quoteToConvert by remember { mutableStateOf<FeatureRowDto?>(null) }
    var quoteDetail by remember { mutableStateOf<FeatureRowDto?>(null) }
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
            if (!savedTab) Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
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
            state.convertedInvoiceNumber?.let { invoice ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = BSPOSTheme.colors.successLight
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Venta generada · $invoice", fontWeight = FontWeight.Bold)
                        Text(
                            "La cotización ya es una venta y el inventario fue actualizado. Puedes abrir la factura para imprimirla o compartirla.",
                            fontSize = 12.sp,
                            color = BSPOSTheme.colors.textSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.convertedInvoiceUrl?.let { url ->
                                TextButton(onClick = { onOpenInvoice(url) }) { Text("Abrir factura") }
                            }
                            TextButton(onClick = onOpenSales) { Text("Ver ventas") }
                            TextButton(onClick = onOpenTerminal) { Text("Nueva venta") }
                        }
                    }
                }
            }
            if (savedTab && quoteDetail != null) {
                SavedQuoteDetail(quoteDetail!!, { quoteDetail = null }, { quoteToConvert = quoteDetail },
                    { quoteDetail?.let { row -> row.id?.let { onDownload(it, row.primary) } } }, onOpenInvoice)
            } else if (savedTab) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        BSPOSSectionLabel("Ventas")
                        Text("Cotizaciones", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                        Text("Presupuestos guardados desde la terminal, listos para cobrar.", color = BSPOSTheme.colors.textSecondary)
                        TextButton({ savedTab = false }) { Text("Nueva cotización") }
                    }
                    item { QuoteSummary(state.kpis, state.rows) }
                    item { com.example.bspos.presentation.common.BSPOSSearchField(query, { query = it }, "Buscar por número o cliente…", Modifier.fillMaxWidth()) }
                    if (state.rows.isEmpty() && !state.loading) item { Text("Todavía no hay cotizaciones guardadas.", color = BSPOSTheme.colors.textSecondary) }
                    items(state.rows.filter { "${it.primary} ${it.secondary}".contains(query, true) }, key = { it.id ?: it.primary }) { row ->
                        RecentQuoteRow(row, state.convertingId == row.id, state.convertingId == null,
                            onConvert = { quoteToConvert = row }, onOpen = { quoteDetail = row })
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
    quoteToConvert?.let { row ->
        AlertDialog(
            onDismissRequest = { if (state.convertingId == null) quoteToConvert = null },
            title = { Text("Convertir cotización en venta") },
            text = {
                Text(
                    "Esta acción valida las existencias, descuenta el inventario y crea la factura inmediatamente. " +
                        "Después de confirmar ya no se edita la cotización; podrás abrir la factura para imprimirla o compartirla."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = state.convertingId == null,
                    onClick = {
                        row.id?.let(onConvert)
                        quoteToConvert = null
                    }
                ) { Text("Convertir venta") }
            },
            dismissButton = {
                TextButton(enabled = state.convertingId == null, onClick = { quoteToConvert = null }) { Text("Revisar") }
            }
        )
    }
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
    BSPOSModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
private fun RecentQuoteRow(row: FeatureRowDto, converting: Boolean, enabled: Boolean, onConvert: () -> Unit, onOpen: () -> Unit) {
    Card(onClick = onOpen, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(row.customerName ?: row.secondary.substringBefore(" · ").ifBlank { "Cliente general" }, fontWeight = FontWeight.SemiBold)
                Text(row.primary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary)
            }
            QuoteStatus(row.status)
            if (row.value.isNotBlank()) BSPOSMoney(row.value)
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { BSPOSSectionLabel("Vigencia"); Text(quoteDate(row.validUntil), fontFamily = FontFamily.Monospace) }
            Column(Modifier.weight(1f)) { BSPOSSectionLabel("Artículos"); Text(if (row.itemCount > 0) "${row.itemCount} u." else row.secondary.substringAfter(" · ", "No consultado")) }
        }
        if (row.canConvert) Button(onClick = onConvert, enabled = enabled && !converting, modifier = Modifier.fillMaxWidth()) { Text(if (converting) "Convirtiendo…" else "Convertir venta") }
        }
    }
}

@Composable private fun QuoteStatus(status: String) {
    Surface(shape = RoundedCornerShape(50), color = if (status.contains("convert", true)) BSPOSTheme.colors.successLight else BSPOSTheme.colors.warningLight) {
        Text(status.uppercase(), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

private fun quoteDate(value: String?): String = value?.let {
    runCatching { LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(it)
} ?: "No indicada"

@Composable private fun SavedQuoteDetail(row: FeatureRowDto, onBack: () -> Unit, onConvert: () -> Unit, onDownload: () -> Unit, onOpenInvoice: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            BSPOSSectionLabel("Cotización")
            Text(row.primary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onDownload, enabled = row.pdfAvailable && row.id != null) { Text("Descargar PDF") }
                row.invoiceUrl?.let { url -> Button({ onOpenInvoice(url) }) { Text("Ver venta") } }
                if (row.canConvert) Button(onConvert) { Text("Convertir venta") }
            }
            TextButton(onBack) { Text("← Volver a cotizaciones") }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BSPOSSectionLabel("Detalles")
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text("Cliente", color = BSPOSTheme.colors.textSecondary); Text(row.customerName ?: row.secondary.substringBefore(" · ").ifBlank { "Cliente general" }) }
                        Column(Modifier.weight(1f)) { Text("Estado", color = BSPOSTheme.colors.textSecondary); QuoteStatus(row.status) }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text("Válida hasta", color = BSPOSTheme.colors.textSecondary); Text(quoteDate(row.validUntil), fontFamily = FontFamily.Monospace) }
                        Column(Modifier.weight(1f)) { Text("Artículos", color = BSPOSTheme.colors.textSecondary); Text(row.itemCount.takeIf { it > 0 }?.toString() ?: "No consultado") }
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                Column {
                    row.items.forEach { item ->
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row { Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); BSPOSMoney(item.lineTotal) }
                            Row { Column(Modifier.weight(1f)) { BSPOSSectionLabel("Cant."); Text(item.quantity.toString()) }; Column(Modifier.weight(1f)) { BSPOSSectionLabel("Precio unit."); BSPOSMoney(item.unitPrice) } }
                        }
                        HorizontalDivider(color = BSPOSTheme.colors.outline)
                    }
                    Row(Modifier.fillMaxWidth().padding(14.dp)) { Text("Total", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); BSPOSMoney(row.value) }
                }
            }
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
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BSPOSTheme.colors.outline),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                QuoteMetric("Vigentes", active, BSPOSTheme.colors.textPrimary, Modifier.fillMaxWidth().height(76.dp).padding(14.dp))
                HorizontalDivider(color = BSPOSTheme.colors.outline)
                QuoteMetric("Vencidas", expired, BSPOSTheme.colors.textPrimary, Modifier.fillMaxWidth().height(76.dp).padding(14.dp))
            }
            VerticalDivider(Modifier.height(152.dp), color = BSPOSTheme.colors.outline)
            QuoteMetric("Por convertir", pendingTotal, BSPOSTheme.colors.textPrimary, Modifier.weight(1f).height(152.dp).padding(14.dp))
        }
    }
}

@Composable
private fun QuoteMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
