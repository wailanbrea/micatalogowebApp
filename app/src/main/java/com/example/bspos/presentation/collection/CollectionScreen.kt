package com.example.bspos.presentation.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
    val mayCollect by viewModel.canCollect.collectAsState()
    if (!mayCollect) {
        Text("No tienes permiso vigente para registrar cobros.", Modifier.fillMaxSize().padding(20.dp))
        return
    }
    val customers by viewModel.customers.collectAsState()
    val sales by viewModel.sales.collectAsState()
    val customer by viewModel.customer.collectAsState()
    val collecting by viewModel.collecting.collectAsState()
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
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background).imePadding(),
        contentPadding = PaddingValues(16.dp),
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
                    Icon(Icons.Default.Person, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(customer?.fullName ?: "Seleccionar cliente", fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(customer?.let { "Saldo: ${money(it.balance)}" } ?: "Elige un cliente con saldo pendiente", color = BSPOSTheme.colors.textSecondary)
                    }
                    Text("Cambiar", modifier = Modifier.padding(start = 8.dp), color = BSPOSTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (tab == "Por cobrar") {
            item {
                CollectionPaymentMethods(method, onSelect = { method = it })
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
                            busy = collecting,
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
                        busy = collecting,
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
                            busy = collecting,
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
internal fun PendingSaleCard(
    sale: Sale,
    customer: Customer?,
    method: PaymentMethod,
    busy: Boolean,
    onCollect: (Long) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sale.invoiceNumber, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
                Text(customer?.fullName ?: "Cliente no asociado", fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (customer == null) BSPOSTheme.colors.warning else BSPOSTheme.colors.textPrimary)
                Text("Saldo pendiente", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
                Text(money(sale.pendingAmount), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                Text("Total factura: ${money(sale.total)} · ${method.label}",
                    color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            CollectionAmountForm(sale.id.toString(), sale.pendingAmount, busy, customer != null, onCollect)
        }
    }
}

@Composable
internal fun RemoteCustomerDebtCard(
    customer: Customer,
    method: PaymentMethod,
    busy: Boolean,
    onCollect: (Long) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(customer.fullName, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Saldo pendiente", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
                Text(money(customer.balance), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                Text(method.label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            CollectionAmountForm(customer.id.toString(), customer.balance, busy, true, onCollect)
        }
    }
}

/** A single mobile layout for local invoices and authoritative remote balances. */
@Composable
private fun CollectionAmountForm(identity: String, balance: Long, busy: Boolean, allowed: Boolean, onCollect: (Long) -> Unit) {
    var amount by remember(identity, balance) { mutableStateOf(MoneyUtils.formatCentsCompact(balance)) }
    val value = MoneyUtils.parsePesosStringToCents(amount)
    val valid = value > 0L && value <= balance
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Monto a cobrar") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            enabled = !busy,
            isError = value > balance,
            supportingText = if (value > balance) ({ Text("El monto no puede superar el saldo pendiente.") }) else null
        )
        Button(
            onClick = { if (!busy && allowed && valid) onCollect(value) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = !busy && allowed && valid
        ) { Text(if (busy) "Procesando…" else "Cobrar") }
    }
}

@Composable
internal fun CollectionPaymentMethods(selected: PaymentMethod, onSelect: (PaymentMethod) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Método de cobro", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
        PaymentMethod.entries.toList().chunked(2).forEach { methods ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { value ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        label = { Text(value.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    )
                }
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
