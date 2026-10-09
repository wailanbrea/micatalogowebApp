package com.example.bspos.presentation.dashboard

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SaleItem
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.model.SaleStatus
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.common.DialogScrollableColumn
import com.example.bspos.presentation.pos.InvoicePdfGenerator
import com.example.bspos.presentation.pos.PosCartLine
import com.example.bspos.presentation.pos.PosViewModel
import com.example.bspos.presentation.pos.shareInvoicePdf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    onNewSale: () -> Unit = {},
    onCollections: () -> Unit = {},
    onInventory: () -> Unit = {},
    onProducts: () -> Unit = {},
    onStorefront: () -> Unit = {},
    onCustomers: () -> Unit = {},
    onRoutes: () -> Unit = {},
    onReturns: () -> Unit = {},
    onEncargos: () -> Unit = {},
    onDayClose: () -> Unit = {},
    onMovements: () -> Unit = {},
    onStockFilter: (String) -> Unit = {},
    onPhotos: () -> Unit = {},
    onProfile: () -> Unit = {},
    onSupport: () -> Unit = {},
    onHideSupport: () -> Unit = {},
    showEncargos: Boolean = false,
    routesEnabled: Boolean = false,
    sellerMode: Boolean = false,
    showSales: Boolean = true,
    showCollections: Boolean = true,
    showInventory: Boolean = true,
    showProducts: Boolean = true,
    shopId: String? = null,
    showCustomers: Boolean = false,
    showSupport: Boolean = true,
    onAllSales: () -> Unit = {},
    presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    businessName: String = "tu negocio",
    isExpanded: Boolean = false,
    viewModel: DashboardViewModel = hiltViewModel(),
    printerViewModel: PosViewModel = hiltViewModel()
) {
    if (sellerMode) {
        SellerDashboardScreen(shopId, businessName, showSales, showProducts, showCustomers,
            onNewSale, onProducts, onCustomers, onAllSales, onSupport, onHideSupport, showSupport, viewModel)
        return
    }
    val sales by viewModel.sales.collectAsState()
    val saleItems by viewModel.saleItems.collectAsState()
    val costTotals by viewModel.costTotals.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val products by viewModel.products.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val pendingOrders by viewModel.pendingOrders.collectAsState()
    val ordersError by viewModel.ordersError.collectAsState()
    LaunchedEffect(businessName, showEncargos) { if (showEncargos) viewModel.loadPendingOrders() }
    val selectedSale by viewModel.selectedSale.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val printers by printerViewModel.printers.collectAsState()
    val printerMessage by printerViewModel.printerMessage.collectAsState()
    val context = LocalContext.current
    var showAllSales by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("Hoy") }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    LaunchedEffect(printerMessage) {
        printerMessage?.let {
            com.example.bspos.presentation.common.UiErrorBus.show(it)
            printerViewModel.consumePrinterMessage()
        }
    }
    val completed = sales.filter { it.status == SaleStatus.COMPLETED }.sortedByDescending { it.date }
    val periodStart = selectedDate ?: when (selectedPeriod) {
        "Este mes" -> today.withDayOfMonth(1)
        "Últimos 7" -> today.minusDays(6)
        else -> today
    }
    val periodEnd = selectedDate ?: today
    val periodSales = completed.filter { it.date.atZone(zone).toLocalDate() >= periodStart }
        .filter { it.date.atZone(zone).toLocalDate() <= periodEnd }
    val periodLabel = selectedDate?.let { "del ${it.format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))}" }
        ?: if (selectedPeriod == "Hoy") "de hoy" else selectedPeriod.lowercase(Locale("es"))
    val todaySales = periodSales.sumOf { it.total }
    val todayCollected = periodSales.sumOf { it.paidAmount }
    val periodCost = periodSales.sumOf { costTotals[it.id] ?: 0L }
    val periodProfit = periodSales.sumOf { sale -> sale.total - (costTotals[sale.id] ?: 0L) }
    val averageTicket = if (periodSales.isEmpty()) 0L else periodSales.sumOf { it.total } / periodSales.size
    val receivable = customers.sumOf { it.balance }
    val periodSaleIds = periodSales.mapTo(mutableSetOf()) { it.id }
    val productById = products.associateBy { it.id }
    val topProducts = saleItems
        .asSequence()
        .filter { it.saleId in periodSaleIds }
        .groupBy { it.productId }
        .mapNotNull { (productId, lines) ->
            productById[productId]?.let { product ->
                TopProduct(product.name, lines.sumOf { it.quantity }, lines.sumOf { it.subtotal })
            }
        }
        .sortedWith(compareByDescending<TopProduct> { it.quantity }.thenByDescending { it.revenue })
        .take(5)
    val paymentMix = periodSales
        .groupBy { it.paymentType }
        .map { (type, salesForType) -> PaymentMix(type.label(), salesForType.sumOf { it.paidAmount }) }
        .filter { it.amount > 0L }
        .sortedByDescending { it.amount }
    val quantities = stock.associate { it.productId to it.quantity }
    val activeProducts = products.filter { it.isActive && it.deletedAt == null }
    val lowProducts = activeProducts.filter { product ->
        val quantity = quantities[product.id] ?: 0L
        quantity <= 0L || (product.minimumStock > 0 && quantity <= product.minimumStock)
    }.sortedBy { quantities[it.id] ?: 0L }
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val dailySales = week.map { day -> completed.filter { it.date.atZone(zone).toLocalDate() == day }.sumOf { it.total } }
    val customerNames = customers.associate { it.id to it.businessName }
    val visibleWidgets = presentation.dashboardWidgets.ifEmpty {
        listOf("sales_today", "collections_today", "receivables", "average_ticket") + if (!sellerMode) listOf("profit", "low_stock", "active_customers") else emptyList()
    }
    val kpis = buildList {
        if ("sales_today" in visibleWidgets && showSales) add(Kpi("Ventas $periodLabel", money(todaySales), "Operación del periodo", Icons.Default.BarChart, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight))
        if ("collections_today" in visibleWidgets && showCollections) add(Kpi("Cobros $periodLabel", money(todayCollected), "Efectivo y otros métodos", Icons.Default.AccountBalanceWallet, BSPOSTheme.colors.success, BSPOSTheme.colors.successLight))
        if ("receivables" in visibleWidgets) add(Kpi("Cuentas por cobrar", money(receivable), "Cartera pendiente", Icons.Default.ReceiptLong, BSPOSTheme.colors.warning, BSPOSTheme.colors.warningLight))
        if ("profit" in visibleWidgets && !sellerMode) add(Kpi("Ganancia $periodLabel", money(periodProfit), "Según costo real", Icons.Default.BarChart, BSPOSTheme.colors.success, BSPOSTheme.colors.successLight))
        if ("average_ticket" in visibleWidgets) add(Kpi("Ticket promedio", money(averageTicket), "Por venta del periodo", Icons.Default.ReceiptLong, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight))
        if ("low_stock" in visibleWidgets && showInventory) add(Kpi("Productos bajos", lowProducts.size.toString(), "Requieren atencion", Icons.Default.Inventory2, BSPOSTheme.colors.error, BSPOSTheme.colors.errorLight))
        if ("active_customers" in visibleWidgets) add(Kpi("Clientes activos", customers.count { it.isActive }.toString(), "Directorio comercial", Icons.Default.People, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight))
        if (routesEnabled) add(Kpi("Rutas activas", routes.count { it.isActive }.toString(), "Disponibles hoy", Icons.Default.LocationOn, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight))
    }
    ResumenOverview(
        businessName = businessName, sales = completed, saleItems = saleItems, costTotals = costTotals,
        customers = customers, products = products, quantities = quantities, payments = payments,
        pendingOrders = pendingOrders, ordersError = ordersError,
        showSales = showSales, showCollections = showCollections, showInventory = showInventory,
        showEncargos = showEncargos, showCost = !sellerMode,
        onNewSale = onNewSale, onCollections = onCollections, onInventory = onInventory,
        onStockFilter = onStockFilter, onMovements = onMovements, onProducts = onProducts,
        onPhotos = onPhotos, onEncargos = onEncargos, onDayClose = onDayClose,
        onStorefront = onStorefront, onProfile = onProfile,
        onSupport = onSupport, onHideSupport = onHideSupport, showSupport = showSupport,
        onAllSales = { showAllSales = true }, onSale = viewModel::selectSale
    )
    if (showAllSales) {
        RecentSalesDialog(
            sales = completed,
            customers = customerNames,
            onSelect = { sale -> showAllSales = false; viewModel.selectSale(sale) },
            onDismiss = { showAllSales = false }
        )
    }
    selectedSale?.let { sale ->
        SaleDetailDialog(
            sale = sale,
            customerName = customerNames[sale.customerId] ?: "Consumidor final",
            items = selectedItems,
            products = products,
            productNames = products.associate { it.id to it.name },
            onShare = {
                val lines = selectedItems.mapNotNull { item ->
                    products.find { it.id == item.productId }?.let { product ->
                        PosCartLine(product = product, quantity = item.quantity, unitPrice = item.unitPrice)
                    }
                }
                if (lines.isNotEmpty()) {
                    val pdfUri = InvoicePdfGenerator.create(context, sale, lines)
                    shareInvoicePdf(context, pdfUri, sale.invoiceNumber)
                }
            },
            onPrint = {
                val lines = selectedItems.mapNotNull { item ->
                    products.find { it.id == item.productId }?.let { product ->
                        PosCartLine(product = product, quantity = item.quantity, unitPrice = item.unitPrice)
                    }
                }
                if (lines.isEmpty()) {
                    com.example.bspos.presentation.common.UiErrorBus.show("No hay productos asociados para imprimir esta venta.")
                } else if (printers.isEmpty()) {
                    com.example.bspos.presentation.common.UiErrorBus.show("Configura una impresora Bluetooth desde Impresoras para usar Térmico.")
                } else {
                    printerViewModel.printSaleToConfiguredPrinter(sale, lines)
                }
            },
            onReturn = { viewModel.selectSale(null); onReturns() },
            onDismiss = { viewModel.selectSale(null) }
        )
    }
}

