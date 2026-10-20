package com.example.bspos.presentation.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import com.example.bspos.presentation.common.BSPOSButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val history by viewModel.paymentHistory.collectAsState()
    val collecting by viewModel.collecting.collectAsState()
    val message by viewModel.message.collectAsState()
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var tab by remember { mutableStateOf("Por cobrar") }
    var query by remember(tab) { mutableStateOf("") }
    var period by remember { mutableStateOf("Hoy") }
    var historyMethod by remember { mutableStateOf("Todos") }
    var selectedReceipt by remember { mutableStateOf<CollectionPaymentHistoryItem?>(null) }
    val pendingSales = sales.filter { it.pendingAmount > 0 && it.customerId != null }
    val customersById = customers.associateBy { it.id }
    val debtorIds = (customers.filter { it.isActive && it.deletedAt == null && it.balance > 0L }.map { it.id } + pendingSales.mapNotNull { it.customerId }).toSet()
    val debtorCustomers = customers.filter { it.id in debtorIds }
        .sortedByDescending { it.balance }
    val dueByCustomer = debtorCustomers.associate { debtor ->
        debtor.id to maxOf(debtor.balance, pendingSales.filter { it.customerId == debtor.id }.sumOf { it.pendingAmount })
    }
    val visibleDebtors = debtorCustomers.filter { debtor ->
        query.isBlank() || listOf(debtor.fullName, debtor.businessName, debtor.taxId.orEmpty(), debtor.phone.orEmpty())
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    val today = LocalDate.now()
    val filteredHistory = history.filter { row ->
        val day = row.at.atZone(ZoneId.systemDefault()).toLocalDate()
        val periodMatches = when (period) {
            "Hoy" -> day == today
            "Esta semana" -> !day.isBefore(today.minusDays((today.dayOfWeek.value - 1).toLong()))
            "Este mes" -> day.year == today.year && day.month == today.month
            else -> true
        }
        periodMatches && (historyMethod == "Todos" || row.method == historyMethod) &&
            (query.isBlank() || listOf(row.customerName, row.customerTaxId.orEmpty(), row.receipt, row.reference.orEmpty(), row.notes.orEmpty())
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    val groupedHistory = filteredHistory.groupBy { it.at.atZone(ZoneId.systemDefault()).toLocalDate() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background).imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BSPOSTheme.colors.surface).padding(4.dp)) {
                listOf("Por cobrar", "Historial Pagados").forEach { option ->
                    val selected = tab == option
                    Surface(
                        onClick = { tab = option; query = "" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) BSPOSTheme.colors.background else androidx.compose.ui.graphics.Color.Transparent,
                        shadowElevation = if (selected) 1.dp else 0.dp
                    ) {
                        Text(option, Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                            color = if (selected) BSPOSTheme.colors.textPrimary else BSPOSTheme.colors.textSecondary,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (tab == "Por cobrar") {
            val dueTotal = dueByCustomer.values.sum()
            item {
                CollectionDueSummary(debtorCustomers.size, dueTotal)
            }
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Buscar cliente por nombre o RNC…") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Seleccionar cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("${visibleDebtors.size} pendientes", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (visibleDebtors.isEmpty()) {
                item { CreditEmptyState(tab) }
            } else {
                items(visibleDebtors, key = { "debtor-${it.id}" }) { debtor ->
                    val selected = customer?.id == debtor.id
                    DebtorSelectorCard(debtor, dueByCustomer[debtor.id] ?: debtor.balance, selected, onClick = {
                        viewModel.select(if (selected) null else debtor)
                    })
                    if (selected) {
                        CollectionPaymentMethods(method, onSelect = { method = it })
                        val invoices = pendingSales.filter { it.customerId == debtor.id }
                        if (invoices.isEmpty()) {
                            RemoteCustomerDebtCard(debtor, method, collecting) { amount -> viewModel.collectRemote(debtor, amount, method) }
                        } else {
                            invoices.forEach { sale ->
                                PendingSaleCard(sale, debtor, method, collecting) { amount ->
                                    viewModel.collect(sale.id, amount, method)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Buscar cliente, recibo o RNC…") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hoy", "Esta semana", "Este mes").forEach { label ->
                        FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label) })
                    }
                    listOf("Todos", "Efectivo", "Transferencia", "Tarjeta", "Cheque").forEach { label ->
                        FilterChip(selected = historyMethod == label, onClick = { historyMethod = label }, label = { Text(label) })
                    }
                }
            }
            item { CollectionHistorySummary(filteredHistory) }
            if (groupedHistory.isEmpty()) {
                item { CreditEmptyState("Historial Pagados") }
            } else {
                groupedHistory.toSortedMap(compareByDescending { it }).forEach { (day, rows) ->
                    item(key = "day-$day") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(day.format(DateTimeFormatter.ofPattern("EEEE · d 'de' MMMM", Locale.forLanguageTag("es"))).replaceFirstChar { it.uppercase() },
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${rows.size} cobros", color = BSPOSTheme.colors.textSecondary)
                        }
                    }
                    items(rows, key = { "payment-${it.id}" }) { payment ->
                        PaymentHistoryCard(payment) { selectedReceipt = payment }
                    }
                }
            }
        }
    }

    selectedReceipt?.let { receipt ->
        AlertDialog(onDismissRequest = { selectedReceipt = null }, title = { Text("Recibo ${receipt.receipt}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(receipt.customerName, fontWeight = FontWeight.Bold)
                    Text("${receipt.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a", Locale.forLanguageTag("es")))} · ${receipt.method}")
                    Text("Monto recibido", color = BSPOSTheme.colors.textSecondary)
                    Text(money(receipt.amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    receipt.reference?.takeIf(String::isNotBlank)?.let { Text("Referencia: $it") }
                    receipt.notes?.takeIf(String::isNotBlank)?.let { Text(it, color = BSPOSTheme.colors.textSecondary) }
                }
            }, confirmButton = { TextButton(onClick = { selectedReceipt = null }) { Text("Cerrar") } })
    }
    message?.let { text -> AlertDialog(onDismissRequest = viewModel::consumeMessage, title = { Text("Cobros") }, text = { Text(text) }, confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("Aceptar") } }) }
}

