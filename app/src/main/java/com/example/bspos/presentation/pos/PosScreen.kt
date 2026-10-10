package com.example.bspos.presentation.pos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
import com.example.bspos.presentation.common.BSPOSButton as Button
import com.example.bspos.presentation.common.BSPOSOutlinedButton as OutlinedButton
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.example.bspos.core.ui.theme.BSPOSFonts as FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.common.BSPOSModalBottomSheet
import coil.compose.AsyncImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    creditOnly: Boolean = false,
    presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    onOpenQuotes: () -> Unit = {},
    onOpenDayClose: () -> Unit = {},
    onOpenCustomers: () -> Unit = {},
    onOpenServices: () -> Unit = {},
    showServicesAction: Boolean = true,
    showQuoteAction: Boolean = true,
    showDayCloseAction: Boolean = true,
    showCosts: Boolean = false,
    onOpenMenu: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: PosViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsState()
    val recentProductIds by viewModel.recentProductIds.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val cartTotal by viewModel.cartTotal.collectAsState()
    val cartSubtotal by viewModel.cartSubtotal.collectAsState()
    val discount by viewModel.discount.collectAsState()
    val saleDate by viewModel.saleDate.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val customer by viewModel.customer.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val settings by viewModel.settings.collectAsState()
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
    // Puntto keeps the help available from the terminal header without
    // occupying the catalog on every visit. The guide remains one tap away.
    var showingQuickGuide by rememberSaveable { mutableStateOf(false) }
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
            (catalogTab == "all" || (catalogTab == "products" && it.remoteSaleUnit != "decant" && it.remoteSaleUnit != "service") || (catalogTab == "services" && it.remoteSaleUnit == "service") || (catalogTab == "decants" && it.remoteSaleUnit == "decant")) &&
            (selectedCategoryId == null || it.categoryId == selectedCategoryId) &&
            (it.name.contains(query, true) ||
                it.internalCode.contains(query, true) ||
                it.barcode?.contains(query, true) == true)
    }
    val cartQuantity = cart.sumOf { it.quantity }
    val categoryOptions = categories.filter { category -> products.any { product -> product.categoryId == category.id } }
    val hasDecants = products.any { it.remoteSaleUnit == "decant" }
    val hasServices = products.any { it.remoteSaleUnit == "service" }
    val decantsCount = products.count { it.isActive && it.deletedAt == null && it.remoteSaleUnit == "decant" }
    val servicesCount = products.count { it.isActive && it.deletedAt == null && it.remoteSaleUnit == "service" }
    val recentProducts = recentProductIds.mapNotNull { id -> products.firstOrNull { it.id == id } }
    LaunchedEffect(categoryOptions) {
        if (selectedCategoryId != null && categoryOptions.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }
    LaunchedEffect(hasDecants) {
        if (!hasDecants && catalogTab == "decants") catalogTab = "all"
    }
    LaunchedEffect(hasServices) {
        if (!hasServices && catalogTab == "services") catalogTab = "all"
    }
    // The cash register is owned by the server. POS sales are stored locally
    // and queued for synchronization when the connection is unavailable.
    val cashAction = { showCart = false; reviewMethod = CheckoutReviewMethod.CASH }
    val cardAction = { showCart = false; reviewMethod = CheckoutReviewMethod.CARD }
    val transferAction = { showCart = false; reviewMethod = CheckoutReviewMethod.TRANSFER }
    val creditAction = { if (customer == null) choosingCustomer = true else { showCart = false; reviewMethod = CheckoutReviewMethod.CREDIT } }

    LaunchedEffect(checkoutResult) {
        checkoutResult?.let { result ->
            if (result.success) {
                showCart = false
                showingSplitDialog = false
                reviewMethod = null
            } else {
                if (showingSplitDialog || reviewMethod != null) return@LaunchedEffect
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
                TerminalHeader({ showingTerminalGuide = true }, { showingTerminalOptions = true })
                if (!creditOnly && showingQuickGuide) {
                    TerminalQuickGuideCard(onDismiss = { showingQuickGuide = false })
                }
                if (!creditOnly) {
                    TerminalModeRow(onQuote = onOpenQuotes)
                }
                if (presentation.posShowWholesale) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !wholesaleMode, onClick = { viewModel.setWholesaleMode(false) }, label = { Text("Detalle") }, enabled = !isProcessing)
                        FilterChip(selected = wholesaleMode, onClick = { viewModel.setWholesaleMode(true) }, label = { Text("Mayoreo") }, enabled = !isProcessing)
                    }
                }
                Spacer(Modifier.height(14.dp))
                SearchField(query, { query = it }, presentation.posSearchPlaceholder) { showingBarcodeScanner = true }
                ProductKindFilterRow(catalogTab, hasDecants, hasServices) { catalogTab = it }
                if (!creditOnly && showServicesAction) {
                    ServiceQuickAction(onOpenServices = onOpenServices)
                }
                CategoryFilterRow(categoryOptions, selectedCategoryId) { selectedCategoryId = it }
                if (catalogTab == "all" && query.isBlank() && selectedCategoryId == null && recentProducts.isNotEmpty()) {
                    RecentProductsRow(recentProducts, quantities, settings.allowNegativeStock, { viewModel.add(it) })
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                     ProductGrid(catalog, quantities, selectedQuantities, settings.allowNegativeStock, { viewModel.add(it) }, { product, quantity -> viewModel.change(product.id, quantity) }, 4, wholesaleMode, Modifier.weight(1f))
                     CartPanel(cart, cartTotal, customer, { choosingCustomer = true }, { viewModel.change(it.product.id, it.quantity - 1) }, { viewModel.change(it.product.id, it.quantity + 1) }, { viewModel.change(it.product.id, 0) }, viewModel::clearCart, cashAction, cardAction, transferAction, creditAction, { showingSplitDialog = true }, isProcessing, creditOnly, presentation.posShowCredit || creditOnly, Modifier.widthIn(min = 340.dp, max = 400.dp).fillMaxHeight())
                }
            }
        } else {
            Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    bottomBar = {
                        Surface(color = Color.White, shadowElevation = 0.dp) {
                            if (cart.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).dashedRoundedBorder(Color(0xFFD4D4D8)),
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("+", color = Color(0xFF71717A), style = MaterialTheme.typography.titleLarge)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Toca un producto para empezar la venta", color = Color(0xFF71717A))
                                    }
                                }
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).clickable { reviewMethod = if (creditOnly) CheckoutReviewMethod.CREDIT else CheckoutReviewMethod.CASH },
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(0xFF2563EB)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ShoppingCart, null, tint = Color.White)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Cobrar", color = Color.White, fontWeight = FontWeight.ExtraBold)
                                            Text("$cartQuantity ${if (cartQuantity == 1L) "artículo" else "artículos"}", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(money(cartTotal), color = Color.White, fontWeight = FontWeight.ExtraBold)
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                ) { contentPadding ->
                    val mobileCatalog = catalog.filter { product ->
                        when (catalogTab) {
                            "decants" -> product.remoteSaleUnit == "decant"
                            "services" -> product.remoteSaleUnit == "service"
                            else -> product.remoteSaleUnit != "decant" && product.remoteSaleUnit != "service"
                        }
                    }
                    Column(Modifier.fillMaxSize().background(Color.White).padding(contentPadding)) {
                        PunttoTerminalHeader(
                            creditOnly = creditOnly,
                            wholesaleMode = wholesaleMode,
                            wholesaleEnabled = true,
                            onBack = onNavigateBack,
                            onWholesaleChanged = { viewModel.setWholesaleMode(it) },
                            onOpenMenu = onOpenMenu,
                            onGuide = { showingTerminalGuide = true },
                            onOptions = { showingTerminalOptions = true },
                            onQuote = onOpenQuotes,
                            isProcessing = isProcessing
                        )
                        PunttoSearchRow(
                            query = query,
                            placeholder = "Buscar o escanear...",
                            onQueryChange = { query = it },
                            onScan = { showingBarcodeScanner = true },
                            onService = onOpenServices,
                            showService = !creditOnly && showServicesAction
                        )
                        Spacer(Modifier.height(8.dp))
                        PunttoKindFilterRow(catalogTab, decantsCount, servicesCount) { catalogTab = it }
                        HorizontalDivider(color = Color(0xFFE4E4E7), modifier = Modifier.padding(top = 10.dp))
                        if (catalogTab == "all" && query.isBlank() && selectedCategoryId == null && recentProducts.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            PunttoRecentGrid(recentProducts.filter { !wholesaleMode || (it.wholesalePrice ?: 0L) > 0L }, quantities, selectedQuantities, settings.allowNegativeStock, { viewModel.add(it) }, wholesaleMode)
                        }
                        Spacer(Modifier.height(8.dp))
                        PunttoProductList(
                            products = mobileCatalog,
                            quantities = quantities,
                            selectedQuantities = selectedQuantities,
                            allowNegativeStock = settings.allowNegativeStock,
                            wholesaleMode = wholesaleMode,
                            onAdd = { viewModel.add(it) },
                            onChangeQuantity = { product, quantity -> viewModel.change(product.id, quantity) },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (showCart) {
        BSPOSModalBottomSheet(
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
                onSplit = { showCart = false; showingSplitDialog = true },
                isProcessing = isProcessing,
                creditOnly = creditOnly,
                creditEnabled = presentation.posShowCredit || creditOnly,
                 modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 12.dp).imePadding().navigationBarsPadding()
            )
        }
    }
    if (showingTerminalGuide) {
        TerminalGuideDialog { showingTerminalGuide = false }
    }
    if (showingTerminalOptions) {
        TerminalOptionsSheet(
            onDismiss = { showingTerminalOptions = false },
            showQuote = showQuoteAction,
            showDayClose = showDayCloseAction,
            onDayClose = { showingTerminalOptions = false; onOpenDayClose() },
            onQuote = { showingTerminalOptions = false; onOpenQuotes() },
            categoryFilters = { CategoryFilterRow(categoryOptions, selectedCategoryId) { selectedCategoryId = it } }
        )
    }
    if (showingSplitDialog) {
        SplitPaymentDialog(
            totalCents = cartTotal,
            customerName = customer?.fullName,
            creditAvailable = customer?.let { (it.creditLimit - it.balance).coerceAtLeast(0L) },
            creditEnabled = presentation.posShowCredit || creditOnly,
            isProcessing = isProcessing,
            errorMessage = checkoutResult?.takeUnless { it.success }?.message,
            onChooseCustomer = { choosingCustomer = true },
            onDismiss = { if (!isProcessing) { showingSplitDialog = false; viewModel.consumeCheckoutResult() } },
            onConfirm = { payments, dueDate ->
                viewModel.consumeCheckoutResult()
                viewModel.completeSplit(payments, dueDate)
            }
        )
    }
    reviewMethod?.let { method ->
        CheckoutReviewSheet(
            cart = cart,
            subtotal = cartSubtotal,
            discount = discount,
            customer = customer,
            method = method,
            wholesaleMode = wholesaleMode,
            saleDate = saleDate,
            quantities = quantities,
            creditEnabled = presentation.posShowCredit || creditOnly,
            showCosts = showCosts,
            isProcessing = isProcessing,
            errorMessage = checkoutResult?.takeUnless { it.success }?.message,
            onCreditConfirm = { abono, method, notes, reference, dueDate ->
                viewModel.consumeCheckoutResult()
                viewModel.completeCredit(dueDate, notes, abono, method, reference)
            },
            onDismiss = { if (!isProcessing) reviewMethod = null },
            onCustomer = { if (!isProcessing) choosingCustomer = true },
            onNewCustomer = { if (!isProcessing) { reviewMethod = null; onOpenCustomers() } },
            onQuantity = viewModel::change,
            onUnitPrice = viewModel::changeUnitPrice,
            onClear = viewModel::clearCart,
            onDiscount = viewModel::setDiscount,
            onDate = viewModel::setSaleDate,
            onMethodChange = { next ->
                if (!isProcessing) {
                    reviewMethod = next
                    if (next == CheckoutReviewMethod.CREDIT && customer == null) choosingCustomer = true
                }
            },
            onMixed = {
                if (!isProcessing) {
                    reviewMethod = null
                    showingSplitDialog = true
                }
            },
            onConfirm = { notes, reference, dueDate ->
                viewModel.consumeCheckoutResult()
                when (method) {
                    CheckoutReviewMethod.CASH -> viewModel.completeCash(notes)
                    CheckoutReviewMethod.CARD -> viewModel.completeCard(notes)
                    CheckoutReviewMethod.TRANSFER -> viewModel.completeTransfer(reference, notes)
                    CheckoutReviewMethod.CREDIT -> viewModel.completeCredit(dueDate, notes)
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
    if (choosingCustomer) {
        CustomerDialog(
            customers = customers.filter { it.isActive && it.deletedAt == null },
            requiresCredit = reviewMethod == CheckoutReviewMethod.CREDIT || creditOnly,
            onSelect = { viewModel.selectCustomer(it); choosingCustomer = false },
            onClear = { viewModel.selectCustomer(null); choosingCustomer = false },
            onOpenCustomers = {
                choosingCustomer = false
                onOpenCustomers()
            }
        )
    }
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
private fun TerminalHeader(onGuide: () -> Unit, onOptions: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onGuide) { Text("Ayuda") }
        OutlinedButton(onClick = onOptions, shape = RoundedCornerShape(14.dp)) { Text("Opciones") }
    }
}

/**
 * Compact terminal shell based on Puntto's mobile terminal. The mobile POS
 * deliberately avoids the desktop-style page heading and keeps the first
 * viewport focused on selecting a product.
 */
@Composable
private fun PunttoTerminalHeader(
    creditOnly: Boolean,
    wholesaleMode: Boolean,
    wholesaleEnabled: Boolean,
    onBack: () -> Unit,
    onWholesaleChanged: (Boolean) -> Unit,
    onOpenMenu: () -> Unit,
    onGuide: () -> Unit,
    onOptions: () -> Unit,
    isProcessing: Boolean,
    onQuote: () -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showBackButton = maxWidth >= 420.dp
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack, enabled = !isProcessing, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = BSPOSTheme.colors.textSecondary)
                }
            }
        Surface(
            modifier = Modifier.widthIn(max = 235.dp).weight(1f, fill = false).height(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF0F0F2)
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable(enabled = !isProcessing) { onWholesaleChanged(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (!wholesaleMode) Color.White else Color.Transparent,
                        shadowElevation = if (!wholesaleMode) 1.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("Detalle", fontWeight = if (!wholesaleMode) FontWeight.Bold else FontWeight.Normal, color = BSPOSTheme.colors.textPrimary, fontSize = 13.sp) }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable(enabled = wholesaleEnabled && !isProcessing) { onWholesaleChanged(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (wholesaleMode) Color.White else Color.Transparent,
                        shadowElevation = if (wholesaleMode) 1.dp else 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text("Mayoreo", fontWeight = if (wholesaleMode) FontWeight.Bold else FontWeight.Normal, color = if (wholesaleMode) BSPOSTheme.colors.textPrimary else BSPOSTheme.colors.textSecondary, fontSize = 13.sp) }
                    }
                }
            }
        }
        OutlinedButton(onQuote, enabled = !isProcessing, contentPadding = PaddingValues(horizontal = 8.dp), shape = RoundedCornerShape(50)) { Text("Cotizar", fontSize = 12.sp) }
        IconButton(onClick = onGuide, enabled = !isProcessing, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.HelpOutline, contentDescription = "Ayuda", tint = Color(0xFF3F3F46))
        }
            IconButton(onClick = onOptions, enabled = !isProcessing, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreHoriz, contentDescription = "Más opciones", tint = Color(0xFF3F3F46))
            }
        }
    }
}

