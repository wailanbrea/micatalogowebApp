package com.example.bspos.presentation.returning

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SaleItem
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.model.SaleStatus
import com.example.bspos.presentation.common.DialogScrollableColumn
import com.example.bspos.domain.usecase.CashRefundAmounts
import com.example.bspos.domain.usecase.RefundableLine
import java.util.Locale

@Composable
fun ReturnScreen(viewModel: ReturnViewModel = hiltViewModel()) {
    val sales by viewModel.sales.collectAsState()
    val items by viewModel.items.collectAsState()
    val products by viewModel.products.collectAsState()
    val message by viewModel.message.collectAsState()
    val returned by viewModel.returnedQuantities.collectAsState()
    var selected by remember { mutableStateOf<Sale?>(null) }
    var line by remember { mutableStateOf<SaleItem?>(null) }
    val names = products.associate { it.id to it.name }
    val returnedByItem = returned.associate { it.saleItemId to it.quantity }
    val selectedItems = items.filter { it.saleId == selected?.id }
    val charged = selected?.let { sale -> if (selectedItems.isEmpty()) emptyMap() else
        CashRefundAmounts.allocate(selectedItems.map { RefundableLine(it.id.toString(),it.quantity,it.subtotal) },sale.discount,sale.tax) }.orEmpty()
    val eligible = sales.filter { it.status == SaleStatus.COMPLETED && it.paymentType == SalePaymentType.CASH && it.pendingAmount == 0L && it.paidAmount == it.total }

    Column(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) IconButton({ selected = null; viewModel.select(null) }) { Icon(Icons.Default.ArrowBack, "Volver") }
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(BSPOSTheme.colors.errorLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.Replay, null, tint = BSPOSTheme.colors.error) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (selected == null) "Reembolsos de ventas de contado" else "Factura ${selected?.invoiceNumber}", color = BSPOSTheme.colors.textSecondary)
            }
        }
        Spacer(Modifier.size(16.dp))
        if (selected == null) {
            Text("Selecciona una factura liquidada", color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.size(8.dp))
            if (eligible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No hay ventas elegibles para reembolso", color = BSPOSTheme.colors.textSecondary) }
            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { items(eligible, key = { it.id }) { sale -> SaleCard(sale) { selected = sale; viewModel.select(sale) } } }
        } else {
            Text("Selecciona un producto y la cantidad a devolver.", color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.size(8.dp))
            if (items.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("La factura no contiene productos", color = BSPOSTheme.colors.textSecondary) }
            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { items(selectedItems, key = { it.id }) { item -> ReturnItemCard(names[item.productId] ?: "Producto", item) { if (item.quantity > (returnedByItem[item.id] ?: 0L)) line = item } } }
        }
    }

    line?.let { item -> ReturnLineDialog(item, charged[item.id.toString()] ?: 0L, returnedByItem[item.id] ?: 0L,
        { quantity, restock, reason -> viewModel.submit(item, quantity, restock, reason); line = null }, { line = null }) }
    message?.let { text -> AlertDialog(onDismissRequest = viewModel::consumeMessage, title = { Text("No se pudo completar") }, text = { Text(text) }, confirmButton = { TextButton(viewModel::consumeMessage) { Text("Cerrar") } }) }
}

@Composable
private fun SaleCard(sale: Sale, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.errorLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.Replay, null, tint = BSPOSTheme.colors.error) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(sale.invoiceNumber, fontWeight = FontWeight.ExtraBold); Text("Venta liquidada", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium) }
            Text(money(sale.total), fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary)
        }
    }
}

@Composable
private fun ReturnItemCard(name: String, item: SaleItem, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingBag, null, tint = BSPOSTheme.colors.primary) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${item.quantity} unidades · ${money(item.unitPrice)} c/u", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium) }
            Text("Devolver", color = BSPOSTheme.colors.error, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReturnLineDialog(item: SaleItem, charged: Long, returned: Long, onSave: (Long, Boolean, String) -> Unit, onDismiss: () -> Unit) {
    val remaining = item.quantity - returned
    var quantity by remember { mutableStateOf(remaining.toString()) }
    var restock by remember { mutableStateOf(true) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = quantity.toLongOrNull()?.let { runCatching { CashRefundAmounts.portion(charged,item.quantity,returned,it) }.getOrNull() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Confirmar devolución") }, text = {
        DialogScrollableColumn {
            Text("Máximo permitido: $remaining unidades · Ya devueltas: $returned", color = BSPOSTheme.colors.textSecondary)
            OutlinedTextField(quantity, { quantity = it; error = null }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = error != null)
            amount?.let { Text("Reembolso: ${money(it)}", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.primary) }
            Text("Importe original con descuentos e impuestos. La devolución remota espera la confirmación de la venta original.", style = MaterialTheme.typography.bodySmall)
            FilterChip(restock, { restock = !restock }, label = { Text(if (restock) "Reintegrar al inventario" else "No reintegrar al inventario") })
            OutlinedTextField(reason, { reason = it; error = null }, label = { Text("Motivo") }, minLines = 2, isError = error != null)
            error?.let { Text(it, color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton({ val value = quantity.toLongOrNull(); when { value == null || value <= 0L -> error = "Indica una cantidad válida"; value > remaining -> error = "La cantidad supera el saldo pendiente de devolución"; reason.isBlank() -> error = "El motivo es obligatorio"; else -> onSave(value, restock, reason.trim()) } }) { Text("Reembolsar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)