@Composable
private fun CollectionDueSummary(customers: Int, total: Long) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.secondaryNavy)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SALDOS POR COBRAR", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .78f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$customers clientes", color = BSPOSTheme.colors.success, fontWeight = FontWeight.Bold)
            }
            Text(money(total), color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun DebtorSelectorCard(customer: Customer, balance: Long, selected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) BSPOSTheme.colors.primary.copy(alpha = .07f) else BSPOSTheme.colors.surface),
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp,
            if (selected) BSPOSTheme.colors.primary else BSPOSTheme.colors.outline.copy(alpha = .35f))) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (selected) BSPOSTheme.colors.primary else BSPOSTheme.colors.background) {
                Text(customer.fullName.trim().take(2).uppercase(), Modifier.padding(11.dp),
                    color = if (selected) androidx.compose.ui.graphics.Color.White else BSPOSTheme.colors.textSecondary,
                    fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(customer.fullName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(customer.taxId?.takeIf(String::isNotBlank)?.let { "RNC: $it" } ?: customer.phone.orEmpty(),
                    style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("SALDO", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                Text(money(balance), color = if (balance > 0) BSPOSTheme.colors.error else BSPOSTheme.colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun CollectionHistorySummary(rows: List<CollectionPaymentHistoryItem>) {
    val total = rows.sumOf { it.amount }
    val cash = rows.filter { it.method == "Efectivo" }.sumOf { it.amount }
    val transfer = rows.filter { it.method == "Transferencia" }.sumOf { it.amount }
    val card = rows.filter { it.method == "Tarjeta" }.sumOf { it.amount }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.secondaryNavy)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("COBROS DEL PERÍODO", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .78f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${rows.size} registrados", color = BSPOSTheme.colors.success, fontWeight = FontWeight.Bold)
            }
            Text(money(total), color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            androidx.compose.material3.HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = .12f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Efectivo" to cash, "Transferencia" to transfer, "Tarjeta" to card).forEach { (label, amount) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(money(amount), style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PaymentHistoryCard(payment: CollectionPaymentHistoryItem, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(payment.customerName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val identity = payment.customerTaxId?.takeIf(String::isNotBlank)?.let { "RNC: $it · " }.orEmpty()
                    Text("$identity${payment.receipt} · ${payment.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es")))}",
                            style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                }
                Surface(shape = RoundedCornerShape(50), color = BSPOSTheme.colors.success.copy(alpha = .12f)) {
                    Text("✓ Cobrado", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = BSPOSTheme.colors.success, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("+${money(payment.amount)}", modifier = Modifier.align(Alignment.End), color = BSPOSTheme.colors.success,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            androidx.compose.material3.HorizontalDivider(color = BSPOSTheme.colors.outline.copy(alpha = .28f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(9.dp), color = BSPOSTheme.colors.background) {
                    Text(payment.method, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                Text("Ver recibo", color = BSPOSTheme.colors.primary, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.padding(start = 8.dp).size(19.dp))
            }
        }
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
    var amount by remember(identity, balance) { mutableStateOf(String.format(Locale.US, "%.2f", balance / 100.0)) }
    val value = MoneyUtils.parsePesosStringToCents(amount)
    val valid = value > 0L && value <= balance
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = amount,
            onValueChange = { raw -> val f = raw.replace(',', '.').filter { c -> c.isDigit() || c == '.' }; val p = f.split('.'); amount = if (p.size <= 2) f else p[0] + "." + p.drop(1).joinToString("") },
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
