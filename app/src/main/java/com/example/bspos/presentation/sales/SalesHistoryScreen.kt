package com.example.bspos.presentation.sales

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
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
@OptIn(ExperimentalMaterial3Api::class)
fun SalesHistoryScreen(
    onReturns: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    printerViewModel: PosViewModel = hiltViewModel()
) {
    val sales by viewModel.sales.collectAsState()
    val saleItems by viewModel.saleItems.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val products by viewModel.products.collectAsState()
    val printers by printerViewModel.printers.collectAsState()
    val printerMessage by printerViewModel.printerMessage.collectAsState()
    val selectedSale by viewModel.selectedSale.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val context = LocalContext.current
    val currency = LocalCurrency.current
    var period by remember { mutableStateOf("Hoy") }
    var paymentFilter by remember { mutableStateOf("Todas") }
    var query by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var originFilter by remember { mutableStateOf("Todos") }
    var minAmount by remember { mutableStateOf("") }
    var maxAmount by remember { mutableStateOf("") }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showExportDialog by remember { mutableStateOf(false) }
    var exportAll by remember { mutableStateOf(false) }
    var exportContents by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportContents.toByteArray(Charsets.UTF_8)) }
                ?: error("No se pudo guardar el archivo.")
        }.onFailure { UiErrorBus.show("No se pudo exportar: ${it.message ?: "error"}") }
    }

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
                "Parciales" -> sale.paidAmount > 0 && sale.pendingAmount > 0
                else -> true
            }
        }
        .filter { sale -> originFilter == "Todos" || (originFilter == "Ruta" && sale.routeId != null) || (originFilter == "Terminal" && sale.routeId == null) }
        .filter { sale -> minAmount.isBlank() || sale.total >= MoneyUtils.parsePesosStringToCents(minAmount) }
        .filter { sale -> maxAmount.isBlank() || sale.total <= MoneyUtils.parsePesosStringToCents(maxAmount) }
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
                    Text("VENTAS", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Consulta tus ventas, cobros y resultados.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showExportDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Exportar ventas")
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
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
            item {
                OutlinedButton(onClick = { showFilters = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.FilterList, null); Spacer(Modifier.width(8.dp)); Text("Filtros" + if (originFilter != "Todos" || minAmount.isNotBlank() || maxAmount.isNotBlank()) " · activos" else "")
                }
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
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
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportar ventas") },
            text = {
                Column {
                    Text("CSV con las ventas y líneas disponibles en este dispositivo.", color = BSPOSTheme.colors.textSecondary)
                    Row(Modifier.fillMaxWidth().clickable { exportAll = false }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !exportAll, onClick = { exportAll = false })
                        Text("Lo que ves ahora · ${visibleSales.size} ventas")
                    }
                    Row(Modifier.fillMaxWidth().clickable { exportAll = true }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = exportAll, onClick = { exportAll = true })
                        Text("Todas las ventas · ${sales.count { it.status == SaleStatus.COMPLETED }} ventas")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val rows = if (exportAll) sales.filter { it.status == SaleStatus.COMPLETED }.sortedByDescending { it.date } else visibleSales
                    exportContents = buildSalesCsv(rows, customerNames, products.associateBy { it.id }, saleItems, currency)
                    showExportDialog = false
                    exportLauncher.launch("ventas-${LocalDate.now()}.csv")
                }) { Text("Guardar CSV") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancelar") } }
        )
    }
    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = filterSheetState) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Filtros de ventas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("Total", color = BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(minAmount, { minAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, Modifier.weight(1f), label = { Text("Desde") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(maxAmount, { maxAmount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } }, Modifier.weight(1f), label = { Text("Hasta") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                }
                Text("Pago", color = BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Todas", "Pagadas", "A crédito", "Parciales", "Anuladas").forEach { label ->
                        FilterChip(selected = paymentFilter == label, onClick = { paymentFilter = label }, label = { Text(label) })
                    }
                }
                Text("Origen", color = BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Terminal", "Ruta").forEach { label ->
                        FilterChip(selected = originFilter == label, onClick = { originFilter = label }, label = { Text(label) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { originFilter = "Todos"; paymentFilter = "Todas"; minAmount = ""; maxAmount = "" }, modifier = Modifier.weight(1f)) { Text("Limpiar") }
                    Button(onClick = { showFilters = false }, modifier = Modifier.weight(1f)) { Text("Ver ventas") }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
internal fun SalesSummary(sales: List<Sale>) {
    val total = sales.sumOf { it.total }
    val average = if (sales.isEmpty()) 0 else total / sales.size
    val credit = sales.sumOf { it.pendingAmount.coerceAtLeast(0L) }
    val creditRate = if (total <= 0) 0 else (credit * 100 / total).toInt()
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Metric("Ventas del período", sales.size.toString(), BSPOSTheme.colors.primary, Modifier.weight(1f))
                VerticalDivider(Modifier.height(88.dp), color = BSPOSTheme.colors.outline)
                Metric("Total vendido", money(total), BSPOSTheme.colors.success, Modifier.weight(1f))
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Metric("Promedio por venta", money(average.toLong()), BSPOSTheme.colors.primary, Modifier.weight(1f))
                VerticalDivider(Modifier.height(88.dp), color = BSPOSTheme.colors.outline)
                Metric("A crédito", "$creditRate%", BSPOSTheme.colors.warning, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(Locale("es")), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
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

internal fun buildSalesCsv(
    sales: List<Sale>,
    customers: Map<java.util.UUID, String>,
    products: Map<java.util.UUID, com.example.bspos.domain.model.Product>,
    items: List<com.example.bspos.domain.model.SaleItem>,
    currency: com.example.bspos.domain.model.CurrencyUnit
): String {
    fun cell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    fun format(cents: Long): String = MoneyUtils.formatCents(cents, currency)
    val output = StringBuilder(listOf("factura", "fecha", "cliente", "metodo", "estado", "total", "producto", "cantidad", "precio_unitario", "subtotal_linea").joinToString(",", transform = ::cell)).append("\r\n")
    sales.forEach { sale ->
        val lines = items.filter { it.saleId == sale.id }
        val fields = { item: com.example.bspos.domain.model.SaleItem? ->
            listOf(
                sale.invoiceNumber,
                sale.date.toString(),
                customers[sale.customerId].orEmpty(),
                sale.paymentType.label(),
                if (sale.pendingAmount > 0L) "Pendiente" else if (sale.status == SaleStatus.COMPLETED) "Pagada" else "Anulada",
                format(sale.total),
                item?.let { products[it.productId]?.name }.orEmpty(),
                item?.quantity?.toString().orEmpty(),
                item?.let { format(it.unitPrice) }.orEmpty(),
                item?.let { format(it.subtotal) }.orEmpty()
            )
        }
        (lines.ifEmpty { listOf(null) }).forEach { line ->
            output.append(fields(line).joinToString(",", transform = ::cell)).append("\r\n")
        }
    }
    return output.toString()
}
