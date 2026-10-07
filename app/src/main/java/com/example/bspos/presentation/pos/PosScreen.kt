package com.example.bspos.presentation.pos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import coil.compose.AsyncImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    creditOnly: Boolean = false,
    presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    onOpenCash: () -> Unit = {},
    onOpenQuotes: () -> Unit = {},
    onOpenDayClose: () -> Unit = {},
    viewModel: PosViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsState()
    val recentProductIds by viewModel.recentProductIds.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val cartTotal by viewModel.cartTotal.collectAsState()
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
    var catalogTab by rememberSaveable { mutableStateOf("all") }
    var selectedCategoryId by remember { mutableStateOf<java.util.UUID?>(null) }
    var choosingCustomer by remember { mutableStateOf(false) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var showingSplitDialog by remember { mutableStateOf(false) }
    var showingTerminalGuide by remember { mutableStateOf(false) }
    var showingTerminalOptions by remember { mutableStateOf(false) }
    var showingBarcodeScanner by rememberSaveable { mutableStateOf(false) }
    var reviewMethod by remember { mutableStateOf<CheckoutReviewMethod?>(null) }
    val cartSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val quantities = stock.associate { it.productId to it.quantity }
    val selectedQuantities = cart.associate { it.product.id to it.quantity }
    val catalog = products.filter {
        it.isActive && it.deletedAt == null &&
            (!wholesaleMode || (it.wholesalePrice ?: 0L) > 0L) &&
            (catalogTab == "all" || (catalogTab == "products" && it.remoteSaleUnit != "decant") || (catalogTab == "decants" && it.remoteSaleUnit == "decant")) &&
            (selectedCategoryId == null || it.categoryId == selectedCategoryId) &&
            (it.name.contains(query, true) ||
                it.internalCode.contains(query, true) ||
                it.barcode?.contains(query, true) == true)
    }
    val cartQuantity = cart.sumOf { it.quantity }
    val categoryOptions = categories.filter { category -> products.any { product -> product.categoryId == category.id } }
    val hasDecants = products.any { it.remoteSaleUnit == "decant" }
    val recentProducts = recentProductIds.mapNotNull { id -> products.firstOrNull { it.id == id } }
    LaunchedEffect(categoryOptions) {
        if (selectedCategoryId != null && categoryOptions.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }
    LaunchedEffect(hasDecants) {
        if (!hasDecants && catalogTab == "decants") catalogTab = "all"
    }
    val cashAction = {
        if (cashSession == null) onOpenCash() else reviewMethod = CheckoutReviewMethod.CASH
    }
    val cardAction = { reviewMethod = CheckoutReviewMethod.CARD }
    val transferAction = { reviewMethod = CheckoutReviewMethod.TRANSFER }
    val creditAction = { if (customer == null) choosingCustomer = true else reviewMethod = CheckoutReviewMethod.CREDIT }

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
                TerminalHeader(creditOnly, { showingTerminalGuide = true }, { showingTerminalOptions = true })
                if (presentation.posShowWholesale) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !wholesaleMode, onClick = { viewModel.setWholesaleMode(false) }, label = { Text("Detalle") }, enabled = !isProcessing)
                        FilterChip(selected = wholesaleMode, onClick = { viewModel.setWholesaleMode(true) }, label = { Text("Mayoreo") }, enabled = !isProcessing)
                    }
                }
                Spacer(Modifier.height(14.dp))
                SearchField(query, { query = it }, presentation.posSearchPlaceholder) { showingBarcodeScanner = true }
                if (catalogTab == "all" && query.isBlank() && selectedCategoryId == null && recentProducts.isNotEmpty()) {
                    RecentProductsRow(recentProducts, quantities, settings.allowNegativeStock, { viewModel.add(it) })
                }
                ProductKindFilterRow(catalogTab, hasDecants) { catalogTab = it }
                CategoryFilterRow(categoryOptions, selectedCategoryId) { selectedCategoryId = it }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                     ProductGrid(catalog, quantities, selectedQuantities, settings.allowNegativeStock, { viewModel.add(it) }, 4, wholesaleMode, Modifier.weight(1f))
                     CartPanel(cart, cartTotal, customer, { choosingCustomer = true }, { viewModel.change(it.product.id, it.quantity - 1) }, { viewModel.change(it.product.id, it.quantity + 1) }, { viewModel.change(it.product.id, 0) }, viewModel::clearCart, cashAction, cardAction, transferAction, creditAction, { showingSplitDialog = true }, isProcessing, creditOnly, presentation.posShowCredit || creditOnly, cashSession != null, Modifier.widthIn(min = 340.dp, max = 400.dp).fillMaxHeight())
                }
            }
        } else {
            Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    bottomBar = {
                        if (cart.isNotEmpty()) {
                            Surface(color = BSPOSTheme.colors.surface, shadowElevation = 6.dp) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ShoppingCart, null, tint = BSPOSTheme.colors.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) { Text("$cartQuantity productos", fontWeight = FontWeight.Bold); Text(money(cartTotal), color = BSPOSTheme.colors.textSecondary) }
                                    Button(onClick = { showCart = true }, shape = RoundedCornerShape(14.dp)) { Text("Ver carrito") }
                                }
                            }
                        }
                    }
                ) { contentPadding ->
                    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 18.dp)) {
                        TerminalHeader(creditOnly, { showingTerminalGuide = true }, { showingTerminalOptions = true })
                        if (presentation.posShowWholesale) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !wholesaleMode, onClick = { viewModel.setWholesaleMode(false) }, label = { Text("Detalle") }, enabled = !isProcessing)
                                FilterChip(selected = wholesaleMode, onClick = { viewModel.setWholesaleMode(true) }, label = { Text("Mayoreo") }, enabled = !isProcessing)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        SearchField(query, { query = it }, presentation.posSearchPlaceholder) { showingBarcodeScanner = true }
                        if (catalogTab == "all" && query.isBlank() && selectedCategoryId == null && recentProducts.isNotEmpty()) {
                            RecentProductsRow(recentProducts, quantities, settings.allowNegativeStock, { viewModel.add(it) })
                        }
                        ProductKindFilterRow(catalogTab, hasDecants) { catalogTab = it }
                        CategoryFilterRow(categoryOptions, selectedCategoryId) { selectedCategoryId = it }
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
                 customer = customer,
                 total = cartTotal,
                 onCustomer = { choosingCustomer = true },
                 onMinus = { viewModel.change(it.product.id, it.quantity - 1) },
                 onPlus = { viewModel.change(it.product.id, it.quantity + 1) },
                 onRemove = { viewModel.change(it.product.id, 0) },
                 onClear = viewModel::clearCart,
                onCash = cashAction,
                onCard = cardAction,
                onTransfer = transferAction,
                onCredit = creditAction,
                onSplit = { showingSplitDialog = true },
                isProcessing = isProcessing,
                creditOnly = creditOnly,
                creditEnabled = presentation.posShowCredit || creditOnly,
                cashSessionOpen = cashSession != null,
                 modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(horizontal = 12.dp).imePadding().navigationBarsPadding()
            )
        }
    }
    if (showingTerminalGuide) {
        TerminalGuideDialog { showingTerminalGuide = false }
    }
    if (showingTerminalOptions) {
        TerminalOptionsSheet(
            onDismiss = { showingTerminalOptions = false },
            onQuote = { showingTerminalOptions = false; onOpenQuotes() },
            onDayClose = { showingTerminalOptions = false; onOpenDayClose() },
            onCash = { showingTerminalOptions = false; onOpenCash() }
        )
    }
    if (showingSplitDialog) {
        SplitPaymentDialog(
            totalCents = cartTotal,
            customerName = customer?.fullName,
            cashSessionOpen = cashSession != null,
            onDismiss = { showingSplitDialog = false },
            onConfirm = { payments, dueDate ->
                viewModel.completeSplit(payments, dueDate)
            }
        )
    }
    reviewMethod?.let { method ->
        CheckoutReviewSheet(
            cart = cart,
            total = cartTotal,
            customer = customer,
            method = method,
            isProcessing = isProcessing,
            onDismiss = { if (!isProcessing) reviewMethod = null },
            onConfirm = {
                reviewMethod = null
                when (method) {
                    CheckoutReviewMethod.CASH -> viewModel.completeCash()
                    CheckoutReviewMethod.CARD -> viewModel.completeCard()
                    CheckoutReviewMethod.TRANSFER -> viewModel.completeTransfer()
                    CheckoutReviewMethod.CREDIT -> viewModel.completeCredit()
                }
            }
        )
    }
    if (showingBarcodeScanner) {
        BarcodeScannerSheet(
            onDismiss = { showingBarcodeScanner = false },
            onCode = { code ->
                showingBarcodeScanner = false
                val match = products.firstOrNull {
                    it.barcode.equals(code, ignoreCase = true) || it.internalCode.equals(code, ignoreCase = true)
                }
                if (match != null) viewModel.add(match) else query = code
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
private fun TerminalHeader(creditOnly: Boolean, onGuide: () -> Unit, onOptions: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(if (creditOnly) "Venta a crédito" else "Nueva venta", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text(if (creditOnly) "Asigna un cliente y los productos para registrar el saldo" else "Selecciona los productos para agregarlos al carrito", color = BSPOSTheme.colors.textSecondary)
                }
                TextButton(onClick = onGuide) { Text("Cómo hacer una venta") }
                OutlinedButton(onClick = onOptions, shape = RoundedCornerShape(14.dp)) { Text("Opciones") }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Text(if (creditOnly) "Venta a crédito" else "Nueva venta", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text(if (creditOnly) "Asigna un cliente y los productos para registrar el saldo" else "Selecciona los productos para agregarlos al carrito", color = BSPOSTheme.colors.textSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onGuide) { Text("Ayuda") }
                    OutlinedButton(onClick = onOptions, shape = RoundedCornerShape(14.dp)) { Text("Opciones") }
                }
            }
        }
    }
}