@Composable
internal fun PunttoSearchRow(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onScan: () -> Unit,
    onService: () -> Unit,
    showService: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f).height(44.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4E4E7))
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = BSPOSTheme.colors.textPrimary, fontSize = 16.sp),
                        decorationBox = { field ->
                            if (query.isBlank()) Text(placeholder, color = BSPOSTheme.colors.textTertiary, fontSize = 16.sp)
                            field()
                        }
                    )
                }
            }
        }
        OutlinedButton(onScan, modifier = Modifier.size(44.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear código", modifier = Modifier.size(20.dp))
        }
        if (showService) {
            Button(
                onClick = onService,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BSPOSTheme.colors.secondaryNavy)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Servicio", maxLines = 1)
            }
        }
    }
}

@Composable
private fun PunttoKindFilterRow(
    selected: String,
    decantsCount: Int,
    servicesCount: Int,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PunttoTerminalChip(selected == "all" || selected == "products", "Productos", onClick = { onSelected("products") })
        PunttoTerminalChip(selected == "decants", "Decants ($decantsCount)", onClick = { onSelected("decants") })
        PunttoTerminalChip(selected == "services", "Servicios ($servicesCount)", onClick = { onSelected("services") })
    }
}

@Composable
internal fun PunttoTerminalChip(selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) BSPOSTheme.colors.secondaryNavy else BSPOSTheme.colors.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4E4E7))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            color = if (selected) Color.White else Color(0xFF52525B),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PunttoProductList(
    products: List<Product>,
    quantities: Map<java.util.UUID, Long>,
    selectedQuantities: Map<java.util.UUID, Long>,
    allowNegativeStock: Boolean,
    wholesaleMode: Boolean,
    onAdd: (Product) -> Unit,
    onChangeQuantity: (Product, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(if (wholesaleMode) "No hay productos con precio por mayor para esta búsqueda." else "Sin productos para mostrar", modifier = Modifier.padding(20.dp), color = BSPOSTheme.colors.textSecondary)
        }
        return
    }
    LazyColumn(
        modifier = modifier.testTag("pos-product-list").background(Color.White),
        contentPadding = PaddingValues(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        lazyItems(products, key = { it.id }) { product ->
            val available = quantities[product.id] ?: 0L
            val selected = selectedQuantities[product.id] ?: 0L
            val canIncrease = product.remoteSaleUnit == "service" || selected < available
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = canIncrease) { onAdd(product) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerminalProductThumbnail(product)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = Color(0xFF18181B), fontSize = 13.5.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(money(if (wholesaleMode) product.wholesalePrice ?: product.salePrice else product.salePrice), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFF18181B))
                    if (selected > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onChangeQuantity(product, selected - 1) }, modifier = Modifier.size(27.dp)) {
                                Icon(Icons.Default.Remove, contentDescription = "Disminuir", modifier = Modifier.size(17.dp))
                            }
                            Text(selected.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { if (canIncrease) onAdd(product) }, enabled = canIncrease, modifier = Modifier.size(27.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF4F4F5))
        }
    }
}

