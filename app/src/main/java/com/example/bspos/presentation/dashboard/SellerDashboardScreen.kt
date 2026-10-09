package com.example.bspos.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme

@Composable
internal fun SellerDashboardScreen(
    shopId: String?, businessName: String, showSales: Boolean, showProducts: Boolean,
    showCustomers: Boolean, onNewSale: () -> Unit, onProducts: () -> Unit,
    onCustomers: () -> Unit, onAllSales: () -> Unit, onSupport: () -> Unit,
    onHideSupport: () -> Unit, showSupport: Boolean, viewModel: DashboardViewModel
) {
    var period by rememberSaveable(shopId) { mutableStateOf("today") }
    val summary by viewModel.sellerSummary.collectAsState()
    val loading by viewModel.sellerSummaryLoading.collectAsState()
    val error by viewModel.sellerSummaryError.collectAsState()
    val currency = LocalCurrency.current
    fun money(cents: Long) = MoneyUtils.formatCents(cents, currency)
    LaunchedEffect(shopId, period, showSales) {
        if (showSales) viewModel.loadSellerSummary(shopId, period)
    }
    LazyColumn(
        Modifier.fillMaxSize().background(BSPOSTheme.colors.background),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.secondaryNavy)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(businessName, color = BSPOSTheme.colors.textOnNavy, style = MaterialTheme.typography.labelLarge)
                    Text("Tu espacio de ventas", color = BSPOSTheme.colors.textOnNavy, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (showSales) Button(onClick = onNewSale) { Text("Nueva venta") }
                }
            }
        }
        if (showSupport) item { SupportSummaryCard(onSupport = onSupport, onHide = onHideSupport) }
        if (showSales) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("today" to "Hoy", "week" to "Últimos 7", "month" to "Este mes").forEach { (key, label) ->
                        FilterChip(selected = period == key, onClick = { period = key }, label = { Text(label) })
                    }
                }
                TextButton(onClick = { viewModel.loadSellerSummary(shopId, period) }) { Text("Actualizar resumen") }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = BSPOSTheme.colors.error) } }
            summary?.let { data ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(data.periodLabel, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SellerMetric("Tus ventas", money(data.metrics.total), Modifier.weight(1f))
                            SellerMetric("Tus ganancias", money(data.metrics.commission), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SellerMetric("Ventas realizadas", data.metrics.count.toString(), Modifier.weight(1f))
                            SellerMetric("Ticket promedio", money(data.metrics.average), Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Evolución de tus ventas", fontWeight = FontWeight.Bold)
                            Text("Últimos 7 días · Solo tus ventas", style = MaterialTheme.typography.bodySmall)
                            val maximum = data.chart.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
                            data.chart.forEach { day ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(day.label, Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall)
                                    LinearProgressIndicator(progress = { day.total.toFloat() / maximum }, modifier = Modifier.weight(1f).padding(top = 5.dp))
                                    Text(money(day.total), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Comisión generada, no necesariamente pagada. Se conserva la comisión registrada en cada venta.", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                    Text("Tus ventas recientes", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
                }
                if (data.sales.isEmpty()) item { Text("Aún no hay ventas en este periodo.") }
                items(data.sales, key = { it.invoiceNumber }) { sale ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(sale.invoiceNumber, fontWeight = FontWeight.Bold)
                            Text("${sale.customer} · ${sale.date}", style = MaterialTheme.typography.bodySmall)
                            Text("Total: ${money(sale.total)}")
                            Text("Tu ganancia: ${money(sale.commission)}", color = BSPOSTheme.colors.success, fontWeight = FontWeight.Bold)
                            Text(when (sale.status) { "paid" -> "Pagada"; "partial" -> "Pago parcial"; "pending", "unpaid" -> "Pendiente"; else -> sale.status }, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (data.hasMoreSales) item { TextButton(onClick = onAllSales) { Text("Ver todas las ventas") } }
            }
        } else item { Text("Tu administrador no ha habilitado el acceso a Ventas.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showProducts) OutlinedButton(onClick = onProducts) { Text("Productos") }
                if (showCustomers) OutlinedButton(onClick = onCustomers) { Text("Clientes") }
            }
        }
    }
}

@Composable
private fun SellerMetric(title: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = BSPOSTheme.colors.textSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