@Composable
private fun TerminalGuideDialog(onDismiss: () -> Unit) {
    val steps = remember {
        listOf(
            GuideStep("Busca lo que te piden", "Escribe el nombre o pasa el código de barras. Los precios salen de tu catálogo."),
            GuideStep("Agrega productos", "Toca una tarjeta o el botón + y ajusta cantidades desde el carrito."),
            GuideStep("¿Venta o cotización?", "Cobrar registra la venta ahora. Cotizar prepara un precio por escrito sin tocar el inventario."),
            GuideStep("Revisa la venta", "Confirma artículos, cantidades, cliente, descuentos y total antes de continuar."),
            GuideStep("Cobra", "Elige efectivo, transferencia, tarjeta, mixto o crédito. Se descuenta inventario y se genera el recibo.")
        )
    }
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = BSPOSTheme.colors.surface,
        tonalElevation = 8.dp,
        icon = {
            Surface(shape = CircleShape, color = BSPOSTheme.colors.primaryLight) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.padding(12.dp), tint = BSPOSTheme.colors.primary)
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cómo hacer una venta")
                Text(
                    "PASO ${currentStep + 1} DE ${steps.size}",
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(180)) + slideInHorizontally(animationSpec = tween(220)) { it / 10 }) togetherWith
                        (fadeOut(animationSpec = tween(120)) + slideOutHorizontally(animationSpec = tween(160)) { -it / 10 })
                },
                label = "terminal-guide-step"
            ) { index ->
                GuideStep(
                    number = "${index + 1}",
                    title = steps[index].title,
                    description = steps[index].description
                )
            }
        },
        dismissButton = {
            if (currentStep > 0) {
                TextButton(onClick = { currentStep -= 1 }) { Text("Atrás") }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (currentStep < steps.lastIndex) currentStep += 1 else onDismiss()
            }) {
                Text(if (currentStep < steps.lastIndex) "Siguiente" else "Entendido")
            }
        }
    )
}