@Composable
private fun PunttoRecentGrid(
    products: List<Product>,
    quantities: Map<java.util.UUID, Long>,
    selectedQuantities: Map<java.util.UUID, Long>,
    allowNegativeStock: Boolean,
    onAdd: (Product) -> Unit,
    wholesaleMode: Boolean = false
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("RECIENTES", color = Color(0xFF71717A), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            products.take(2).forEach { product ->
                val quantity = quantities[product.id] ?: 0L
                val selected = selectedQuantities[product.id] ?: 0L
                val canAdd = product.remoteSaleUnit == "service" || selected < quantity
                Surface(
                    modifier = Modifier.weight(1f).clickable(enabled = canAdd) { onAdd(product) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4E4E7)),
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF18181B))
                        Text(money(if (wholesaleMode) product.wholesalePrice ?: product.salePrice else product.salePrice), color = Color(0xFF71717A), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

private fun productInitials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "PR" }

@Composable
private fun TerminalProductThumbnail(product: Product) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF2F2F4),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE4E4E7))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(productInitials(product.name), color = Color(0xFF71717A), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            val image = product.thumbnailPath?.takeIf { it.isNotBlank() } ?: product.imagePath?.takeIf { it.isNotBlank() }
            if (image != null) AsyncImage(image, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

private fun Modifier.dashedRoundedBorder(color: Color, strokeWidth: Dp = 1.dp, cornerRadius: Dp = 18.dp): Modifier = drawWithContent {
    drawContent()
    val stroke = strokeWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()), 0f))
    )
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

