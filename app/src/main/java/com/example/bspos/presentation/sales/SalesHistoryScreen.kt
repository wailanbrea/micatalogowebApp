package com.example.bspos.presentation.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.model.SaleStatus
import com.example.bspos.presentation.dashboard.DashboardViewModel
import com.example.bspos.presentation.dashboard.SaleDetailDialog
import com.example.bspos.presentation.pos.InvoicePdfGenerator
import com.example.bspos.presentation.pos.PosCartLine
import com.example.bspos.presentation.pos.PosViewModel
import com.example.bspos.presentation.pos.shareInvoicePdf
import com.example.bspos.presentation.common.UiErrorBus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SalesHistoryScreen(
    onReturns: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    printerViewModel: PosViewModel = hiltViewModel()
) {
    val sales by viewModel.sales.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val products by viewModel.products.collectAsState()
    val printers by printerViewModel.printers.collectAsState()
    val printerMessage by printerViewModel.printerMessage.collectAsState()
    val selectedSale by viewModel.selectedSale.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val context = LocalContext.current
    var period by remember { mutableStateOf("Hoy") }
    var paymentFilter by remember { mutableStateOf("Todas") }
    var query by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(printerMessage) {
        printerMessage?.let {
            UiErrorBus.show(it)
            printerViewModel.consumePrinterMessage()
        }
    }

    val today = remember { Instant.now().atZone(ZoneId.systemDefault()).toLocalDate() }
    val start = when (period) {
        "Este mes" -> today.withDayOfMonth(1)
        "Últimos 7" -> today.minusDays(6)
        "Todo" -> null
        else -> today
    }
    val customerNames = customers.associate { it.id to it.fullName }
    val normalizedQuery = query.trim()
    val visibleSales = sales
        .filter { sale ->
            val isCancelled = sale.status != SaleStatus.COMPLETED
            when (paymentFilter) {
                "Anuladas" -> isCancelled
                else -> !isCancelled
            }
        }
        .filter { sale -> start == null || sale.date.atZone(ZoneId.systemDefault()).toLocalDate() >= start }
        .filter { sale ->
            when (paymentFilter) {
                "Pagadas" -> sale.pendingAmount <= 0
                "A crédito" -> sale.paymentType == SalePaymentType.CREDIT || sale.pendingAmount > 0
                else -> true
            }
        }
        .filter { sale ->
            normalizedQuery.isBlank() || sale.invoiceNumber.contains(normalizedQuery, ignoreCase = true) ||
                (customerNames[sale.customerId] ?: "").contains(normalizedQuery, ignoreCase = true)
        }
        .sortedByDescending { it.date }
    Column(
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ventas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                    Text("Consulta tus ventas, cobros y resultados.", color = BSPOSTheme.colors.textSecondary)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Hoy", "Este mes", "Últimos 7", "Todo").forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option },
                            label = { Text(option, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BSPOSTheme.colors.secondaryNavy,
                                selectedLabelColor = Color.White,
                                containerColor = BSPOSTheme.colors.surface
                            )
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Buscar factura o cliente...") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BSPOSTheme.colors.primary,
                        unfocusedBorderColor = BSPOSTheme.colors.outline,
                        focusedLeadingIconColor = BSPOSTheme.colors.primary
                    )
                )
            }
            item { SalesSummary(visibleSales) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = BSPOSTheme.colors.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Historial de ventas", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("${visibleSales.size} ventas", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Todas", "Pagadas", "A crédito", "Anuladas").forEach { option ->
                        FilterChip(
                            selected = paymentFilter == option,
                            onClick = { paymentFilter = option },
                            label = { Text(option) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BSPOSTheme.colors.primaryLight,
                                selectedLabelColor = BSPOSTheme.colors.primary,
                                containerColor = BSPOSTheme.colors.surface
                            )
                        )
                    }
                }
            }
            if (visibleSales.isEmpty()) {
                item { EmptySalesState() }
            } else {
                items(visibleSales, key = { it.id }) { sale ->
                    SaleHistoryCard(
                        sale = sale,
                        customerName = customerNames[sale.customerId] ?: "Consumidor final",
                        onClick = { viewModel.selectSale(sale) }
                    )
                }
            }
            item {
                val total = visibleSales.sumOf { it.total }
                Text("${visibleSales.size} ventas · ${money(total)}", color = BSPOSTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
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
                    UiErrorBus.show("No hay productos asociados para imprimir esta venta.")
                } else if (printers.isEmpty()) {
                    UiErrorBus.show("Configura una impresora Bluetooth desde Impresoras para usar Térmico.")
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
private fun SalesSummary(sales: List<Sale>) {
    val total = sales.sumOf { it.total }
    val average = if (sales.isEmpty()) 0 else total / sales.size
    val credit = sales.count { it.paymentType == SalePaymentType.CREDIT || it.pendingAmount > 0 }
    val creditRate = if (sales.isEmpty()) 0 else credit * 100 / sales.size
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val metrics = listOf(
                Triple("Total vendido", money(total), BSPOSTheme.colors.primary),
                Triple("Promedio por venta", money(average.toLong()), BSPOSTheme.colors.textPrimary),
                Triple("A crédito", "$creditRate%", BSPOSTheme.colors.warning)
            )
            if (maxWidth < 500.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    metrics.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { (label, value, color) -> Metric(label, value, color, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    metrics.forEach { (label, value, color) -> Metric(label, value, color, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SaleHistoryCard(sale: Sale, customerName: String, onClick: () -> Unit) {
    val date = sale.date.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = BSPOSTheme.colors.primary, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(sale.invoiceNumber, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                Text(customerName, color = BSPOSTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$date · ${sale.paymentType.label()}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(money(sale.total), fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                Text(if (sale.pendingAmount > 0) "PENDIENTE" else "PAGADA", color = if (sale.pendingAmount > 0) BSPOSTheme.colors.warning else BSPOSTheme.colors.success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptySalesState() {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aún no hay ventas en este período", fontWeight = FontWeight.Bold)
            Text("Las ventas registradas desde Terminal aparecerán aquí.", color = BSPOSTheme.colors.textSecondary)
        }
    }
}

private fun SalePaymentType.label(): String = when (this) {
    SalePaymentType.CASH -> "Efectivo"
    SalePaymentType.CREDIT -> "Crédito"
    SalePaymentType.CARD -> "Tarjeta"
    SalePaymentType.TRANSFER -> "Transferencia"
    SalePaymentType.MIXED -> "Mixto"
}

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)