private data class GuideStep(
    val title: String,
    val description: String
)

@Composable
private fun GuideStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = BSPOSTheme.colors.primaryLight) {
            Text(number, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = BSPOSTheme.colors.primary, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalOptionsSheet(onDismiss: () -> Unit, onQuote: () -> Unit, onDayClose: () -> Unit, onCash: () -> Unit) {
    var quoteMode by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BSPOSTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Opciones de la terminal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("Acciones relacionadas con esta jornada de ventas.", color = BSPOSTheme.colors.textSecondary)

            Text("MODO", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !quoteMode,
                    onClick = { quoteMode = false },
                    label = { Text("Cobrar", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = quoteMode,
                    onClick = { quoteMode = true },
                    label = { Text("Cotizar", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                if (quoteMode) "Prepara un precio por escrito sin descontar existencias."
                else "Registra la venta y el pago al cobrar.",
                color = BSPOSTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { if (quoteMode) onQuote() else onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (quoteMode) "Abrir cotizaciones" else "Volver a la terminal") }
            TextButton(onClick = onDayClose, modifier = Modifier.fillMaxWidth()) { Text("Cierre de día", modifier = Modifier.fillMaxWidth()) }
            TextButton(onClick = onCash, modifier = Modifier.fillMaxWidth()) { Text("Abrir o revisar caja", modifier = Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(12.dp))
        }
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
    val sale = result.sale ?: return
    val itemCount = result.lines.sumOf { it.quantity }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = BSPOSTheme.colors.surface,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(58.dp),
                            shape = CircleShape,
                            color = BSPOSTheme.colors.successLight
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.padding(11.dp),
                                tint = BSPOSTheme.colors.success
                            )
                        }
                        Column {
                            Text(
                                "Venta completada",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = BSPOSTheme.colors.textPrimary
                            )
                            Text(
                                "Registrada correctamente",
                                style = MaterialTheme.typography.bodySmall,
                                color = BSPOSTheme.colors.textSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar comprobante",
                            tint = BSPOSTheme.colors.textSecondary
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = BSPOSTheme.colors.primaryLight
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Total de la venta", style = MaterialTheme.typography.labelMedium, color = BSPOSTheme.colors.textSecondary)
                        Text(money(sale.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primaryDark)
                        Text(paymentLabel(sale.paymentType), style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = BSPOSTheme.colors.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = BSPOSTheme.colors.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Comprobante", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                            Text(sale.invoiceNumber, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                        }
                        Text("$itemCount artículo(s)", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                    }
                }

                Text(result.message, style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                    OutlinedButton(onClick = onPrint, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasConfiguredPrinter) "Imprimir" else "PDF")
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("Nueva venta", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun paymentLabel(type: com.example.bspos.domain.model.SalePaymentType): String = when (type) {
    com.example.bspos.domain.model.SalePaymentType.CASH -> "Pago en efectivo"
    com.example.bspos.domain.model.SalePaymentType.CARD -> "Pago con tarjeta"
    com.example.bspos.domain.model.SalePaymentType.TRANSFER -> "Pago por transferencia"
    com.example.bspos.domain.model.SalePaymentType.CREDIT -> "Venta a crédito"
    com.example.bspos.domain.model.SalePaymentType.MIXED -> "Pago mixto"
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
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "Buscar producto",
    onScan: () -> Unit = {}
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = BSPOSTheme.colors.primary)
            OutlinedTextField(value, onChange, Modifier.weight(1f), placeholder = { Text(placeholder) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent))
            IconButton(onClick = onScan) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear código de barras", tint = BSPOSTheme.colors.primary)
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<com.example.bspos.domain.model.Category>,
    selectedCategoryId: java.util.UUID?,
    onCategorySelected: (java.util.UUID?) -> Unit
) {
    if (categories.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onCategorySelected(null) },
                label = { Text("Todos") }
            )
        }
        lazyItems(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onCategorySelected(category.id) },
                label = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun ProductKindFilterRow(
    selected: String,
    hasDecants: Boolean,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == "all",
            onClick = { onSelected("all") },
            label = { Text("Todos") }
        )
        FilterChip(
            selected = selected == "products",
            onClick = { onSelected("products") },
            label = { Text("Productos") }
        )
        if (hasDecants) {
            FilterChip(
                selected = selected == "decants",
                onClick = { onSelected("decants") },
                label = { Text("Decants") }
            )
        }
    }
}

@Composable
private fun RecentProductsRow(
    products: List<Product>,
    quantities: Map<java.util.UUID, Long>,
    allowNegativeStock: Boolean,
    onAdd: (Product) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Recientes",
            color = BSPOSTheme.colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            lazyItems(products, key = { it.id }) { product ->
                val quantity = quantities[product.id] ?: 0L
                val canAdd = quantity > 0 || allowNegativeStock
                Surface(
                    modifier = Modifier
                        .widthIn(min = 150.dp, max = 230.dp)
                        .clickable(enabled = canAdd) { onAdd(product) },
                    shape = RoundedCornerShape(16.dp),
                    color = BSPOSTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(money(product.salePrice), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarcodeScannerSheet(onDismiss: () -> Unit, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BSPOSTheme.colors.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Escanear producto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("Apunta la cámara al código de barras. El producto se agregará al carrito automáticamente.", color = BSPOSTheme.colors.textSecondary)
            if (permissionGranted) {
                Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(20.dp))) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Surface(
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth(.78f).height(110.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(2.dp, BSPOSTheme.colors.primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {}
                }
                BarcodeCameraBinding(previewView, lifecycleOwner, onCode)
            } else {
                Text("Necesitamos permiso para usar la cámara.", color = BSPOSTheme.colors.textSecondary)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Permitir cámara")
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BarcodeCameraBinding(
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onCode: (String) -> Unit
) {
    val context = LocalContext.current
    val scanner = remember { BarcodeScanning.getClient() }
    val delivered = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase ->
                    useCase.setAnalyzer(executor) { imageProxy ->
                        analyzeBarcode(imageProxy, scanner) { value ->
                            if (delivered.compareAndSet(false, true)) onCode(value)
                        }
                    }
                }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, executor)
        onDispose {
            scanner.close()
            runCatching { providerFuture.get().unbindAll() }
        }
    }
}

private fun analyzeBarcode(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onCode: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    scanner.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.trim()?.takeIf { it.isNotBlank() }?.let(onCode)
        }
        .addOnCompleteListener { imageProxy.close() }
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
private fun CartPanel(
    cart: List<PosCartLine>, total: Long, customer: Customer?, onCustomer: () -> Unit,
    onMinus: (PosCartLine) -> Unit, onPlus: (PosCartLine) -> Unit, onRemove: (PosCartLine) -> Unit, onClear: () -> Unit,
    onCash: () -> Unit, onCard: () -> Unit, onTransfer: () -> Unit, onCredit: () -> Unit,
    onSplit: () -> Unit, isProcessing: Boolean, creditOnly: Boolean, creditEnabled: Boolean, cashSessionOpen: Boolean,
    modifier: Modifier = Modifier
) {
    var confirmClear by remember { mutableStateOf(false) }
    var creditSelected by remember(creditOnly) { mutableStateOf(creditOnly) }
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Carrito de venta", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { confirmClear = true }, enabled = cart.isNotEmpty() && !isProcessing) {
                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("Vaciar")
                }
            }
            SelectedCustomerCard(customer, onCustomer)
            if (cart.isEmpty()) {
                EmptyCartState(Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lazyItems(cart, key = { it.product.id }) { line ->
                        CartProductItem(line, onMinus, onPlus, onRemove, isProcessing)
                        HorizontalDivider(color = BSPOSTheme.colors.outline)
                    }
                }
            }
            CartSummary(total)
            if (creditEnabled && !creditOnly) PaymentTypeSelector(creditSelected) { creditSelected = it }
            CartActions(
                cartIsEmpty = cart.isEmpty(),
                isProcessing = isProcessing,
                creditSelected = creditSelected,
                hasCustomer = customer != null,
                cashSessionOpen = cashSessionOpen,
                onCash = onCash,
                onCredit = onCredit,
                onCard = onCard,
                onTransfer = onTransfer,
                onSplit = onSplit,
                creditOnly = creditOnly
            )
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = BSPOSTheme.colors.surface,
        tonalElevation = 8.dp,
        icon = {
            Surface(shape = CircleShape, color = BSPOSTheme.colors.warningLight) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.padding(12.dp), tint = BSPOSTheme.colors.warning)
            }
        },
        title = { Text("¿Vaciar el carrito?") },
        text = { Text("Se eliminarán todos los productos agregados.") },
        confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Vaciar") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
    )
}

private enum class CheckoutReviewMethod(val title: String, val subtitle: String) {
    CASH("Efectivo", "Se registra en la caja abierta"),
    CARD("Tarjeta", "Se concilia contra el banco"),
    TRANSFER("Transferencia", "Se concilia contra el banco"),
    CREDIT("A crédito", "Queda pendiente de cobro")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutReviewSheet(
    cart: List<PosCartLine>,
    total: Long,
    customer: Customer?,
    method: CheckoutReviewMethod,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var cashReceivedText by remember(total, method) {
        mutableStateOf(if (method == CheckoutReviewMethod.CASH) MoneyUtils.formatCents(total).removePrefix("RD$ ").trim() else "")
    }
    val cashReceivedCents = MoneyUtils.parsePesosStringToCents(cashReceivedText)
    val cashChangeCents = cashReceivedCents - total
    val cashAmountIsValid = method != CheckoutReviewMethod.CASH || cashReceivedCents >= total

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BSPOSTheme.colors.surface,
        tonalElevation = 8.dp,
        scrimColor = Color.Black.copy(alpha = 0.52f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = BSPOSTheme.colors.primary
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color.White, modifier = Modifier.padding(10.dp))
                    }
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Cobrar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            Surface(shape = RoundedCornerShape(50), color = BSPOSTheme.colors.primaryLight) {
                                Text("VENTA", modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text("${cart.sumOf { it.quantity }} artículo(s) · revisa antes de confirmar", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = onDismiss, enabled = !isProcessing) { Icon(Icons.Default.Close, "Cerrar cobro") }
            }

            Text(money(total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BSPOSTheme.colors.background,
                border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Detalle de la venta", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textSecondary)
                            Text("El inventario se actualiza al confirmar.", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                        }
                        Surface(shape = RoundedCornerShape(50), color = BSPOSTheme.colors.surface) {
                            Text("${cart.sumOf { it.quantity }} unidad(es)", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    cart.forEach { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(BSPOSTheme.colors.surface)
                                .padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) {
                                val image = line.product.thumbnailPath ?: line.product.imagePath
                                if (image != null) AsyncImage(image, line.product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(line.product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${line.quantity} × ${money(line.unitPrice)}", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                            }
                            Text(money(Math.multiplyExact(line.quantity, line.unitPrice)), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BSPOSTheme.colors.primaryLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.primary), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cliente", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                        Text(customer?.fullName ?: "Consumidor final", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (customer == null) "Venta de mostrador" else "Cliente seleccionado", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                    }
                    Icon(Icons.Default.CheckCircle, null, tint = BSPOSTheme.colors.success)
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BSPOSTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = BSPOSTheme.colors.successLight) {
                        Icon(if (method == CheckoutReviewMethod.CREDIT) Icons.Default.ReceiptLong else Icons.Default.CheckCircle, null, tint = BSPOSTheme.colors.success, modifier = Modifier.padding(9.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Método de pago", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                        Text(method.title, fontWeight = FontWeight.Bold)
                        Text(method.subtitle, style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                    }
                }
            }

            if (method == CheckoutReviewMethod.CASH) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = cashReceivedText,
                        onValueChange = { cashReceivedText = it },
                        label = { Text("Recibido (${LocalCurrency.current.symbol})") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = !cashAmountIsValid,
                        supportingText = {
                            if (!cashAmountIsValid) Text("El efectivo recibido no puede ser menor que el total.")
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Exacto" to total, "1,000" to 100_000L, "2,000" to 200_000L, "5,000" to 500_000L)
                            .forEach { (label, amount) ->
                                AssistChip(
                                    onClick = { cashReceivedText = MoneyUtils.formatCents(amount).removePrefix("RD$ ").trim() },
                                    label = { Text(label) },
                                    enabled = amount >= total && !isProcessing
                                )
                            }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BSPOSTheme.colors.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text("Recibido", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                                Text(money(cashReceivedCents), fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (cashChangeCents >= 0) BSPOSTheme.colors.successLight else BSPOSTheme.colors.warningLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(13.dp)) {
                                Text("Devolver", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                                Text(money(cashChangeCents.coerceAtLeast(0)), fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(20.dp), color = BSPOSTheme.colors.textPrimary, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Subtotal", color = BSPOSTheme.colors.surface.copy(alpha = 0.72f)); Text(money(total), color = BSPOSTheme.colors.surface, fontWeight = FontWeight.Bold) }
                    HorizontalDivider(color = BSPOSTheme.colors.surface.copy(alpha = 0.18f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) { Text("Total", color = BSPOSTheme.colors.surface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold); Text(money(total), color = BSPOSTheme.colors.surface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }
                }
            }

            Button(onClick = onConfirm, enabled = !isProcessing && cashAmountIsValid, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else {
                    Text(if (method == CheckoutReviewMethod.CREDIT) "Registrar venta a crédito" else "Confirmar venta · ${money(total)}", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SelectedCustomerCard(customer: Customer?, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), color = BSPOSTheme.colors.primaryLight) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.primary), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White) }
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
                Text("Cliente", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                Text(customer?.fullName ?: "Consumidor final", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(customer?.taxId?.takeIf { it.isNotBlank() } ?: "Seleccionar cliente", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
            }
            Icon(Icons.Default.ChevronRight, "Seleccionar cliente", tint = BSPOSTheme.colors.primary)
        }
    }
}

@Composable
private fun CartProductItem(line: PosCartLine, onMinus: (PosCartLine) -> Unit, onPlus: (PosCartLine) -> Unit, onRemove: (PosCartLine) -> Unit, processing: Boolean) {
    val price = line.unitPrice
    Row(Modifier.fillMaxWidth().animateContentSize().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) {
            val image = line.product.thumbnailPath ?: line.product.imagePath
            if (image != null) AsyncImage(image, line.product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary)
        }
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { Text(line.product.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(money(price), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                IconButton(onClick = { onRemove(line) }, enabled = !processing, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.DeleteOutline, "Eliminar ${line.product.name}", tint = BSPOSTheme.colors.error) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMinus(line) }, enabled = !processing, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Remove, "Disminuir cantidad de ${line.product.name}") }
                Text(line.quantity.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                IconButton(onClick = { onPlus(line) }, enabled = !processing, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Add, "Aumentar cantidad de ${line.product.name}") }
                Spacer(Modifier.weight(1f)); Text(money(Math.multiplyExact(line.quantity, price)), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyCartState(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.ShoppingCart, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(10.dp)); Text("Tu carrito está vacío", fontWeight = FontWeight.Bold); Text("Selecciona productos para comenzar la venta.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun CartSummary(total: Long) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Subtotal", color = BSPOSTheme.colors.textSecondary); Text(money(total), fontWeight = FontWeight.SemiBold) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Descuento", color = BSPOSTheme.colors.textSecondary); Text("No disponible", color = BSPOSTheme.colors.textTertiary, style = MaterialTheme.typography.bodySmall) }
    HorizontalDivider(color = BSPOSTheme.colors.outline)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PaymentTypeSelector(creditSelected: Boolean, onChange: (Boolean) -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text("Método de pago", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !creditSelected, onClick = { onChange(false) }, label = { Text("Contado") }, modifier = Modifier.weight(1f))
        FilterChip(selected = creditSelected, onClick = { onChange(true) }, label = { Text("Crédito") }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CartActions(cartIsEmpty: Boolean, isProcessing: Boolean, creditSelected: Boolean, hasCustomer: Boolean, cashSessionOpen: Boolean, onCash: () -> Unit, onCredit: () -> Unit, onCard: () -> Unit, onTransfer: () -> Unit, onSplit: () -> Unit, creditOnly: Boolean) {
    val enabled = !cartIsEmpty && !isProcessing && (!creditSelected || hasCustomer)
    Button(onClick = { if (creditSelected || creditOnly) onCredit() else onCash() }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp)) { if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else { Text(if (creditSelected || creditOnly) "Vender a crédito" else if (cashSessionOpen) "Cobrar" else "Abrir caja para cobrar", fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } }
    if (!creditOnly && !creditSelected) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onCard, enabled = !cartIsEmpty && !isProcessing, modifier = Modifier.weight(1f)) { Text("Tarjeta") }; OutlinedButton(onTransfer, enabled = !cartIsEmpty && !isProcessing, modifier = Modifier.weight(1f)) { Text("Transferencia") }; OutlinedButton(onSplit, enabled = !cartIsEmpty && !isProcessing, modifier = Modifier.weight(1f)) { Text("Mixto") } }
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
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = BSPOSTheme.colors.surface,
        tonalElevation = 8.dp,
        icon = {
            Surface(shape = CircleShape, color = BSPOSTheme.colors.primaryLight) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.padding(12.dp), tint = BSPOSTheme.colors.primary)
            }
        },
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
    AlertDialog(
        onDismissRequest = onClear,
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(28.dp),
        containerColor = BSPOSTheme.colors.surface,
        tonalElevation = 8.dp,
        icon = {
            Surface(shape = CircleShape, color = BSPOSTheme.colors.primaryLight) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(12.dp), tint = BSPOSTheme.colors.primary)
            }
        },
        title = { Text("Seleccionar cliente") },
        text = {
            if (customers.isEmpty()) {
                Text("No hay clientes activos con crédito disponible. Registra o actualiza uno desde Clientes.")
            } else {
                LazyColumn {
                    lazyItems(customers, key = { it.id }) { customer ->
                        TextButton({ onSelect(customer) }, Modifier.fillMaxWidth()) {
                            Text("${customer.fullName} - Disponible: ${money(customer.creditLimit - customer.balance)}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClear) { Text("Cancelar") } }
    )
}

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)
