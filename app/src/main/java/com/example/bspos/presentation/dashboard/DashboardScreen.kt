package com.example.bspos.presentation.dashboard

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
    onCustomers: () -> Unit = {},
    onRoutes: () -> Unit = {},
    routesEnabled: Boolean = false,
    sellerMode: Boolean = false,
    showSales: Boolean = true,
    showCollections: Boolean = true,
    showInventory: Boolean = true,
    showProducts: Boolean = true,
    presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    isExpanded: Boolean = false,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val sales by viewModel.sales.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val products by viewModel.products.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val selectedSale by viewModel.selectedSale.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    var showAllSales by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf("Hoy") }
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    val completed = sales.filter { it.status == SaleStatus.COMPLETED }.sortedByDescending { it.date }
    val todaySales = completed.filter { it.date.atZone(zone).toLocalDate() == today }.sumOf { it.total }
    val todayCollected = completed.filter { it.date.atZone(zone).toLocalDate() == today }.sumOf { it.paidAmount }
    val receivable = customers.sumOf { it.balance }
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
        listOf("sales_today", "collections_today", "receivables") + if (!sellerMode) listOf("low_stock", "active_customers") else emptyList()
    }
    val kpis = buildList {
        if ("sales_today" in visibleWidgets && showSales) add(Kpi("Ventas de hoy", money(todaySales), "Operacion del dia", Icons.Default.BarChart, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight))
        if ("collections_today" in visibleWidgets && showCollections) add(Kpi("Cobros de hoy", money(todayCollected), "Efectivo y otros metodos", Icons.Default.AccountBalanceWallet, BSPOSTheme.colors.success, BSPOSTheme.colors.successLight))
        if ("receivables" in visibleWidgets) add(Kpi("Cuentas por cobrar", money(receivable), "Cartera pendiente", Icons.Default.ReceiptLong, BSPOSTheme.colors.warning, BSPOSTheme.colors.warningLight))
        if ("low_stock" in visibleWidgets && showInventory) add(Kpi("Productos bajos", lowProducts.size.toString(), "Requieren atencion", Icons.Default.Inventory2, BSPOSTheme.colors.error, BSPOSTheme.colors.errorLight))
        if ("active_customers" in visibleWidgets) add(Kpi("Clientes activos", customers.count { it.isActive }.toString(), "Directorio comercial", Icons.Default.People, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight))
        if (routesEnabled) add(Kpi("Rutas activas", routes.count { it.isActive }.toString(), "Disponibles hoy", Icons.Default.LocationOn, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = !isExpanded || maxWidth < 840.dp
        val kpiColumns = if (!compact) 3 else if (maxWidth >= 400.dp) 2 else 1
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background),
            contentPadding = PaddingValues(if (compact) 16.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DashboardEnter { DashboardHeading(compact, sellerMode, presentation) } }
            item { DashboardPeriodSelector(selectedPeriod) { selectedPeriod = it } }
            item {
                val setupItems = listOf(
                    "Agrega tu primer producto" to activeProducts.isNotEmpty(),
                    "Haz tu primera venta" to completed.isNotEmpty(),
                    "Comparte tu catálogo" to false
                )
                if (setupItems.any { !it.second }) {
                    DashboardEnter(delayMillis = 70) {
                        GuidedSetupCard(
                            steps = setupItems,
                            onProduct = onProducts,
                            onSale = onNewSale,
                            onShare = onProducts
                        )
                    }
                }
            }
            item {
                DashboardEnter(delayMillis = 120) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        kpis.chunked(kpiColumns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { item -> KpiCard(item, Modifier.weight(1f)) }
                                repeat(kpiColumns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item { DashboardEnter(delayMillis = 170) { QuickActions(onNewSale, onCollections, onInventory, onProducts, compact, sellerMode, showSales, showCollections, showInventory, showProducts, presentation.dashboardQuickActions) } }
            item {
                DashboardEnter(delayMillis = 220) {
                    if (sellerMode) {
                        WeeklySalesCard(dailySales, week.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es")) })
                    } else if (compact) {
                        WeeklySalesCard(dailySales, week.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es")) })
                        Spacer(Modifier.height(16.dp))
                        if ("receivables" in visibleWidgets) PortfolioCard(completed.sumOf { it.paidAmount }, receivable, customers.count { it.balance > 0 })
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            WeeklySalesCard(dailySales, week.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es")) }, Modifier.weight(1.35f))
                            if ("receivables" in visibleWidgets) PortfolioCard(completed.sumOf { it.paidAmount }, receivable, customers.count { it.balance > 0 }, Modifier.weight(.85f))
                        }
                    }
                }
            }
            item {
                DashboardEnter(delayMillis = 270) {
                    if (sellerMode) {
                        RecentSalesCard(completed.take(10), customerNames, viewModel::selectSale, { showAllSales = true })
                    } else if (compact) {
                        if (routesEnabled) RoutesCard(routes.filter { it.isActive }, onRoutes)
                        Spacer(Modifier.height(16.dp))
                        if ("low_stock" in visibleWidgets && showInventory) LowStockCard(lowProducts.take(5), quantities, onInventory)
                        Spacer(Modifier.height(16.dp))
                        RecentSalesCard(completed.take(5), customerNames, viewModel::selectSale, { showAllSales = true })
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (routesEnabled) RoutesCard(routes.filter { it.isActive }, onRoutes, Modifier.weight(1f))
                            if ("low_stock" in visibleWidgets && showInventory) LowStockCard(lowProducts.take(5), quantities, onInventory, Modifier.weight(1f))
                            RecentSalesCard(completed.take(5), customerNames, viewModel::selectSale, { showAllSales = true }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
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
            productNames = products.associate { it.id to it.name },
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
private fun DashboardPeriodSelector(selected: String, onSelected: (String) -> Unit) {
    val periods = listOf("Hoy", "Este mes", "Últimos 7")
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
        Text("▣", color = BSPOSTheme.colors.textSecondary, fontSize = 20.sp)
    }
}

@Composable
private fun GuidedSetupCard(
    steps: List<Pair<String, Boolean>>,
    onProduct: () -> Unit,
    onSale: () -> Unit,
    onShare: () -> Unit
) {
    val actions = listOf(onProduct, onSale, onShare)
    val completed = steps.count { it.second }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.primary.copy(alpha = .35f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Termina tu tienda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("$completed de ${steps.size}", color = BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
            }
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

private data class Kpi(val title: String, val value: String, val helper: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: Color, val soft: Color)

@Composable
private fun DashboardHeading(compact: Boolean, sellerMode: Boolean, presentation: MiCatalogoBusinessPresentation) {
    Column {
        Text(
            when {
                sellerMode -> "Mis ventas"
                presentation.dashboardTitle.isNotBlank() -> presentation.dashboardTitle
                compact -> "Ventas y cobros"
                else -> "Resumen de ventas"
            },
            color = BSPOSTheme.colors.secondaryNavy,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            if (sellerMode) "Resumen de tus ventas, cobros y clientes pendientes."
            else "Controla tus ventas, cobros y clientes pendientes desde un solo lugar.",
            color = BSPOSTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
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
private fun SaleDetailDialog(
    sale: Sale,
    customerName: String,
    items: List<SaleItem>,
    productNames: Map<java.util.UUID, String>,
    onDismiss: () -> Unit
) {
    val date = sale.date.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detalle de ${sale.invoiceNumber}") },
        text = {
            Column {
                Text(customerName, fontWeight = FontWeight.Bold)
                Text(date, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                Text("Pago: ${sale.paymentType.label()}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (items.isEmpty()) {
                    Text("No hay productos asociados a esta venta.", color = BSPOSTheme.colors.textSecondary)
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(items, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(productNames[item.productId] ?: "Producto", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${item.quantity} x ${money(item.unitPrice)}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                                }
                                Text(money(item.subtotal), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.ExtraBold)
                    Text(money(sale.total), fontWeight = FontWeight.ExtraBold)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Cerrar") } }
    )
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
