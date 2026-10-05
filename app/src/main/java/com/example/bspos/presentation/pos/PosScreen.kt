package com.example.bspos.presentation.pos

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.Product
import coil.compose.AsyncImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    creditOnly: Boolean = false,
    onOpenCash: () -> Unit = {},
    viewModel: PosViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val customer by viewModel.customer.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cashSession by viewModel.cashSession.collectAsState()
    val checkoutResult by viewModel.checkoutResult.collectAsState()
    val printers by viewModel.printers.collectAsState()
    val printerMessage by viewModel.printerMessage.collectAsState()
    val wholesaleMode by viewModel.wholesaleMode.collectAsState()
    var query by remember { mutableStateOf("") }
    var choosingCustomer by remember { mutableStateOf(false) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var showingSplitDialog by remember { mutableStateOf(false) }
    val cartSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val quantities = stock.associate { it.productId to it.quantity }
    val selectedQuantities = cart.associate { it.product.id to it.quantity }
    val catalog = products.filter { it.isActive && it.deletedAt == null && (!wholesaleMode || (it.wholesalePrice ?: 0L) > 0L) && (it.name.contains(query, true) || it.internalCode.contains(query, true)) }
    val cartQuantity = cart.sumOf { it.quantity }
    val cashAction = {
        if (cashSession == null) onOpenCash() else viewModel.completeCash()
    }

    LaunchedEffect(checkoutResult) {
        checkoutResult?.let { result ->
            if (result.success) {
                showCart = false
                showingSplitDialog = false
            } else {
                // The modal sheet can cover the root Snackbar. Close it first so
                // failures such as "open cash session required" are visible.
                showCart = false
                showingSplitDialog = false
                snackbarHostState.showSnackbar(result.message)
                viewModel.consumeCheckoutResult()
            }
        }
    }

    LaunchedEffect(printerMessage) {
        printerMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumePrinterMessage()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        val tablet = maxWidth >= 700.dp
        if (tablet) {
            Column(Modifier.fillMaxSize().padding(28.dp)) {
                Text(if (creditOnly) "Venta a credito" else "Nueva venta", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (creditOnly) "Asigna un cliente y los productos para registrar el saldo" else "Selecciona los productos para agregarlos al carrito", color = BSPOSTheme.colors.textSecondary)
                FilterChip(selected = wholesaleMode, onClick = { viewModel.setWholesaleMode(!wholesaleMode) }, label = { Text(if (wholesaleMode) "Venta por mayor activa" else "Cambiar a precio por mayor") }, enabled = !isProcessing)
                Spacer(Modifier.height(14.dp))
                SearchField(query, { query = it })
                Spacer(Modifier.height(14.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                     ProductGrid(catalog, quantities, selectedQuantities, settings.allowNegativeStock, { viewModel.add(it) }, 4, wholesaleMode, Modifier.weight(1f))
                     CartPanel(cart, customer?.fullName, { choosingCustomer = true }, { viewModel.change(it.product.id, it.quantity - 1) }, { viewModel.change(it.product.id, it.quantity + 1) }, cashAction, viewModel::completeCard, viewModel::completeTransfer, viewModel::completeCredit, { showingSplitDialog = true }, isProcessing, creditOnly, cashSession != null, wholesaleMode, Modifier.width(330.dp))
                }
            }
        } else {
            Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { showCart = true },
                            icon = { Icon(Icons.Default.ShoppingCart, "Abrir carrito") },
                            text = { Text("Carrito ($cartQuantity)") }
                        )
                    }
                ) { contentPadding ->
                    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 18.dp)) {
                        Text(if (creditOnly) "Venta a credito" else "Nueva venta", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                        Text(if (creditOnly) "Asigna un cliente y los productos para registrar el saldo" else "Selecciona los productos para agregarlos al carrito", color = BSPOSTheme.colors.textSecondary)
                        FilterChip(selected = wholesaleMode, onClick = { viewModel.setWholesaleMode(!wholesaleMode) }, label = { Text(if (wholesaleMode) "Venta por mayor activa" else "Cambiar a precio por mayor") }, enabled = !isProcessing)
                        Spacer(Modifier.height(14.dp))
                        SearchField(query, { query = it })
                        Spacer(Modifier.height(14.dp))
                         ProductGrid(catalog, quantities, selectedQuantities, settings.allowNegativeStock, { viewModel.add(it) }, 2, wholesaleMode, Modifier.weight(1f).fillMaxWidth())
                    }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (showCart) {
        ModalBottomSheet(
            onDismissRequest = { showCart = false },
            sheetState = cartSheetState
        ) {
            CartPanel(
                cart = cart,
                 customer = customer?.fullName,
                onCustomer = { choosingCustomer = true },
                onMinus = { viewModel.change(it.product.id, it.quantity - 1) },
                onPlus = { viewModel.change(it.product.id, it.quantity + 1) },
                onCash = cashAction,
                onCard = viewModel::completeCard,
                onTransfer = viewModel::completeTransfer,
                onCredit = viewModel::completeCredit,
                onSplit = { showingSplitDialog = true },
                isProcessing = isProcessing,
                creditOnly = creditOnly,
                cashSessionOpen = cashSession != null,
                wholesaleMode = wholesaleMode,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).imePadding().navigationBarsPadding()
            )
        }
    }
    if (showingSplitDialog) {
        val totalCents = cart.sumOf { it.quantity * if (wholesaleMode) it.product.wholesalePrice ?: it.product.salePrice else it.product.salePrice }
        SplitPaymentDialog(
            totalCents = totalCents,
            customerName = customer?.fullName,
            cashSessionOpen = cashSession != null,
            onDismiss = { showingSplitDialog = false },
            onConfirm = { payments, dueDate ->
                viewModel.completeSplit(payments, dueDate)
            }
        )
    }
    if (choosingCustomer) CustomerDialog(customers.filter { it.isActive && it.deletedAt == null && it.creditLimit > it.balance }, { viewModel.selectCustomer(it); choosingCustomer = false }, { viewModel.selectCustomer(null); choosingCustomer = false })
    checkoutResult?.takeIf { it.success && it.sale != null }?.let { result ->
        SaleCompleteDialog(
            result = result,
            hasConfiguredPrinter = printers.isNotEmpty(),
            onShare = {
                val pdfUri = InvoicePdfGenerator.create(context, result.sale!!, result.lines, settings.currency, settings.invoice)
                shareInvoicePdf(context, pdfUri, result.sale.invoiceNumber)
            },
            onPrint = {
                if (printers.isNotEmpty()) {
                    viewModel.printToConfiguredPrinter(result)
                } else {
                    val pdfUri = InvoicePdfGenerator.create(context, result.sale!!, result.lines, settings.currency, settings.invoice)
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    printManager.print(
                        "Factura ${result.sale.invoiceNumber}",
                        InvoicePrintAdapter(context, "Factura ${result.sale.invoiceNumber}", pdfUri),
                        PrintAttributes.Builder().build()
                    )
                }
            },
            onDismiss = {
                showCart = false
                viewModel.consumeCheckoutResult()
            }
        )
    }
}

