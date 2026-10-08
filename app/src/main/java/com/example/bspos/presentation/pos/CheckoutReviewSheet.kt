package com.example.bspos.presentation.pos

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Customer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal enum class CheckoutReviewMethod(val title: String, val subtitle: String) {
    CASH("Efectivo", "BILLETES"), TRANSFER("Transferencia", "BANCO"),
    CARD("Tarjeta", "DÉBITO · CRÉDITO"), CREDIT("A crédito", "CUENTA POR COBRAR")
}

private fun amountText(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString()
private fun amountCents(text: String) = MoneyUtils.parseDecimalToCents(text) ?: 0L
private fun numericAmount(text: String) = text.filter { it.isDigit() || it == '.' || it == ',' }.take(18)

/** One expanded sheet: only the body scrolls; the title and confirmation remain visible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckoutReviewSheet(
    cart: List<PosCartLine>, subtotal: Long, discount: Long, customer: Customer?,
    method: CheckoutReviewMethod, wholesaleMode: Boolean, saleDate: LocalDate,
    quantities: Map<UUID, Long>, creditEnabled: Boolean, isProcessing: Boolean,
    onDismiss: () -> Unit, onCustomer: () -> Unit, onNewCustomer: () -> Unit,
    onMethodChange: (CheckoutReviewMethod) -> Unit, onMixed: () -> Unit,
    onQuantity: (UUID, Long) -> Unit, onUnitPrice: (UUID, Long) -> Unit,
    onClear: () -> Unit, onDiscount: (Long) -> Unit, onDate: (LocalDate) -> Unit,
    onConfirm: (String?, String?, String?) -> Unit,
    showCosts: Boolean = false
) {
    val total = subtotal - discount.coerceIn(0L, subtotal)
    val context = LocalContext.current
    val currency = LocalCurrency.current
    fun money(cents: Long) = MoneyUtils.formatCents(cents, currency)
    var received by remember(total) { mutableStateOf(amountText(total)) }
    var notes by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var showNotes by remember { mutableStateOf(false) }
    var discountText by remember(discount) { mutableStateOf(amountText(discount)) }
    var showClear by remember { mutableStateOf(false) }
    var editingPrice by remember { mutableStateOf<PosCartLine?>(null) }
    var priceText by remember { mutableStateOf("") }
    var showServerDateInfo by remember { mutableStateOf(false) }
    val receivedCents = amountCents(received)
    val valid = cart.isNotEmpty() && !isProcessing && (method != CheckoutReviewMethod.CASH || (MoneyUtils.parseDecimalToCents(received) != null && receivedCents >= total)) &&
        (method != CheckoutReviewMethod.CREDIT || customer != null)
    val knownCosts = cart.all { it.product.averageCost > 0L }
    val estimatedMargin = if (knownCosts) total - cart.sumOf { Math.multiplyExact(it.quantity, it.product.averageCost) } else null

    ModalBottomSheet(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BSPOSTheme.colors.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Surface(Modifier.padding(top = 10.dp, bottom = 6.dp).width(28.dp).height(4.dp),
                shape = RoundedCornerShape(4.dp), color = BSPOSTheme.colors.outline) {}
        }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cobrar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(50), color = BSPOSTheme.colors.primaryLight) {
                        Text(if (wholesaleMode) "MAYOREO" else "CONTADO", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp, letterSpacing = 1.sp, color = BSPOSTheme.colors.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !isProcessing, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PauseCircleOutline, "Volver a Terminal conservando el carrito", Modifier.size(19.dp))
                    }
                    IconButton(onClick = onDismiss, enabled = !isProcessing, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Cerrar cobro", Modifier.size(19.dp))
                    }
                }
                Text(money(total), fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("checkout-total"))
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("checkout-body")
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${cart.sumOf { it.quantity }} artículos", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showClear = true }, enabled = !isProcessing && cart.isNotEmpty(), contentPadding = PaddingValues(0.dp)) { Text("Vaciar", fontSize = 13.sp) }
                }
                Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline), color = BSPOSTheme.colors.surface) {
                    Column {
                        cart.forEachIndexed { index, line ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.product.name, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(Modifier.clickable(enabled = !isProcessing) { editingPrice = line; priceText = amountText(line.unitPrice) }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${money(line.unitPrice)} c/u", fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace, color = BSPOSTheme.colors.textSecondary)
                                        Icon(Icons.Default.Edit, "Editar precio de ${line.product.name}", Modifier.padding(start = 3.dp).size(12.dp), tint = BSPOSTheme.colors.textSecondary)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedIconButton(onClick = { onQuantity(line.product.id, line.quantity - 1) }, enabled = !isProcessing,
                                        modifier = Modifier.size(30.dp), shape = RoundedCornerShape(9.dp)) { Icon(Icons.Default.Remove, "Disminuir ${line.product.name}", Modifier.size(16.dp)) }
                                    Text(line.quantity.toString(), Modifier.widthIn(min = 28.dp).wrapContentWidth(Alignment.CenterHorizontally), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    val canAdd = line.product.remoteSaleUnit == "service" || line.quantity < (quantities[line.product.id] ?: 0L)
                                    OutlinedIconButton(onClick = { onQuantity(line.product.id, line.quantity + 1) }, enabled = !isProcessing && canAdd,
                                        modifier = Modifier.size(30.dp), shape = RoundedCornerShape(9.dp)) { Icon(Icons.Default.Add, "Aumentar ${line.product.name}", Modifier.size(16.dp)) }
                                }
                                Text(money(Math.multiplyExact(line.quantity, line.unitPrice)), Modifier.widthIn(min = 80.dp).padding(start = 8.dp),
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            if (index != cart.lastIndex) HorizontalDivider(color = BSPOSTheme.colors.outline)
                        }
                    }
                }
                TextButton(onClick = { showNotes = true }, contentPadding = PaddingValues(0.dp), enabled = !isProcessing) {
                    Text(if (discount > 0L || notes.isNotBlank()) "Descuento o nota · editar" else "Descuento o nota", fontSize = 13.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    CheckoutValueRow("Subtotal", money(subtotal))
                    if (discount > 0) CheckoutValueRow("Descuento", "− ${money(discount.coerceAtMost(subtotal))}")
                    if (showCosts) CheckoutValueRow("Margen estimado", estimatedMargin?.let { "${money(it)} · ${if (total > 0L) (it.toDouble() / total * 100).toInt() else 0}%" } ?: "Costo pendiente")
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckoutSectionLabel("CLIENTE")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.weight(1f).height(44.dp).clickable(enabled = !isProcessing, onClick = onCustomer),
                            shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BSPOSTheme.colors.outline), color = BSPOSTheme.colors.surface) {
                            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(30.dp), shape = RoundedCornerShape(8.dp), color = BSPOSTheme.colors.surfaceVariant) {
                                    Text(customer?.fullName?.take(1) ?: "C", Modifier.wrapContentSize(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(customer?.fullName ?: "Cliente general", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (customer == null) "VENTA DE MOSTRADOR" else "CLIENTE SELECCIONADO", fontSize = 9.sp, letterSpacing = 1.sp, color = BSPOSTheme.colors.textSecondary)
                                }
                                Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                            }
                        }
                        OutlinedIconButton(onClick = onNewCustomer, enabled = !isProcessing, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Add, "Nuevo cliente", Modifier.size(19.dp)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CheckoutSectionLabel("FECHA")
                    Text(if (saleDate == LocalDate.now()) "Hoy" else saleDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), Modifier.padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(saleDate.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale("es", "DO"))), Modifier.weight(1f).padding(start = 8.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = BSPOSTheme.colors.textSecondary)
                    TextButton(onClick = {
                        if (cart.any { !it.product.remoteProductId.isNullOrBlank() }) showServerDateInfo = true
                        else DatePickerDialog(context, { _, year, month, day -> onDate(LocalDate.of(year, month + 1, day)) }, saleDate.year, saleDate.monthValue - 1, saleDate.dayOfMonth)
                            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }, enabled = !isProcessing, contentPadding = PaddingValues(0.dp)) { Text("Cambiar", fontSize = 13.sp) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckoutSectionLabel("¿CÓMO PAGA?")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckoutPaymentTile(CheckoutReviewMethod.CASH, method, !isProcessing, onMethodChange, Modifier.weight(1f))
                        CheckoutPaymentTile(CheckoutReviewMethod.TRANSFER, method, !isProcessing, onMethodChange, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckoutPaymentTile(CheckoutReviewMethod.CARD, method, !isProcessing, onMethodChange, Modifier.weight(1f))
                        if (creditEnabled) CheckoutPaymentTile(CheckoutReviewMethod.CREDIT, method, !isProcessing, onMethodChange, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    }
                    if (method == CheckoutReviewMethod.CASH) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.surfaceVariant) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Recibido", fontSize = 12.sp)
                                androidx.compose.foundation.text.BasicTextField(received, { received = numericAmount(it) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).testTag("checkout-received"), singleLine = true,
                                    enabled = !isProcessing, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                            }
                        }
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.surfaceVariant) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Devolver", fontSize = 12.sp)
                                Text(money((receivedCents - total).coerceAtLeast(0L)), Modifier.padding(top = 14.dp), fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (method == CheckoutReviewMethod.CASH && receivedCents < total) Text("El recibido debe cubrir el total.", color = BSPOSTheme.colors.error, fontSize = 12.sp)
                    if (method == CheckoutReviewMethod.TRANSFER) OutlinedTextField(reference, { reference = it }, label = { Text("Referencia bancaria (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (method == CheckoutReviewMethod.CREDIT) OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Vencimiento (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = onMixed, enabled = !isProcessing, contentPadding = PaddingValues(0.dp)) { Text("Dividir entre métodos de pago", fontSize = 12.sp) }
                }
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Surface(color = BSPOSTheme.colors.surface) {
                Button(onClick = { onConfirm(notes.trim().ifBlank { null }, reference.trim().ifBlank { null }, dueDate.trim().ifBlank { null }) },
                    enabled = valid, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).height(48.dp).testTag("checkout-confirm"),
                    shape = RoundedCornerShape(10.dp)) {
                    if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Confirmar · ${money(total)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showNotes) AlertDialog(onDismissRequest = { showNotes = false }, title = { Text("Descuento o nota") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(discountText, { discountText = numericAmount(it) }, label = { Text("Descuento (${currency.symbol})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = amountCents(discountText) > subtotal)
            OutlinedTextField(notes, { notes = it }, label = { Text("Nota en el recibo") }, maxLines = 3)
        }
    }, confirmButton = { TextButton(onClick = { onDiscount(amountCents(discountText)); showNotes = false }, enabled = MoneyUtils.parseDecimalToCents(discountText) != null && amountCents(discountText) <= subtotal) { Text("Aplicar") } }, dismissButton = { TextButton(onClick = { showNotes = false }) { Text("Cerrar") } })
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text("¿Vaciar el carrito?") }, text = { Text("Se quitarán los artículos de esta venta.") },
        confirmButton = { TextButton(onClick = { onClear(); showClear = false }) { Text("Vaciar") } }, dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancelar") } })
    editingPrice?.let { line -> AlertDialog(onDismissRequest = { editingPrice = null }, title = { Text("Precio por unidad") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(priceText, { priceText = numericAmount(it) }, label = { Text(line.product.name) }, readOnly = !line.product.remoteProductId.isNullOrBlank(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (!line.product.remoteProductId.isNullOrBlank()) Text("Esta venta usa el precio vigente del catálogo. Para rebajarlo en esta venta, utiliza Descuento o nota.")
        }
    }, confirmButton = { TextButton(onClick = { if (line.product.remoteProductId.isNullOrBlank()) onUnitPrice(line.product.id, amountCents(priceText)); editingPrice = null }, enabled = MoneyUtils.parseDecimalToCents(priceText) != null) { Text(if (line.product.remoteProductId.isNullOrBlank()) "Aplicar" else "Entendido") } }, dismissButton = { TextButton(onClick = { editingPrice = null }) { Text("Cancelar") } }) }
    if (showServerDateInfo) AlertDialog(onDismissRequest = { showServerDateInfo = false }, title = { Text("Fecha de la venta") }, text = {
        Text("Las ventas sincronizadas usan la fecha de registro del servidor. La API actual no permite registrar ventas con una fecha anterior.")
    }, confirmButton = { TextButton(onClick = { showServerDateInfo = false }) { Text("Entendido") } })
}

@Composable
private fun CheckoutSectionLabel(text: String) = Text(text, fontSize = 10.sp, letterSpacing = 1.6.sp, color = BSPOSTheme.colors.textSecondary)

@Composable
private fun CheckoutValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CheckoutPaymentTile(option: CheckoutReviewMethod, selected: CheckoutReviewMethod, enabled: Boolean,
    onClick: (CheckoutReviewMethod) -> Unit, modifier: Modifier) {
    Surface(modifier.height(54.dp).clickable(enabled = enabled) { onClick(option) }, shape = RoundedCornerShape(15.dp),
        color = if (option == selected) BSPOSTheme.colors.primary else BSPOSTheme.colors.surface,
        border = BorderStroke(1.dp, if (option == selected) BSPOSTheme.colors.primary else BSPOSTheme.colors.outline)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(option.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (option == selected) Color.White else BSPOSTheme.colors.textPrimary)
            Text(option.subtitle, fontSize = 9.sp, letterSpacing = 1.sp, color = if (option == selected) Color.White.copy(alpha = .8f) else BSPOSTheme.colors.textSecondary)
        }
    }
}