@Composable
private fun DashboardEnter(delayMillis: Long = 0, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(360)) + slideInVertically(animationSpec = tween(360)) { it / 10 }
    ) { content() }
}

@Composable
private fun DashboardPeriodSelector(
    selected: String,
    customDate: LocalDate?,
    onSelected: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val periods = listOf("Hoy", "Este mes", "Últimos 7")
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        periods.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelected(period) },
                label = { Text(period, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BSPOSTheme.colors.secondaryNavy,
                    selectedLabelColor = Color.White,
                    containerColor = BSPOSTheme.colors.surface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == period,
                    borderColor = BSPOSTheme.colors.outline,
                    selectedBorderColor = BSPOSTheme.colors.secondaryNavy
                )
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {
            val initial = customDate ?: LocalDate.now()
            DatePickerDialog(
                context,
                { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth
            ).show()
        }) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Seleccionar fecha",
                tint = if (customDate != null) BSPOSTheme.colors.primary else BSPOSTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun GuidedSetupCard(
    steps: List<Pair<String, Boolean>>,
    onCash: () -> Unit,
    onProduct: () -> Unit,
    onSale: () -> Unit,
    onShare: () -> Unit
) {
    val actions = listOf(onCash, onProduct, onSale, onShare)
    val completed = steps.count { it.second }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.primary.copy(alpha = .35f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Primeros pasos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("$completed de ${steps.size}", color = BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
            }
            Text(
                "Completa estas acciones para dejar tu negocio listo para operar.",
                color = BSPOSTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            steps.forEachIndexed { index, (label, done) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { actions[index]() }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (done) BSPOSTheme.colors.success else BSPOSTheme.colors.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text(if (done) "✓" else "${index + 1}", color = if (done) Color.White else BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = if (done) BSPOSTheme.colors.textSecondary else BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("→", color = BSPOSTheme.colors.primary, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun StorefrontProgressCard(onOpen: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val steps = listOf(
        "Sube tu logo",
        "Agrega tu WhatsApp",
        "Completa el catálogo",
        "Define tu horario",
        "Enlaza tus redes",
        "Revisa cómo se ve"
    )
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(BSPOSTheme.colors.primaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = BSPOSTheme.colors.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Termina tu tienda", fontWeight = FontWeight.ExtraBold)
                    Text("Sigue la guía para dejarla lista para tus clientes.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text("${steps.size} pasos", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Text(if (expanded) "⌃" else "⌄", color = BSPOSTheme.colors.primary, fontSize = 20.sp)
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                    steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = BSPOSTheme.colors.primary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(24.dp))
                            Text(step, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Text("Abrir guía de Mi tienda")
                    }
                }
            }
        }
    }
}

private data class Kpi(val title: String, val value: String, val helper: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: Color, val soft: Color)
private data class TopProduct(val name: String, val quantity: Long, val revenue: Long)
private data class PaymentMix(val label: String, val amount: Long)

@Composable
private fun DashboardHeading(
    sellerMode: Boolean,
    businessName: String,
    presentation: MiCatalogoBusinessPresentation
) {
    Column {
        Text(
            "RESUMEN",
            color = BSPOSTheme.colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Buenas, ${businessName.ifBlank { "tu negocio" }}",
            color = BSPOSTheme.colors.secondaryNavy,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            if (sellerMode) "Así van tus ventas hoy."
            else "Así va tu negocio hoy.",
            color = BSPOSTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
        if (presentation.dashboardTitle.isNotBlank() && presentation.dashboardTitle != "Resumen de tu negocio") {
            Text(
                presentation.dashboardTitle,
                color = BSPOSTheme.colors.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun KpiCard(item: Kpi, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(item.soft), contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = item.accent) }
                Spacer(Modifier.width(10.dp))
                Text(item.title, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 2)
            }
            Spacer(Modifier.height(10.dp))
            Text(item.value, color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.helper, color = item.accent, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun InventorySummaryCard(
    products: List<Product>,
    quantities: Map<java.util.UUID, Long>,
    lowProducts: List<Product>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val units = products.sumOf { (quantities[it.id] ?: 0L).coerceAtLeast(0L) }
    val capitalAtCost = products.sumOf { product ->
        (quantities[product.id] ?: 0L).coerceAtLeast(0L) * product.averageCost.coerceAtLeast(0L)
    }
    val inStock = products.count { (quantities[it.id] ?: 0L) > 0L }
    val outOfStock = products.count { (quantities[it.id] ?: 0L) <= 0L }
    val lowWithoutOut = lowProducts.count { (quantities[it.id] ?: 0L) > 0L }

    SectionCard(modifier) {
        SectionTitle("Tu inventario", Icons.Default.Inventory2, "Capital y existencias", onOpen)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(Modifier.weight(1.35f)) {
                Text(money(capitalAtCost), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Capital al costo", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
            InventoryMetric(inStock.toString(), "productos", Modifier.weight(1f))
            InventoryMetric(units.toString(), "unidades", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 500.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InventoryStatusChip("$inStock con existencia", BSPOSTheme.colors.successLight, BSPOSTheme.colors.success, Modifier.weight(1f))
                        InventoryStatusChip("$lowWithoutOut bajo mínimo", BSPOSTheme.colors.warningLight, BSPOSTheme.colors.warning, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth()) {
                        InventoryStatusChip("$outOfStock agotados", BSPOSTheme.colors.errorLight, BSPOSTheme.colors.error, Modifier.fillMaxWidth(.5f))
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InventoryStatusChip("$inStock con existencia", BSPOSTheme.colors.successLight, BSPOSTheme.colors.success, Modifier.weight(1f))
                    InventoryStatusChip("$lowWithoutOut bajo mínimo", BSPOSTheme.colors.warningLight, BSPOSTheme.colors.warning, Modifier.weight(1f))
                    InventoryStatusChip("$outOfStock agotados", BSPOSTheme.colors.errorLight, BSPOSTheme.colors.error, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InventoryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InventoryStatusChip(label: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = background, modifier = modifier) {
        Text(label, color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
    }
}

@Composable
private fun QuickActions(
    onNewSale: () -> Unit,
    onCollections: () -> Unit,
    onInventory: () -> Unit,
    onProducts: () -> Unit,
    compact: Boolean,
    sellerMode: Boolean,
    showSales: Boolean,
    showCollections: Boolean,
    showInventory: Boolean,
    showProducts: Boolean,
    configuredActions: List<String>
) {
    val allowedActions = configuredActions.ifEmpty { listOf("new_sale", "collect", "inventory", "new_product") }
    val actions = buildList {
        if (showSales && "new_sale" in allowedActions) add(QuickAction("Nueva venta", Icons.Default.ShoppingCart, true, onNewSale))
        if (showCollections && "collect" in allowedActions) add(QuickAction("Registrar cobro", Icons.Default.AccountBalanceWallet, false, onCollections))
        if (showProducts && "new_product" in allowedActions) add(QuickAction("Productos", Icons.Default.Inventory2, false, onProducts))
        if (showInventory && "inventory" in allowedActions) add(QuickAction("Inventario", Icons.Default.Inventory2, false, onInventory))
    }

    if (actions.isEmpty()) return

    SectionCard {
        SectionTitle("Vender y cobrar", Icons.Default.Add)
        Spacer(Modifier.height(12.dp))
        if (compact) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { action -> ActionButton(action.label, action.icon, action.primary, action.onClick, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { action -> ActionButton(action.label, action.icon, action.primary, action.onClick, Modifier.weight(1f)) }
            }
        }
    }
}

private data class QuickAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val primary: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Button(onClick, modifier, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 13.dp), colors = ButtonDefaults.buttonColors(containerColor = if (primary) BSPOSTheme.colors.primary else BSPOSTheme.colors.surface, contentColor = if (primary) Color.White else BSPOSTheme.colors.primary)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WeeklySalesCard(values: List<Long>, labels: List<String>, modifier: Modifier = Modifier) {
    val primary = BSPOSTheme.colors.primary
    SectionCard(modifier) {
        SectionTitle("Ventas de la semana", Icons.Default.BarChart, "Ultimos 7 dias")
        Spacer(Modifier.height(18.dp))
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            val maximum = max(1L, values.maxOrNull() ?: 0L).toFloat()
            val slot = size.width / values.size
            values.forEachIndexed { index, value ->
                val barHeight = (value / maximum) * (size.height - 14.dp.toPx())
                val width = slot * .52f
                drawRoundRect(primary.copy(alpha = if (index == values.lastIndex) 1f else .45f), androidx.compose.ui.geometry.Offset(slot * index + (slot - width) / 2f, size.height - barHeight), androidx.compose.ui.geometry.Size(width, barHeight), androidx.compose.ui.geometry.CornerRadius(14f, 14f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { labels.forEach { Text(it.take(3), color = BSPOSTheme.colors.textTertiary, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
private fun PortfolioCard(collected: Long, receivable: Long, customers: Int, modifier: Modifier = Modifier) {
    val success = BSPOSTheme.colors.success
    val errorLight = BSPOSTheme.colors.errorLight
    val total = max(1L, collected + receivable).toFloat()
    SectionCard(modifier) {
        SectionTitle("Cartera y cobros", Icons.Default.AccountBalanceWallet, "Cobrado frente a pendiente")
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(142.dp)) {
                drawArc(errorLight, 0f, 360f, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
                if (collected > 0) drawArc(success, -90f, 360f * collected / total, false, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(money(receivable), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge); Text("por cobrar", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium) }
        }
        HorizontalDivider(color = BSPOSTheme.colors.outline)
        Spacer(Modifier.height(10.dp))
        Text("$customers clientes con saldo pendiente", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TopProductsCard(products: List<TopProduct>, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        SectionTitle("Top productos", Icons.Default.Inventory2, "Más vendidos")
        Spacer(Modifier.height(10.dp))
        products.forEachIndexed { index, product ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(BSPOSTheme.colors.primaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", color = BSPOSTheme.colors.primary, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${product.quantity} unidad(es)", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Text(money(product.revenue), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun PaymentMixCard(payments: List<PaymentMix>, modifier: Modifier = Modifier) {
    val total = payments.sumOf { it.amount }.coerceAtLeast(1L)
    SectionCard(modifier) {
        SectionTitle("Dinero por método", Icons.Default.AccountBalanceWallet, "Cobrado en el periodo")
        Spacer(Modifier.height(10.dp))
        payments.forEach { payment ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(payment.label, fontWeight = FontWeight.SemiBold)
                    Text(money(payment.amount), fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { payment.amount.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                    color = BSPOSTheme.colors.primary,
                    trackColor = BSPOSTheme.colors.primaryLight
                )
            }
        }
    }
}

@Composable
private fun RoutesCard(routes: List<CommercialRoute>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        SectionTitle("Estado de rutas", Icons.Default.Route, "Rutas comerciales activas", onClick)
        Spacer(Modifier.height(10.dp))
        if (routes.isEmpty()) EmptyText("No hay rutas activas") else routes.take(3).forEach { route ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocationOn, null, tint = BSPOSTheme.colors.primary) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(route.name, fontWeight = FontWeight.Bold); Text(route.code, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium) }
                AssistChip(onClick = onClick, label = { Text("Activa") }, colors = AssistChipDefaults.assistChipColors(labelColor = BSPOSTheme.colors.success, containerColor = BSPOSTheme.colors.successLight))
            }
        }
    }
}

@Composable
private fun LowStockCard(products: List<Product>, quantities: Map<java.util.UUID, Long>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        SectionTitle("Productos con stock bajo", Icons.Default.WarningAmber, "Requieren reabastecimiento", onClick)
        Spacer(Modifier.height(10.dp))
        if (products.isEmpty()) EmptyText("Todo el inventario esta en niveles saludables") else products.forEach { product ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.errorLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.error) }
                Spacer(Modifier.width(10.dp))
                Text(product.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${quantities[product.id] ?: 0}", color = BSPOSTheme.colors.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecentSalesCard(sales: List<Sale>, customers: Map<java.util.UUID, String>, onSaleClick: (Sale) -> Unit, onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        SectionTitle("Ventas recientes", Icons.Default.ReceiptLong, "Actividad mas reciente", onViewAll)
        Spacer(Modifier.height(10.dp))
        if (sales.isEmpty()) EmptyText("Aun no hay ventas registradas") else recentSalesByDate(sales).forEach { (date, salesForDate) ->
            RecentSalesDateHeader(date)
            salesForDate.forEach { sale ->
                Row(Modifier.fillMaxWidth().clickable { onSaleClick(sale) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(BSPOSTheme.colors.secondaryNavy), contentAlignment = Alignment.Center) { Text("V", color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(customers[sale.customerId] ?: "Consumidor final", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(sale.invoiceNumber, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium) }
                    Text(money(sale.total), fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun RecentSalesDialog(
    sales: List<Sale>,
    customers: Map<java.util.UUID, String>,
    onSelect: (Sale) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ventas recientes") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recentSalesByDate(sales).forEach { (date, salesForDate) ->
                    item(key = date) { RecentSalesDateHeader(date) }
                    items(salesForDate, key = { it.id }) { sale ->
                        TextButton(onClick = { onSelect(sale) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(customers[sale.customerId] ?: "Consumidor final", fontWeight = FontWeight.Bold)
                                    Text(sale.invoiceNumber, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                                }
                                Text(money(sale.total), fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

private fun recentSalesByDate(sales: List<Sale>): Map<LocalDate, List<Sale>> =
    sales.groupBy { it.date.atZone(ZoneId.systemDefault()).toLocalDate() }

@Composable
private fun RecentSalesDateHeader(date: LocalDate) {
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    val label = when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es")))
    }

    Text(label.replaceFirstChar { it.titlecase(Locale("es")) }, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
internal fun SaleDetailDialog(
    sale: Sale,
    customerName: String,
    items: List<SaleItem>,
    products: List<Product>,
    productNames: Map<java.util.UUID, String>,
    onShare: () -> Unit,
    onPrint: (() -> Unit)? = null,
    onReturn: () -> Unit,
    onDismiss: () -> Unit
) {
    val date = sale.date.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    val cost = items.sumOf { item -> item.unitCostSnapshot * item.quantity }
    val gain = sale.total - cost
    val margin = if (sale.total > 0) gain.toDouble() / sale.total.toDouble() * 100 else 0.0
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.94f).heightIn(max = 760.dp),
            shape = BSPOSTheme.shapes.extraLarge,
            color = BSPOSTheme.colors.surface,
            tonalElevation = 6.dp
        ) {
            DialogScrollableColumn {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("VENTA", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(sale.invoiceNumber, color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                            Text("$date · Terminal", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                        }
                        Surface(shape = RoundedCornerShape(50), color = if (sale.status == SaleStatus.COMPLETED) BSPOSTheme.colors.successLight else BSPOSTheme.colors.errorLight) {
                            Text(if (sale.status == SaleStatus.COMPLETED) "COMPLETADA" else "ANULADA", color = if (sale.status == SaleStatus.COMPLETED) BSPOSTheme.colors.success else BSPOSTheme.colors.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Recibo") }
                        Button(onClick = onShare, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Compartir") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onPrint?.let { print ->
                            OutlinedButton(onClick = print, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Térmico") }
                        }
                        OutlinedButton(onClick = onReturn, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Devolver") }
                    }
                    FinanceSummaryCard(sale, cost, gain, margin)
                    InfoPairCard("Cliente", customerName, "Pago", sale.paymentType.label())
                    InfoPairCard("Estado", if (sale.status == SaleStatus.COMPLETED) "Completada" else "Anulada", "Origen", "Terminal")
                    SectionTitle("Productos", Icons.Default.Inventory2, "${items.size} artículo(s)")
                    if (items.isEmpty()) {
                        EmptyText("No hay productos asociados a esta venta.")
                    } else {
                        items.forEach { item ->
                            val product = products.find { it.id == item.productId }
                            SaleDetailLine(item, productNames[item.productId] ?: "Producto", product)
                        }
                    }
                    HorizontalDivider(color = BSPOSTheme.colors.outline)
                    SummaryRow("Subtotal", money(sale.subtotal))
                    if (sale.discount > 0) SummaryRow("Descuento", "- ${money(sale.discount)}", BSPOSTheme.colors.success)
                    if (sale.tax > 0) SummaryRow("Impuestos", money(sale.tax))
                    SummaryRow("Total", money(sale.total), BSPOSTheme.colors.textPrimary, emphasized = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cerrar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceSummaryCard(sale: Sale, cost: Long, gain: Long, margin: Double) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(15.dp)) {
            val metrics = listOf(
                Triple("COBRADO", money(sale.paidAmount), BSPOSTheme.colors.primary),
                Triple("COSTO", money(cost), BSPOSTheme.colors.textSecondary),
                Triple("GANANCIA", money(gain), BSPOSTheme.colors.success),
                Triple("MARGEN", "${String.format(Locale.US, "%.1f", margin)}%", BSPOSTheme.colors.success)
            )
            if (maxWidth < 520.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    metrics.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (label, value, color) -> FinanceMetric(label, value, color, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    metrics.forEach { (label, value, color) -> FinanceMetric(label, value, color, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FinanceMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoPairCard(firstLabel: String, firstValue: String, secondLabel: String, secondValue: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            InfoValue(firstLabel, firstValue, Modifier.weight(1f))
            InfoValue(secondLabel, secondValue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SaleDetailLine(item: SaleItem, name: String, product: Product?) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.quantity} × ${money(item.unitPrice)}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Text(money(item.subtotal), fontWeight = FontWeight.ExtraBold)
            }
            product?.let {
                val itemCost = item.unitCostSnapshot * item.quantity
                val itemGain = item.subtotal - itemCost
                Text("Costo ${money(itemCost)} · Ganancia ${money(itemGain)}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = BSPOSTheme.colors.textSecondary, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = color, fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.Normal)
        Text(value, color = if (emphasized) BSPOSTheme.colors.textPrimary else color, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BSPOSTheme.colors.primary); Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold); subtitle?.let { Text(it, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium) } }
        onClick?.let { TextButton(it) { Text("Ver todo") } }
    }
}

@Composable
private fun EmptyText(text: String) { Text(text, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 14.dp)) }

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)

private fun SalePaymentType.label(): String = when (this) {
    SalePaymentType.CASH -> "Efectivo"
    SalePaymentType.CARD -> "Tarjeta"
    SalePaymentType.TRANSFER -> "Transferencia"
    SalePaymentType.CREDIT -> "Crédito"
    SalePaymentType.MIXED -> "Mixto"
}
