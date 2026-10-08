package com.example.bspos.presentation.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.PaymentMethod
import com.example.bspos.domain.model.Sale
import com.example.bspos.presentation.common.UiErrorBus
import java.util.Locale

@Composable
fun CollectionScreen(viewModel: CollectionViewModel = hiltViewModel()) {
    val customers by viewModel.customers.collectAsState()
    val sales by viewModel.sales.collectAsState()
    val customer by viewModel.customer.collectAsState()
    var choosing by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var tab by remember { mutableStateOf("Por cobrar") }
    val filteredSales = sales
        .filter { customer == null || it.customerId == customer?.id }
        .filter { sale -> if (tab == "Por cobrar") sale.pendingAmount > 0 else sale.pendingAmount <= 0 }
    val customersById = customers.associateBy { it.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge()
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Consulta quién te debe y registra sus abonos.", color = BSPOSTheme.colors.textSecondary)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Por cobrar", "Pagados").forEach { option ->
                    FilterChip(
                        selected = tab == option,
                        onClick = { tab = option },
                        label = { Text(option, fontWeight = FontWeight.Bold) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (tab == option) BSPOSTheme.colors.secondaryNavy else BSPOSTheme.colors.surface,
                            selectedLabelColor = if (tab == option) androidx.compose.ui.graphics.Color.White else BSPOSTheme.colors.textPrimary,
                            containerColor = BSPOSTheme.colors.surface
                        )
                    )
                }
            }
        }
        item {
            Card(
                onClick = { choosing = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = BSPOSTheme.colors.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(customer?.businessName ?: "Seleccionar cliente", fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(customer?.let { "Saldo: ${money(it.balance)}" } ?: "Elige un cliente con saldo pendiente", color = BSPOSTheme.colors.textSecondary)
                    }
                    Text("Cambiar", color = BSPOSTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tab == "Por cobrar") {
            item {
                Text("Método de cobro", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(3.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PaymentMethod.entries.toList()) { value ->
                        FilterChip(selected = method == value, onClick = { method = value }, label = { Text(value.label) })
                    }
                }
            }
            item {
                Text("Facturas pendientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            if (filteredSales.isEmpty()) {
                item { CreditEmptyState(tab) }
            } else {
                items(filteredSales, key = { it.id }) { sale ->
                    val saleCustomer = sale.customerId?.let(customersById::get)
                    PendingSaleCard(
                        sale = sale,
                        customer = saleCustomer,
                        method = method,
                        onCollect = { amount ->
                            if (saleCustomer == null) {
                                UiErrorBus.show("Esta venta no tiene un cliente asociado para registrar el abono.")
                            } else {
                                viewModel.select(saleCustomer)
                                viewModel.collect(sale.id, amount, method)
                            }
                        }
                    )
                }
            }
        } else {
            item {
                Text("Ventas pagadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            if (filteredSales.isEmpty()) {
                item { CreditEmptyState(tab) }
            } else {
                items(filteredSales, key = { it.id }) { sale ->
                    PaidSaleCard(sale, customer?.businessName ?: "Cliente")
                }
            }
        }
    }

    if (choosing) {
        CustomerPicker(customers.filter { it.isActive && it.deletedAt == null }, { viewModel.select(it); choosing = false }, { choosing = false })
    }
}

@Composable
private fun PendingSaleCard(
    sale: Sale,
    customer: Customer?,
    method: PaymentMethod,
    onCollect: (Long) -> Unit
) {
    var amount by remember(sale.id, sale.pendingAmount) { mutableStateOf(MoneyUtils.formatCentsCompact(sale.pendingAmount)) }
    val value = MoneyUtils.parsePesosStringToCents(amount)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sale.invoiceNumber, fontWeight = FontWeight.ExtraBold)
                    Text(customer?.businessName ?: "Cliente no asociado", color = if (customer == null) BSPOSTheme.colors.warning else BSPOSTheme.colors.textSecondary)
                    Text("Pendiente: ${money(sale.pendingAmount)}", color = BSPOSTheme.colors.textSecondary)
                }
                Text(money(sale.total), fontWeight = FontWeight.ExtraBold)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                    modifier = Modifier.weight(1f),
                    label = { Text("Monto a cobrar") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Button(
                    onClick = { if (value > 0L && value <= sale.pendingAmount) onCollect(value) },
                    enabled = customer != null && value > 0L && value <= sale.pendingAmount
                ) { Text("Cobrar") }
            }
        }
    }
}

@Composable
private fun PaidSaleCard(sale: Sale, customerName: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountBalanceWallet, null, tint = BSPOSTheme.colors.success)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sale.invoiceNumber, fontWeight = FontWeight.ExtraBold)
                Text(customerName, color = BSPOSTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(money(sale.total), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun CreditEmptyState(tab: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AccountBalanceWallet, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(42.dp))
            Text(if (tab == "Por cobrar") "Nadie te debe" else "No hay pagos registrados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                if (tab == "Por cobrar") "Cuando vendas a crédito en Terminal, aquí verás quién te debe y a quién cobrar."
                else "Las ventas pagadas aparecerán aquí para consultar tu historial.",
                color = BSPOSTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun CustomerPicker(customers: List<Customer>, onSelect: (Customer) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar cliente") },
        text = {
            LazyColumn {
                items(customers, key = { it.id }) { customer ->
                    TextButton(onClick = { onSelect(customer) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(customer.businessName, fontWeight = FontWeight.Bold)
                            Text("Saldo: ${money(customer.balance)}", color = BSPOSTheme.colors.textSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun IconBadge() {
    androidx.compose.foundation.layout.Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(BSPOSTheme.colors.successLight),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Default.AccountBalanceWallet, null, tint = BSPOSTheme.colors.success) }
}

private val PaymentMethod.label: String
    get() = when (this) {
        PaymentMethod.CASH -> "Efectivo"
        PaymentMethod.TRANSFER -> "Transferencia"
        PaymentMethod.CHECK -> "Cheque"
        PaymentMethod.CARD -> "Tarjeta"
    }

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)