@Composable
private fun SaleCompleteDialog(
    result: CheckoutResult,
    hasConfiguredPrinter: Boolean,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Venta completada") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                AnimatedCheck()
                Spacer(Modifier.height(12.dp))
                Text(result.message, fontWeight = FontWeight.Bold)
                Text("Factura ${result.sale?.invoiceNumber.orEmpty()}", color = BSPOSTheme.colors.textSecondary)
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onPrint, modifier = Modifier.fillMaxWidth()) {
                    Text(if (hasConfiguredPrinter) "Enviar a impresora" else "Imprimir")
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Text("WhatsApp PDF")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun AnimatedCheck() {
    val progress by animateFloatAsState(1f, animationSpec = tween(750), label = "check-progress")
    val successColor = BSPOSTheme.colors.success
    Canvas(Modifier.size(78.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(successColor, radius)
        val stroke = 6.dp.toPx()
        val start = Offset(size.width * 0.25f, size.height * 0.52f)
        val middle = Offset(size.width * 0.44f, size.height * 0.70f)
        val end = Offset(size.width * 0.76f, size.height * 0.34f)
        val first = (progress / 0.45f).coerceIn(0f, 1f)
        val second = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
        drawLine(Color.White, start, lerp(start, middle, first), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color.White, middle, lerp(middle, end, second), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private fun lerp(from: Offset, to: Offset, fraction: Float): Offset = Offset(
    from.x + (to.x - from.x) * fraction,
    from.y + (to.y - from.y) * fraction
)

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = BSPOSTheme.colors.primary)
            OutlinedTextField(value, onChange, Modifier.weight(1f), placeholder = { Text("Buscar producto") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent))
        }
    }
}

@Composable
private fun ProductGrid(products: List<Product>, quantities: Map<java.util.UUID, Long>, selectedQuantities: Map<java.util.UUID, Long>, allowNegativeStock: Boolean, onAdd: (Product) -> Unit, columns: Int, wholesaleMode: Boolean = false, modifier: Modifier = Modifier) {
    if (products.isEmpty()) Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Sin productos para mostrar", color = BSPOSTheme.colors.textSecondary) } else LazyVerticalGrid(GridCells.Fixed(columns), modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(products, key = { it.id }) { product ->
            val quantity = quantities[product.id] ?: 0L
            val selected = selectedQuantities[product.id] ?: 0L
            val canAdd = quantity > 0 || allowNegativeStock
            Card(Modifier.clickable { if (canAdd) onAdd(product) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(15.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) {
                        val image = product.thumbnailPath ?: product.imagePath
                        if (image != null) AsyncImage(image, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(38.dp))
                         IconButton({ if (canAdd) onAdd(product) }, Modifier.align(Alignment.TopEnd), enabled = canAdd) { Icon(Icons.Default.Add, "Agregar", tint = BSPOSTheme.colors.primary) }
                        if (selected > 0) Surface(Modifier.align(Alignment.BottomStart).padding(6.dp), shape = RoundedCornerShape(50), color = BSPOSTheme.colors.primary) { Text("En carrito: $selected", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(product.name, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(money(if (wholesaleMode) product.wholesalePrice ?: product.salePrice else product.salePrice), color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                     Surface(shape = RoundedCornerShape(50), color = if (quantity > 0) BSPOSTheme.colors.successLight else if (allowNegativeStock) BSPOSTheme.colors.warningLight else BSPOSTheme.colors.errorLight) { Text(if (quantity > 0) "En stock ($quantity)" else if (allowNegativeStock) "Stock negativo permitido" else "Agotado", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = if (quantity > 0) BSPOSTheme.colors.success else if (allowNegativeStock) BSPOSTheme.colors.warning else BSPOSTheme.colors.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun CartPanel(cart: List<PosCartLine>, customer: String?, onCustomer: () -> Unit, onMinus: (PosCartLine) -> Unit, onPlus: (PosCartLine) -> Unit, onCash: () -> Unit, onCard: () -> Unit, onTransfer: () -> Unit, onCredit: () -> Unit, onSplit: () -> Unit, isProcessing: Boolean, creditOnly: Boolean, cashSessionOpen: Boolean, wholesaleMode: Boolean = false, modifier: Modifier = Modifier) {
    val total = cart.sumOf { it.quantity * if (wholesaleMode) it.product.wholesalePrice ?: it.product.salePrice else it.product.salePrice }
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ShoppingCart, null, tint = BSPOSTheme.colors.primary); Spacer(Modifier.width(8.dp)); Text("Carrito de venta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
            TextButton(onCustomer, Modifier.fillMaxWidth()) { Text(customer ?: "Asignar cliente para credito") }
            if (cart.isEmpty()) Box(Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Agrega productos para iniciar", color = BSPOSTheme.colors.textSecondary) } else {
                LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { lazyItems(cart, key = { it.product.id }) { line ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) {
                            val image = line.product.thumbnailPath ?: line.product.imagePath
                            if (image != null) {
                                AsyncImage(image, line.product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(line.product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("Cantidad seleccionada: ${line.quantity}", color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text("${money(if (wholesaleMode) line.product.wholesalePrice ?: line.product.salePrice else line.product.salePrice)} c/u", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall) }
                        Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ onMinus(line) }, enabled = !isProcessing) { Icon(Icons.Default.Remove, "Restar") }; Text(line.quantity.toString(), fontWeight = FontWeight.Bold); IconButton({ onPlus(line) }, enabled = !isProcessing) { Icon(Icons.Default.Add, "Sumar") } }
                    }
                } }
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline); Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text(money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false) }
            Spacer(Modifier.height(10.dp))
            if (creditOnly) {
                Button(onClick = onCredit, enabled = cart.isNotEmpty() && customer != null && !isProcessing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(if (isProcessing) "Procesando..." else "Vender a credito") }
            } else {
                if (!cashSessionOpen) {
                    Text(
                        "La caja está cerrada. Al cobrar efectivo te llevaremos a abrirla.",
                        color = BSPOSTheme.colors.warning,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Button(onClick = onCash, enabled = cart.isNotEmpty() && !isProcessing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(if (isProcessing) "Procesando..." else if (cashSessionOpen) "Cobrar efectivo" else "Abrir caja para cobrar") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = onCard, enabled = cart.isNotEmpty() && !isProcessing, modifier = Modifier.weight(1f)) { Text("Tarjeta") }; OutlinedButton(onClick = onTransfer, enabled = cart.isNotEmpty() && !isProcessing, modifier = Modifier.weight(1f)) { Text("Transferencia") } }
                OutlinedButton(onClick = onCredit, enabled = cart.isNotEmpty() && customer != null && !isProcessing, modifier = Modifier.fillMaxWidth()) { Text("Vender a credito") }
                OutlinedButton(onClick = onSplit, enabled = cart.isNotEmpty() && !isProcessing, modifier = Modifier.fillMaxWidth()) { Text("Pago dividido / Mixto") }
            }
        }
    }
}

@Composable
private fun SplitPaymentDialog(
    totalCents: Long,
    customerName: String?,
    cashSessionOpen: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<com.example.bspos.domain.usecase.PosPaymentSplitInput>, String?) -> Unit
) {
    var cashText by remember { mutableStateOf("") }
    var cardText by remember { mutableStateOf("") }
    var transferText by remember { mutableStateOf("") }
    var creditText by remember { mutableStateOf("") }
    var referenceText by remember { mutableStateOf("") }
    var dueDateText by remember { mutableStateOf("") }

    val cashCents = MoneyUtils.parsePesosStringToCents(cashText)
    val cardCents = MoneyUtils.parsePesosStringToCents(cardText)
    val transferCents = MoneyUtils.parsePesosStringToCents(transferText)
    val creditCents = MoneyUtils.parsePesosStringToCents(creditText)

    val sumCents = cashCents + cardCents + transferCents + creditCents
    val diffCents = totalCents - sumCents
    val isBalanced = diffCents == 0L && totalCents > 0L
    val canSubmit = isBalanced && (creditCents == 0L || customerName != null) && (cashCents == 0L || cashSessionOpen)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Pago dividido / Mixto", fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BSPOSTheme.colors.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total venta:", fontWeight = FontWeight.Bold)
                            Text(money(totalCents), fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Suma ingresada:", color = BSPOSTheme.colors.textSecondary)
                            Text(money(sumCents), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (diffCents == 0L) "Estado:" else if (diffCents > 0) "Falta por asignar:" else "Excedente:",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (diffCents == 0L) "Exacto (Cuadrado)" else money(Math.abs(diffCents)),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (diffCents == 0L) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (cashCents > 0L && !cashSessionOpen) {
                    Text(
                        "Advertencia: La caja está cerrada para cobrar efectivo.",
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (creditCents > 0L && customerName == null) {
                    Text(
                        "Atención: Para asignar crédito debes seleccionar un cliente.",
                        color = Color(0xFFE65100),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = cashText,
                    onValueChange = { cashText = it },
                    label = { Text("Efectivo (${LocalCurrency.current.symbol})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = cardText,
                    onValueChange = { cardText = it },
                    label = { Text("Tarjeta (${LocalCurrency.current.symbol})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = transferText,
                    onValueChange = { transferText = it },
                    label = { Text("Transferencia (${LocalCurrency.current.symbol})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = creditText,
                    onValueChange = { creditText = it },
                    label = { Text("Crédito (${LocalCurrency.current.symbol})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (creditCents > 0L) {
                    OutlinedTextField(
                        value = dueDateText,
                        onValueChange = { dueDateText = it },
                        label = { Text("Fecha de vencimiento (AAAA-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (transferText.isNotBlank()) {
                    OutlinedTextField(
                        value = referenceText,
                        onValueChange = { referenceText = it },
                        label = { Text("No. de referencia bancaria") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val list = mutableListOf<com.example.bspos.domain.usecase.PosPaymentSplitInput>()
                    if (cashCents > 0L) list.add(com.example.bspos.domain.usecase.PosPaymentSplitInput("cash", cashCents))
                    if (cardCents > 0L) list.add(com.example.bspos.domain.usecase.PosPaymentSplitInput("card", cardCents))
                    if (transferCents > 0L) list.add(com.example.bspos.domain.usecase.PosPaymentSplitInput("bank_transfer", transferCents, referenceText.ifBlank { null }))
                    if (creditCents > 0L) list.add(com.example.bspos.domain.usecase.PosPaymentSplitInput("credit", creditCents))
                    onConfirm(list, dueDateText.ifBlank { null })
                },
                enabled = canSubmit
            ) {
                Text("Confirmar Cobro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun CustomerDialog(customers: List<com.example.bspos.domain.model.Customer>, onSelect: (com.example.bspos.domain.model.Customer) -> Unit, onClear: () -> Unit) {
    AlertDialog(onDismissRequest = onClear, title = { Text("Seleccionar cliente") }, text = { if (customers.isEmpty()) Text("No hay clientes activos con crédito disponible. Registra o actualiza uno desde Clientes.") else LazyColumn { lazyItems(customers, key = { it.id }) { customer -> TextButton({ onSelect(customer) }, Modifier.fillMaxWidth()) { Text("${customer.fullName} - Disponible: ${money(customer.creditLimit - customer.balance)}") } } } }, confirmButton = { TextButton(onClear) { Text("Cancelar") } })
}

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)
