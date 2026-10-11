package com.example.bspos.presentation.dashboard

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.bspos.presentation.common.BSPOSButton as Button
import com.example.bspos.presentation.common.BSPOSOutlinedButton as OutlinedButton
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.bspos.core.ui.theme.BSPOSFonts as FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.local.entity.PaymentEntity
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.domain.model.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal val ResumenInk = Color(0xFF0F172A)
internal val ResumenMuted = Color(0xFF64748B)
internal val ResumenAccent = Color(0xFF1E60F4)
private val Line = Color(0xFFE2E8F0)
private val Panel = Color(0xFFF1F5F9)
private val Green = Color(0xFF10B981)
private val Red = Color(0xFFEF4444)

internal data class ResumenRange(val from: LocalDate, val to: LocalDate) {
    val previous: ResumenRange get() {
        val days = ChronoUnit.DAYS.between(from, to) + 1
        return ResumenRange(from.minusDays(days), from.minusDays(1))
    }
}

internal fun resumenRange(period: String, today: LocalDate): ResumenRange = when (period) {
    "Este mes" -> ResumenRange(today.withDayOfMonth(1), today)
    "Últimos 7" -> ResumenRange(today.minusDays(6), today)
    else -> ResumenRange(today, today)
}

internal fun resumenComparison(current: Long, previous: Long): String = when {
    previous == 0L -> "sin datos previos"
    else -> {
        val percentage = (java.math.BigDecimal.valueOf(current).subtract(java.math.BigDecimal.valueOf(previous)))
            .multiply(java.math.BigDecimal.valueOf(100)).divide(java.math.BigDecimal.valueOf(previous).abs(), 0, java.math.RoundingMode.HALF_UP).toLong()
        "${if (percentage >= 0) "▲" else "▼"} ${kotlin.math.abs(percentage)}% vs periodo anterior"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResumenOverview(
    businessName: String,
    sales: List<Sale>,
    saleItems: List<SaleItem>,
    costTotals: Map<java.util.UUID, Long>,
    customers: List<Customer>,
    products: List<Product>,
    quantities: Map<java.util.UUID, Long>,
    payments: List<PaymentEntity>,
    pendingOrders: List<FeatureRowDto>?,
    ordersError: String?,
    showSales: Boolean,
    showCollections: Boolean,
    showInventory: Boolean,
    showEncargos: Boolean,
    showCost: Boolean,
    onNewSale: () -> Unit,
    onCollections: () -> Unit,
    onInventory: () -> Unit,
    onStockFilter: (String) -> Unit,
    onMovements: () -> Unit,
    onProducts: () -> Unit,
    onPhotos: () -> Unit,
    onEncargos: () -> Unit,
    onDayClose: () -> Unit,
    onStorefront: () -> Unit,
    onProfile: () -> Unit,
    onSupport: () -> Unit = {},
    onHideSupport: () -> Unit = {},
    showSupport: Boolean = true,
    firstSaleCompleted: Boolean? = null,
    setupStatusKnown: Boolean = true,
    offline: Boolean = false,
    onRetryStartup: () -> Unit = {},
    onAllSales: () -> Unit,
    onSale: (Sale) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var period by rememberSaveable { mutableStateOf("Hoy") }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    val range = if (customFrom != null && customTo != null) ResumenRange(LocalDate.parse(customFrom), LocalDate.parse(customTo)) else resumenRange(period, today)
    fun salesIn(r: ResumenRange) = sales.filter { sale -> sale.date.atZone(zone).toLocalDate().let { it >= r.from && it <= r.to } }
    val selectedSales = salesIn(range)
    val previousSales = salesIn(range.previous)
    val total = selectedSales.sumOf { it.total }
    val profit = selectedSales.sumOf { it.total - (costTotals[it.id] ?: 0L) }
    val average = if (selectedSales.isEmpty()) 0L else total / selectedSales.size
    val priorTotal = previousSales.sumOf { it.total }
    val priorProfit = previousSales.sumOf { it.total - (costTotals[it.id] ?: 0L) }
    val priorAverage = if (previousSales.isEmpty()) 0L else priorTotal / previousSales.size
    val active = products.filter { it.isActive && it.deletedAt == null }
    val tracked = active.filter { it.remoteSaleUnit != "service" && !it.remoteIsCombo }
    val inStock = tracked.count { (quantities[it.id] ?: 0) > 0 }
    val out = tracked.filter { (quantities[it.id] ?: 0) <= 0 }
    val low = tracked.filter { (quantities[it.id] ?: 0) > 0 && it.minimumStock > 0 && (quantities[it.id] ?: 0) <= it.minimumStock }
    val capital = tracked.filter { it.remoteSourceProductId == null }.sumOf { Math.multiplyExact((quantities[it.id] ?: 0L).coerceAtLeast(0), it.averageCost.coerceAtLeast(0)) }
    val decantCapital = tracked.filter { it.remoteSourceProductId != null }.sumOf { Math.multiplyExact((quantities[it.id] ?: 0L).coerceAtLeast(0), it.averageCost.coerceAtLeast(0)) }
    val balances = customers.filter { it.isActive && it.balance > 0 }.sortedByDescending { it.balance }
    val receivable = customers.sumOf { it.balance }
    val saleIds = selectedSales.map { it.id }.toSet()
    val top = saleItems.filter { it.saleId in saleIds }.groupBy { it.productId }.mapNotNull { (id, items) ->
        products.find { it.id == id }?.let { Triple(it.name, items.sumOf { line -> line.quantity }, items.sumOf { line -> line.subtotal }) }
    }.sortedByDescending { it.second }.take(5)
    val collected = payments.filter { payment -> payment.date.atZone(zone).toLocalDate().let { it >= range.from && it <= range.to } }
    val creditCollected = collected.sumOf { it.amount }
    val mix = selectedSales.groupBy { it.paymentType }.mapValues { (_, rows) -> rows.sumOf { it.paidAmount } }.filterValues { it > 0 }
    val context = LocalContext.current
    val onlyDay = range.from == range.to
    val bars = if (onlyDay) (0..23).map { hour ->
        "%02d:00".format(hour) to selectedSales.filter { it.date.atZone(zone).hour == hour }.sumOf { it.total }
    } else (0..ChronoUnit.DAYS.between(range.from, range.to).toInt()).map { offset ->
        val day = range.from.plusDays(offset.toLong())
        day.format(DateTimeFormatter.ofPattern("EEE d", Locale("es"))) to selectedSales.filter { it.date.atZone(zone).toLocalDate() == day }.sumOf { it.total }
    }
    LazyColumn(Modifier.fillMaxSize().background(BSPOSTheme.colors.background), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonoLabel("RESUMEN")
                Text("Buenas, ${businessName.ifBlank { "tu negocio" }}", color = ResumenInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Así va tu negocio hoy.", color = ResumenMuted, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showCollections) OutlinedButton(onCollections, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = ResumenInk), contentPadding = PaddingValues(12.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Payments, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Registrar cobro", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (showSales) Button(onNewSale, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = ResumenAccent), contentPadding = PaddingValues(12.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nueva venta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (showSupport) item { SupportSummaryCard(onSupport = onSupport, onHide = onHideSupport) }
        if (offline) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.warningLight),
                border = BorderStroke(1.dp, BSPOSTheme.colors.warning.copy(alpha = .35f))
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sin conexión", fontWeight = FontWeight.Bold, color = ResumenInk)
                        Text("Mostramos tus últimos datos guardados.", color = ResumenMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = onRetryStartup) { Text("Reintentar") }
                }
            }
        }
        val hasFirstSale = firstSaleCompleted ?: sales.isNotEmpty()
        if (showSales && setupStatusKnown && !hasFirstSale) {
            item { ResumenSetup(active.isNotEmpty(), hasFirstSale, onProducts, onNewSale, onStorefront, onProfile) }
        }
        item {
            Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Line), color = Color.White) {
                Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Hoy", "Este mes", "Últimos 7").forEach { label ->
                        val selected = customFrom == null && period == label
                        Surface(Modifier.clickable { customFrom = null; customTo = null; period = label }, shape = RoundedCornerShape(6.dp), color = if (selected) ResumenAccent else Color.White) {
                            Text(label, Modifier.padding(horizontal = 10.dp, vertical = 10.dp), color = if (selected) Color.White else ResumenMuted, fontSize = 13.sp)
                        }
                    }
                    IconButton(onClick = {
                        val first = range.from
                        DatePickerDialog(context, { _, y, m, d ->
                            val from = LocalDate.of(y, m + 1, d)
                            DatePickerDialog(context, { _, ey, em, ed ->
                                val to = LocalDate.of(ey, em + 1, ed)
                                customFrom = minOf(from, to).toString(); customTo = maxOf(from, to).toString()
                            }, range.to.year, range.to.monthValue - 1, range.to.dayOfMonth).apply { setTitle("Hasta"); show() }
                        }, first.year, first.monthValue - 1, first.dayOfMonth).apply { setTitle("Desde"); show() }
                    }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.CalendarMonth, "Seleccionar rango de fechas", tint = ResumenMuted, modifier = Modifier.size(19.dp)) }
                }
            }
            if (customFrom != null) Text("${range.from} — ${range.to}", color = ResumenMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        }
        item {
            Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                Column {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        MetricCell("VENTAS", amount(total), resumenComparison(total, priorTotal), Modifier.weight(1f), if (total < priorTotal) Red else Green)
                        VerticalDivider(color = Line)
                        MetricCell("GANANCIA", if (showCost) amount(profit) else "Restringido", if (showCost) resumenComparison(profit, priorProfit) else "Sin permiso de costos", Modifier.weight(1f), if (profit < priorProfit) Red else Green)
                    }
                    HorizontalDivider(color = Line)
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        MetricCell("TICKET PROMEDIO", amount(average), resumenComparison(average, priorAverage), Modifier.weight(1f), if (average < priorAverage) Red else Green)
                        VerticalDivider(color = Line)
                        MetricCell("POR COBRAR", amount(receivable), if (receivable == 0L) "al día" else "${balances.size} clientes pendientes", Modifier.weight(1f), if (receivable > 0L) ResumenAccent else Green)
                    }
                }
            }
        }
        if (showEncargos) item {
            SummaryPanel("Encargos", Icons.Default.CalendarMonth, "Ver agenda", onEncargos) {
                when {
                    ordersError != null -> Text(ordersError, color = ResumenMuted, fontSize = 13.sp)
                    pendingOrders == null -> Text("Consultando encargos…", color = ResumenMuted, fontSize = 13.sp)
                    pendingOrders.isEmpty() -> Text("Nada pendiente de entregar.", color = ResumenMuted, fontSize = 13.sp)
                    else -> pendingOrders.take(5).forEach { row ->
                        Row(Modifier.fillMaxWidth().clickable(onClick = onEncargos).padding(vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) { Text(row.primary, color = ResumenInk, fontSize = 13.sp); Text(row.secondary, color = ResumenMuted, fontSize = 11.sp) }
                            Text(row.status, color = ResumenAccent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (showInventory) item {
            SummaryPanel("Tu inventario", Icons.Default.Inventory2, "Ver inventario", onInventory) {
                Surface(Modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (showCost) amount(capital + decantCapital) else "Restringido", fontFamily = FontFamily.Monospace, color = ResumenInk, fontSize = 24.sp)
                        Text("Capital al costo", color = ResumenMuted, fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    CountMetric(tracked.size.toString(), "productos", Modifier.weight(1f))
                    CountMetric(tracked.sumOf { (quantities[it.id] ?: 0L).coerceAtLeast(0) }.toString(), "unidades", Modifier.weight(1f))
                }
                HorizontalDivider(color = Line)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    StatePill("$inStock con existencia", Green, { onStockFilter("in_stock") })
                    StatePill("${low.size} bajo mínimo", if (low.isEmpty()) ResumenMuted else ResumenAccent, { onStockFilter("low") })
                    StatePill("${out.size} agotados", if (out.isEmpty()) ResumenMuted else Red, { onStockFilter("out") })
                }
                if (decantCapital > 0 && showCost) Text("Incluye ${amount(decantCapital)} en decants.", color = ResumenMuted, fontSize = 12.sp)
                TextButton(onMovements, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = ResumenMuted)) {
                    Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ver movimientos")
                }
            }
        }
        item { IncomeChart(bars, total, average, selectedSales.size, onlyDay) }
        if (showSales) item {
            SummaryPanel("Ventas recientes", Icons.Default.ReceiptLong, "Ver todas", onAllSales, padded = false) {
                if (selectedSales.isEmpty()) EmptySummary("Aún no hay ventas ${if (onlyDay) "hoy" else "en este periodo"}", "Las ventas del periodo aparecerán aquí.", 140)
                else {
                    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("VENTA / CLIENTE", color = ResumenMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("TOTAL / ESTADO", color = ResumenMuted, fontSize = 10.sp)
                    }
                    selectedSales.take(5).forEach { sale ->
                        Row(Modifier.fillMaxWidth().clickable { onSale(sale) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sale.invoiceNumber, fontSize = 12.sp, color = ResumenInk, fontWeight = FontWeight.Bold)
                                Text(customers.find { it.id == sale.customerId }?.fullName ?: "Venta directa", color = ResumenMuted, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(amount(sale.total), color = ResumenInk, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Text(if (sale.pendingAmount > 0) "CRÉDITO" else "PAGADO", color = if (sale.pendingAmount > 0) ResumenAccent else Green, fontSize = 9.sp)
                                Text(sale.date.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale("es"))), color = ResumenMuted, fontSize = 9.sp)
                            }
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
        }
        item {
            SummaryPanel("Top productos", Icons.Default.TrendingUp, "Ver todos", onProducts) {
                if (top.isEmpty()) EmptySummary("Sin ventas en este periodo", height = 130)
                else top.forEach { (name, quantity, revenue) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row {
                            Text(name, Modifier.weight(1f), fontSize = 12.sp, color = ResumenInk, maxLines = 2)
                            Text("$quantity", color = ResumenMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        Box(Modifier.fillMaxWidth().padding(top = 5.dp)) { Box(Modifier.fillMaxWidth(quantity.toFloat() / top.maxOf { it.second }.coerceAtLeast(1).toFloat()).height(5.dp).clip(CircleShape).background(ResumenAccent)) }
                        Text(amount(revenue), fontSize = 11.sp, color = ResumenMuted)
                    }
                }
            }
        }
        if (showCollections) item {
            SummaryPanel("Por cobrar", Icons.Default.AccountBalanceWallet, "Crédito", onCollections) {
                if (balances.isEmpty()) Text("Sin cuentas por cobrar.", color = ResumenMuted, fontSize = 13.sp)
                else balances.take(5).forEach { customer ->
                    Row(Modifier.fillMaxWidth().clickable(onClick = onCollections).padding(vertical = 6.dp)) {
                        Text(customer.fullName, Modifier.weight(1f), color = ResumenInk, fontSize = 13.sp)
                        Text(amount(customer.balance), fontFamily = FontFamily.Monospace, color = ResumenInk, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            SummaryPanel("Dinero por método", Icons.Default.Payments, "Cierre", onDayClose) {
                mix.forEach { (method, received) -> MoneyRow(when(method) { SalePaymentType.CASH -> "Efectivo"; SalePaymentType.CARD -> "Tarjeta"; SalePaymentType.TRANSFER -> "Transferencia"; SalePaymentType.CREDIT -> "Crédito"; SalePaymentType.MIXED -> "Mixto" }, received) }
                MoneyRow("Crédito cobrado", creditCollected)
                HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 8.dp))
                MoneyRow("Total recibido", mix.values.sum() + creditCollected, true)
            }
        }
        if (showInventory) item {
            SummaryPanel("Inventario bajo", Icons.Default.WarningAmber, "Reponer", { onStockFilter("low") }) {
                if (low.isEmpty() && out.isEmpty()) Text("Todo bien surtido.", color = ResumenMuted, fontSize = 13.sp)
                else (low + out).take(5).forEach { product ->
                    Row(Modifier.fillMaxWidth().clickable { onStockFilter("low") }.padding(vertical = 6.dp)) {
                        Text(product.name, Modifier.weight(1f), color = ResumenInk, fontSize = 13.sp)
                        Text("${quantities[product.id] ?: 0}", color = Red, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }
        val withoutPhoto = active.count { it.imagePath.isNullOrBlank() && it.thumbnailPath.isNullOrBlank() }
        if (withoutPhoto > 0) item {
            Surface(Modifier.fillMaxWidth().clickable(onClick = onPhotos), color = BSPOSTheme.colors.primaryLight, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ResumenAccent.copy(alpha = .2f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, tint = ResumenAccent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                    Text("$withoutPhoto productos sin foto — revisar", color = ResumenInk, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ResumenSetup(hasProduct: Boolean, hasSale: Boolean, onProduct: () -> Unit, onSale: () -> Unit, onStorefront: () -> Unit, onProfile: () -> Unit) {
    var hidden by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val steps = listOf(Triple("Cuenta conectada", true, onProfile), Triple("Agrega tu primer producto", hasProduct, onProduct), Triple("Haz tu primera venta", hasSale, onSale))
    Surface(Modifier.fillMaxWidth(), color = BSPOSTheme.colors.primaryLight, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ResumenAccent.copy(alpha = .25f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PRIMEROS PASOS ${steps.count { it.second }} de ${steps.size}", Modifier.weight(1f), color = ResumenMuted, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                Text(if (hidden) "Mostrar" else "Ocultar", Modifier.clickable { hidden = !hidden }.padding(4.dp), color = ResumenMuted, fontSize = 12.sp)
            }
            if (!hidden) {
                Text("Ya puedes vender. Completa la configuración de tu negocio. ¿Dudas? Abre Ayuda.", color = ResumenMuted, fontSize = 14.sp)
                steps.forEach { (label, done, action) ->
                    Surface(Modifier.fillMaxWidth().clickable(onClick = action), color = Color.White, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Line)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(if (done) Green else Color.White), contentAlignment = Alignment.Center) {
                                if (done) Text("✓", color = Color.White, fontSize = 13.sp) else Canvas(Modifier.fillMaxSize()) { drawCircle(Line, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(label, color = if (done) ResumenMuted else ResumenInk, fontSize = 13.sp, textDecoration = if (done) TextDecoration.LineThrough else null)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (expanded) "⌄" else "›", color = ResumenMuted); Spacer(Modifier.width(10.dp)); Text("Termina tu tienda", color = ResumenMuted, fontSize = 13.sp); Spacer(Modifier.width(10.dp)); Text("6 pasos", color = ResumenMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                if (expanded) {
                    listOf("Sube tu logo", "Agrega tu WhatsApp", "Completa el catálogo", "Define tu horario", "Enlaza tus redes", "Revisa cómo se ve").forEach { step -> TextButton(onClick = onStorefront, contentPadding = PaddingValues(0.dp)) { Text("○  $step", color = ResumenInk, fontSize = 13.sp) } }
                }
            }
        }
    }
}

@Composable
private fun SummaryPanel(title: String, icon: ImageVector, action: String, onAction: () -> Unit, padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)) {
        Column {
            Row(Modifier.fillMaxWidth().background(Panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(7.dp), color = Color.White, border = BorderStroke(1.dp, Line)) { Icon(icon, null, tint = ResumenMuted, modifier = Modifier.padding(5.dp).size(18.dp)) }
                Spacer(Modifier.width(8.dp)); Text(title, Modifier.weight(1f), color = ResumenInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Surface(Modifier.clickable(onClick = onAction), color = Color.White, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Line)) { Text("$action →", Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = ResumenMuted, fontSize = 11.sp) }
            }
            HorizontalDivider(color = Line)
            Column(Modifier.fillMaxWidth().padding(if (padded) 16.dp else 0.dp), content = content)
        }
    }
}

@Composable private fun MonoLabel(text: String) { Text(text, color = ResumenMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.5.sp) }
@Composable private fun MetricCell(label: String, value: String, helper: String, modifier: Modifier, helperColor: Color) {
    Column(modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        MonoLabel(label)
        Text(value, color = ResumenInk, fontFamily = FontFamily.Monospace, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(helper, color = if (helper == "sin datos previos") ResumenMuted else helperColor, fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 2)
    }
}
@Composable private fun CountMetric(value: String, label: String, modifier: Modifier) { Column(modifier) { Text(value, color = ResumenInk, fontSize = 24.sp); Text(label, color = ResumenMuted, fontSize = 13.sp) } }
@Composable private fun StatePill(text: String, accent: Color, action: () -> Unit) { Surface(Modifier.clickable(onClick = action), color = accent.copy(alpha = .08f), shape = CircleShape) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = accent, fontSize = 11.sp) } }
@Composable private fun EmptySummary(title: String, description: String? = null, height: Int = 90) { Column(Modifier.fillMaxWidth().height(height.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = ResumenInk, fontWeight = FontWeight.Bold, fontSize = 16.sp); if (description != null) Text(description, color = ResumenMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp)) } }
@Composable private fun MoneyRow(label: String, value: Long, bold: Boolean = false) { Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) { Text(label, Modifier.weight(1f), color = ResumenMuted, fontSize = 13.sp); Text(amount(value), color = ResumenInk, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal) } }

@Composable
private fun IncomeChart(bars: List<Pair<String, Long>>, total: Long, average: Long, count: Int, hourly: Boolean) {
    var selected by remember(bars) { mutableStateOf(bars.maxByOrNull { it.second }?.takeIf { it.second > 0L }) }
    val maxValue = bars.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val ceiling = if (maxValue <= 1) 1L else {
        val step = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(maxValue.toDouble()))).toLong().coerceAtLeast(1)
        ((maxValue + step - 1) / step) * step
    }
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(18.dp)) {
            MonoLabel("INGRESOS")
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    (0..4).forEach { tick ->
                        val y = size.height * tick / 4
                        drawLine(Line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    if (bars.isNotEmpty()) {
                        val slotWidth = size.width / bars.size
                        val barWidth = (slotWidth * .68f).coerceAtLeast(2.dp.toPx())
                        bars.forEachIndexed { index, bar ->
                            if (bar.second > 0L) {
                                val barHeight = (size.height * (bar.second.toFloat() / ceiling).coerceIn(0f, 1f)).coerceAtLeast(6.dp.toPx())
                                drawRoundRect(
                                    color = ResumenAccent,
                                    topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2f, size.height - barHeight),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    bars.forEachIndexed { index, bar ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selected = bar }
                                .semantics { contentDescription = "${bar.first}: ${bar.second} centavos en ventas" }
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val labelIndexes = if (hourly) listOf(0, 6, 12, 18, 23) else listOf(0, bars.size / 4, bars.size / 2, (bars.size * 3) / 4, bars.lastIndex)
                labelIndexes.distinct().forEach { index ->
                    Text(bars.getOrNull(index)?.first.orEmpty(), color = ResumenMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
            selected?.let { Text("${it.first} · ${amount(it.second)}", color = ResumenAccent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            HorizontalDivider(color = Line, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
            if (count == 0) Text("Sin ventas en este periodo", color = ResumenMuted, fontSize = 14.sp)
            else Row(verticalAlignment = Alignment.CenterVertically) { Text(amount(total), color = ResumenInk, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("$count ventas · Promedio ${amount(average)}", color = ResumenMuted, fontSize = 10.sp) }
        }
    }
}

@Composable private fun amount(cents: Long) = MoneyUtils.formatCents(cents, LocalCurrency.current)
