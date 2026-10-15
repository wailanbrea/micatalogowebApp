package com.example.bspos.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.bspos.presentation.common.BSPOSButton as Button
import com.example.bspos.presentation.common.BSPOSOutlinedButton as OutlinedButton
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme

/** Seller-only metrics and data, presented with the same visual system as Resumen. */
@Composable
internal fun SellerDashboardScreen(
    shopId: String?, businessName: String, showSales: Boolean, showProducts: Boolean,
    showCustomers: Boolean, onNewSale: () -> Unit, onProducts: () -> Unit,
    onCustomers: () -> Unit, onAllSales: () -> Unit, onSupport: () -> Unit,
    onHideSupport: () -> Unit, showSupport: Boolean, viewModel: DashboardViewModel?,
    previewSummary: com.example.bspos.data.micatalogo.dto.SellerSummaryDto? = null,
    previewLoading: Boolean = false,
    previewError: String? = null
) {
    var period by rememberSaveable(shopId) { mutableStateOf("today") }
    val summary = viewModel?.sellerSummary?.collectAsState()?.value ?: previewSummary
    val loading = viewModel?.sellerSummaryLoading?.collectAsState()?.value ?: previewLoading
    val error = viewModel?.sellerSummaryError?.collectAsState()?.value ?: previewError
    val currency = LocalCurrency.current
    fun money(cents: Long) = MoneyUtils.formatCents(cents, currency)

    LaunchedEffect(shopId, period, showSales) {
        if (showSales) viewModel?.loadSellerSummary(shopId, period)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(BSPOSTheme.colors.background).testTag("seller-dashboard"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SellerMonoLabel("RESUMEN")
                Text("Buenas, ${businessName.ifBlank { "tu negocio" }}", color = ResumenInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Así van tus ventas hoy.", color = ResumenMuted, fontSize = 16.sp)
                if (showSales) {
                    Spacer(Modifier.height(4.dp))
                    Button(onNewSale, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Nueva venta")
                    }
                }
            }
        }

        if (showSupport) item { SupportSummaryCard(onSupport = onSupport, onHide = onHideSupport) }

        if (showSales) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SellerLine),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("today" to "Hoy", "month" to "Este mes", "week" to "Últimos 7").forEach { (key, label) ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                onClick = { period = key },
                                shape = RoundedCornerShape(6.dp),
                                color = if (period == key) ResumenAccent else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    label,
                                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                    color = if (period == key) MaterialTheme.colorScheme.onPrimary else ResumenMuted,
                                    fontSize = 13.sp,
                                    fontWeight = if (period == key) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { viewModel?.loadSellerSummary(shopId, period) }) { Text("Actualizar resumen") }
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = BSPOSTheme.colors.error) } }

            summary?.let { data ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, SellerLine)
                    ) {
                        Column {
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                SellerMetric("TUS VENTAS", money(data.metrics.total), data.periodLabel, Modifier.weight(1f))
                                VerticalDivider(color = SellerLine)
                                SellerMetric("TU COMISIÓN", money(data.metrics.commission), "Generada en el periodo", Modifier.weight(1f))
                            }
                            HorizontalDivider(color = SellerLine)
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                SellerMetric("VENTAS", data.metrics.count.toString(), "Operaciones registradas", Modifier.weight(1f))
                                VerticalDivider(color = SellerLine)
                                SellerMetric("TICKET PROMEDIO", money(data.metrics.average), "Por venta", Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, SellerLine)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SellerMonoLabel("ACTIVIDAD")
                            Text("Evolución de tus ventas", color = ResumenInk, fontWeight = FontWeight.Bold)
                            if (data.chart.all { it.total <= 0L }) {
                                Text("Aún no hay ventas en este periodo.", color = ResumenMuted, fontSize = 13.sp)
                            } else {
                                val maximum = data.chart.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
                                data.chart.forEach { day ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(day.label, Modifier.width(42.dp), color = ResumenMuted, fontSize = 12.sp)
                                        LinearProgressIndicator(
                                            progress = { day.total.toFloat() / maximum },
                                            modifier = Modifier.weight(1f).padding(top = 5.dp),
                                            color = ResumenAccent,
                                            trackColor = ResumenAccent.copy(alpha = .12f)
                                        )
                                        Text(money(day.total), color = ResumenInk, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        SellerMonoLabel("MOVIMIENTOS")
                        Text("Tus ventas recientes", Modifier.padding(top = 5.dp), color = ResumenInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (data.sales.isEmpty()) item { Text("Aún no hay ventas en este periodo.", color = ResumenMuted) }
                items(data.sales, key = { it.invoiceNumber }) { sale ->
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, SellerLine)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sale.invoiceNumber, color = ResumenInk, fontWeight = FontWeight.Bold)
                                Text(sellerStatusLabel(sale.status), color = sellerStatusColor(sale.status), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("${sale.customer} · ${sale.date}", color = ResumenMuted, fontSize = 12.sp)
                            HorizontalDivider(color = SellerLine)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", color = ResumenMuted, fontSize = 13.sp)
                                Text(money(sale.total), color = ResumenInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tu comisión", color = ResumenMuted, fontSize = 13.sp)
                                Text(money(sale.commission), color = ResumenAccent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (data.hasMoreSales) item { TextButton(onClick = onAllSales) { Text("Ver todas las ventas") } }
            }
        } else {
            item {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, SellerLine)) {
                    Text("Tu administrador no ha habilitado el acceso a Ventas.", Modifier.padding(16.dp), color = ResumenMuted)
                }
            }
        }

        if (showProducts || showCustomers) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SellerMonoLabel("ACCESOS RÁPIDOS")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showProducts) OutlinedButton(onProducts, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Productos") }
                    if (showCustomers) OutlinedButton(onCustomers, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("Clientes") }
                }
            }
        }
    }
}

@Composable
private fun SellerMetric(label: String, value: String, helper: String, modifier: Modifier) {
    Column(modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        SellerMonoLabel(label)
        Text(value, color = ResumenInk, fontFamily = FontFamily.Monospace, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(helper, color = ResumenMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SellerMonoLabel(text: String) {
    Text(text, color = ResumenMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.5.sp)
}

private val SellerLine = androidx.compose.ui.graphics.Color(0xFFE2E8F0)

private fun sellerStatusLabel(status: String): String = when (status.lowercase()) {
    "paid" -> "Pagada"
    "partial" -> "Pago parcial"
    "pending", "unpaid" -> "Pendiente"
    "cancelled", "void", "anulado" -> "Anulada"
    else -> status.replaceFirstChar { it.uppercase() }
}

@Composable
private fun sellerStatusColor(status: String): androidx.compose.ui.graphics.Color = when (status.lowercase()) {
    "paid" -> BSPOSTheme.colors.success
    "partial", "pending", "unpaid" -> BSPOSTheme.colors.warning
    "cancelled", "void", "anulado" -> BSPOSTheme.colors.error
    else -> ResumenMuted
}