@Composable
private fun TerminalQuickGuideCard(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight),
        border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.primary.copy(alpha = .28f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("OPERACIÓN · GUÍA RÁPIDA", color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Ocultar") }
            }
            Text("Vende en pocos pasos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
            Text("Busca productos, agrégalos al carrito y cobra sin perder el control del inventario.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideStep("1", "Busca o escanea", "Filtra por nombre, categoría o código y toca un producto para agregarlo.")
                GuideStep("2", "Revisa el carrito", "Ajusta cantidades, cliente, precio detalle o mayorista y tipo de venta.")
                GuideStep("3", "Cobra o cotiza", "El cobro descuenta inventario; la cotización prepara una propuesta sin tocarlo.")
            }
        }
    }
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
private fun TerminalModeRow(onQuote: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MODO", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(50),
                color = BSPOSTheme.colors.primary,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Cobrar",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            FilterChip(
                selected = false,
                onClick = onQuote,
                label = { Text("Cotizar", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalOptionsSheet(onDismiss: () -> Unit, onDayClose: () -> Unit, onQuote: () -> Unit, showQuote: Boolean, showDayClose: Boolean, categoryFilters: @Composable () -> Unit = {}) {
    BSPOSModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Opciones de la terminal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            categoryFilters()

            if (showQuote) TextButton(onClick = onQuote, modifier = Modifier.fillMaxWidth()) { Text("Crear cotización", modifier = Modifier.fillMaxWidth()) }
            if (showDayClose) TextButton(onClick = onDayClose, modifier = Modifier.fillMaxWidth()) { Text("Cierre de día", modifier = Modifier.fillMaxWidth()) }
            if (!showQuote && !showDayClose) Text("No tienes acciones adicionales habilitadas.", color = BSPOSTheme.colors.textSecondary)
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
            shape = BSPOSTheme.shapes.extraLarge,
            color = BSPOSTheme.colors.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
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
    hasServices: Boolean,
    onSelected: (String) -> Unit
) {
    // When the catalog only contains normal products, the category row below
    // is the single filter Puntto presents. Showing another "Todos" row in
    // that case made the terminal look duplicated and added no behavior.
    if (!hasDecants && !hasServices) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == "all",
            onClick = { onSelected("all") },
            label = { Text("Todos los tipos") }
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
        if (hasServices) {
            FilterChip(
                selected = selected == "services",
                onClick = { onSelected("services") },
                label = { Text("Servicios") }
            )
        }
    }
}

@Composable
private fun ServiceQuickAction(onOpenServices: () -> Unit) {
    OutlinedButton(
        onClick = onOpenServices,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Servicio")
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(6.dp), color = BSPOSTheme.colors.primaryLight) {
            Text("F4", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary)
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
                val canAdd = product.remoteSaleUnit == "service" || quantity > 0 || allowNegativeStock
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
internal fun BarcodeScannerSheet(onDismiss: () -> Unit, onCode: (String) -> Unit) {
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

    BSPOSModalBottomSheet(onDismissRequest = onDismiss) {
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

@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
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
internal fun ProductGrid(products: List<Product>, quantities: Map<java.util.UUID, Long>, selectedQuantities: Map<java.util.UUID, Long>, allowNegativeStock: Boolean, onAdd: (Product) -> Unit, onChangeQuantity: (Product, Long) -> Unit, columns: Int, wholesaleMode: Boolean = false, modifier: Modifier = Modifier) {
    if (products.isEmpty()) Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Sin productos para mostrar", color = BSPOSTheme.colors.textSecondary) } else LazyVerticalGrid(GridCells.Fixed(columns), modifier.testTag("pos-product-grid"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(products, key = { it.id }) { product ->
            val quantity = quantities[product.id] ?: 0L
            val selected = selectedQuantities[product.id] ?: 0L
            val canAdd = product.remoteSaleUnit == "service" || quantity > 0 || allowNegativeStock
            Card(Modifier.clickable { if (canAdd) onAdd(product) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(13.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(15.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) {
                        val image = product.thumbnailPath ?: product.imagePath
                        if (image != null) AsyncImage(image, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(38.dp))
                         IconButton({ if (canAdd) onAdd(product) }, Modifier.align(Alignment.TopEnd), enabled = canAdd) { Icon(Icons.Default.Add, "Agregar", tint = BSPOSTheme.colors.primary) }
                        if (selected > 0) {
                            Surface(
                                Modifier.align(Alignment.BottomCenter).padding(6.dp),
                                shape = RoundedCornerShape(50),
                                color = BSPOSTheme.colors.surface,
                                shadowElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { onChangeQuantity(product, selected - 1) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, "Disminuir cantidad de ${product.name}", tint = BSPOSTheme.colors.primary)
                                    }
                                    Text(selected.toString(), fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                                    IconButton(
                                        onClick = { onAdd(product) },
                                        modifier = Modifier.size(32.dp),
                                        enabled = canAdd
                                    ) {
                                        Icon(Icons.Default.Add, "Aumentar cantidad de ${product.name}", tint = BSPOSTheme.colors.primary)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(product.name, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(money(if (wholesaleMode) product.wholesalePrice ?: product.salePrice else product.salePrice), color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.ExtraBold)
                     Surface(shape = RoundedCornerShape(50), color = if (product.remoteSaleUnit == "service") BSPOSTheme.colors.primaryLight else if (quantity > 0) BSPOSTheme.colors.successLight else if (allowNegativeStock) BSPOSTheme.colors.warningLight else BSPOSTheme.colors.errorLight) { Text(if (product.remoteSaleUnit == "service") "Servicio · sin inventario" else if (quantity > 0) "En stock ($quantity)" else if (allowNegativeStock) "Stock negativo permitido" else "Agotado", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = if (product.remoteSaleUnit == "service") BSPOSTheme.colors.primary else if (quantity > 0) BSPOSTheme.colors.success else if (allowNegativeStock) BSPOSTheme.colors.warning else BSPOSTheme.colors.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
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
    onSplit: () -> Unit, isProcessing: Boolean, creditOnly: Boolean, creditEnabled: Boolean,
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
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TerminalProductThumbnail(line.product)
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
            Text(line.product.name, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(money(price), color = BSPOSTheme.colors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(Math.multiplyExact(line.quantity, price)), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMinus(line) }, enabled = !processing, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Remove, "Disminuir cantidad de ${line.product.name}", modifier = Modifier.size(17.dp)) }
                Text(line.quantity.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.widthIn(min = 18.dp))
                IconButton(onClick = { onPlus(line) }, enabled = !processing, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Add, "Aumentar cantidad de ${line.product.name}", modifier = Modifier.size(17.dp)) }
            }
        }
        IconButton(onClick = { onRemove(line) }, enabled = !processing, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.DeleteOutline, "Eliminar ${line.product.name}", tint = BSPOSTheme.colors.error, modifier = Modifier.size(17.dp)) }
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
private fun CartActions(cartIsEmpty: Boolean, isProcessing: Boolean, creditSelected: Boolean, hasCustomer: Boolean, onCash: () -> Unit, onCredit: () -> Unit, onCard: () -> Unit, onTransfer: () -> Unit, onSplit: () -> Unit, creditOnly: Boolean) {
    val enabled = !cartIsEmpty && !isProcessing && (!creditSelected || hasCustomer)
    Button(onClick = { if (creditSelected || creditOnly) onCredit() else onCash() }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp)) { if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else { Text(if (creditSelected || creditOnly) "Vender a crédito" else "Cobrar", fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } }
    if (!creditOnly && !creditSelected) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCard,
                    enabled = !cartIsEmpty && !isProcessing,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text("Tarjeta", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onTransfer,
                    enabled = !cartIsEmpty && !isProcessing,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text("Transferencia", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedButton(
                onClick = onSplit,
                enabled = !cartIsEmpty && !isProcessing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text("Mixto", maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
internal fun SplitPaymentDialog(
    totalCents: Long,
    customerName: String?,
    onDismiss: () -> Unit,
    onConfirm: (List<com.example.bspos.domain.usecase.PosPaymentSplitInput>, String?) -> Unit,
    creditAvailable: Long? = null,
    creditEnabled: Boolean = true,
    isProcessing: Boolean = false,
    errorMessage: String? = null,
    onChooseCustomer: () -> Unit = {}
) {
    var cashText by rememberSaveable { mutableStateOf("") }
    var cardText by rememberSaveable { mutableStateOf("") }
    var transferText by rememberSaveable { mutableStateOf("") }
    var useCredit by rememberSaveable { mutableStateOf(false) }
    var referenceText by rememberSaveable { mutableStateOf("") }
    var dueDateText by rememberSaveable { mutableStateOf("") }
    val validation = validatePaymentWithAutomaticCredit(totalCents, cashText, cardText, transferText, useCredit,
        customerName != null, creditAvailable, creditEnabled, dueDateText)
    val sumCents = validation.sum
    val diffCents = validation.difference
    val creditCents = validation.credit

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
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
                    .heightIn(max = 420.dp)
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
                            Text("Abono inicial:", color = BSPOSTheme.colors.textSecondary)
                            Text(money(sumCents - creditCents), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                        Text("Saldo pendiente: ${money(creditCents)}", fontWeight = FontWeight.Bold)
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

                if (creditEnabled) TextButton(onClick = onChooseCustomer, enabled = !isProcessing) {
                    Text(customerName ?: "Seleccionar cliente para crédito")
                }
                (errorMessage ?: validation.error)?.let { error ->
                    Text(
                        error,
                        color = Color(0xFFE65100),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = cashText,
                    enabled = !isProcessing,
                    onValueChange = { cashText = it },
                    label = { Text("Efectivo (${LocalCurrency.current.symbol})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("split-cash"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = cardText,
                    enabled = !isProcessing,
                    onValueChange = { cardText = it },
                    label = { Text("Tarjeta (${LocalCurrency.current.symbol})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("split-card"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = transferText,
                    enabled = !isProcessing,
                    onValueChange = { transferText = it },
                    label = { Text("Transferencia (${LocalCurrency.current.symbol})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("split-transfer"),
                    singleLine = true
                )

                if (creditEnabled) Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = useCredit, enabled = !isProcessing, onCheckedChange = { useCredit = it })
                    Text("Saldo restante a crédito", Modifier.clickable(enabled = !isProcessing) { useCredit = !useCredit })
                }

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
                    onConfirm(validation.payments.map { payment ->
                        if (payment.method == "bank_transfer") payment.copy(reference = referenceText.trim().ifBlank { null }) else payment
                    }, validation.dueDate)
                },
                enabled = validation.canSubmit && !isProcessing
            ) {
                Text(if (isProcessing) "Registrando…" else "Confirmar Cobro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun CustomerDialog(
    customers: List<com.example.bspos.domain.model.Customer>,
    requiresCredit: Boolean,
    onSelect: (com.example.bspos.domain.model.Customer) -> Unit,
    onClear: () -> Unit,
    onOpenCustomers: () -> Unit
) {
    val selectableCustomers = customers.filter { !requiresCredit || it.creditLimit > it.balance }
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
            if (selectableCustomers.isEmpty()) {
                Text(
                    if (requiresCredit) "No hay clientes activos con crédito disponible. Registra o actualiza uno desde Clientes."
                    else "No hay clientes activos. Registra uno desde Clientes."
                )
            } else {
                LazyColumn {
                    lazyItems(selectableCustomers, key = { it.id }) { customer ->
                        TextButton({ onSelect(customer) }, Modifier.fillMaxWidth()) {
                            Text(
                                if (requiresCredit) "${customer.fullName} - Disponible: ${money(customer.creditLimit - customer.balance)}"
                                else customer.fullName
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenCustomers) { Text("Nuevo cliente") }
                TextButton(onClick = onClear) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)
