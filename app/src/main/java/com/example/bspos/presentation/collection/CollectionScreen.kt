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
import com.example.bspos.presentation.common.BSPOSButton as Button
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
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
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
    val customersWithBalance = customers
        .filter { it.isActive && it.deletedAt == null && it.balance > 0L && (customer == null || it.id == customer?.id) }
        .sortedByDescending { it.balance }
    val localSaleCustomerIds = filteredSales.mapNotNull { it.customerId }.toSet()
    val remoteOnlyCustomers = customersWithBalance.filterNot { it.id in localSaleCustomerIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                Text("Cuentas por cobrar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            if (filteredSales.isEmpty()) {
                if (remoteOnlyCustomers.isEmpty()) {
                    item { CreditEmptyState(tab) }
                } else {
                    item { Text("Clientes con saldo pendiente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    items(remoteOnlyCustomers, key = { "customer-${it.id}" }) { debtor ->
                        RemoteCustomerDebtCard(
                            customer = debtor,
                            method = method,
                            onCollect = { amount -> viewModel.collectRemote(debtor, amount, method) }
                        )
                    }
                }
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
                if (remoteOnlyCustomers.isNotEmpty()) {
                    item { Text("Otros clientes con saldo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                    items(remoteOnlyCustomers, key = { "customer-${it.id}" }) { debtor ->
                        RemoteCustomerDebtCard(
                            customer = debtor,
                            method = method,
                            onCollect = { amount -> viewModel.collectRemote(debtor, amount, method) }
                        )
                    }
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
        CustomerPicker(
            customers = customers.filter { it.isActive && it.deletedAt == null },
            selectedId = customer?.id,
            onSelect = { selected ->
                viewModel.select(selected)
                choosing = false
            },
            onDismiss = { choosing = false }
        )
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
private fun RemoteCustomerDebtCard(
    customer: Customer,
    method: PaymentMethod,
    onCollect: (Long) -> Unit
) {
    var amount by remember(customer.id, customer.balance) {
        mutableStateOf(MoneyUtils.formatCentsCompact(customer.balance))
    }
    val value = MoneyUtils.parsePesosStringToCents(amount)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(customer.fullName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Saldo pendiente: ${money(customer.balance)}", color = BSPOSTheme.colors.warning)
                    Text("Cobro remoto · ${method.label}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Text(money(customer.balance), fontWeight = FontWeight.ExtraBold)
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
                    onClick = { if (value > 0L && value <= customer.balance) onCollect(value) },
                    enabled = value > 0L && value <= customer.balance
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
internal fun CustomerPicker(
    customers: List<Customer>,
    selectedId: java.util.UUID?,
    onSelect: (Customer?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = customers.filter { customer ->
        query.isBlank() || listOf(customer.fullName, customer.businessName, customer.phone.orEmpty())
            .any { it.contains(query.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar cliente") }
                )
                TextButton(
                    onClick = { onSelect(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            if (selectedId == null) "Todos los clientes" else "Quitar cliente seleccionado",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Mostrar las cuentas por cobrar de todos",
                            color = BSPOSTheme.colors.textSecondary
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        if (customers.isEmpty()) "No hay clientes activos registrados."
                        else "No hay clientes que coincidan con la búsqueda.",
                        color = BSPOSTheme.colors.textSecondary
                    )
                } else {
                    // BSPOSAlertDialog already provides the scroll container. A
                    // nested LazyColumn here made the picker unreliable after
                    // changing the selected customer or scrolling the sheet.
                    filtered.forEach { customer ->
                        TextButton(
                            onClick = { onSelect(customer) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    if (customer.id == selectedId) "✓ ${customer.fullName}" else customer.fullName,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Saldo: ${money(customer.balance)}",
                                    color = if (customer.balance > 0L) BSPOSTheme.colors.warning else BSPOSTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
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
