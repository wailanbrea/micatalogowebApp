package com.example.bspos.presentation.purchase

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.PurchaseDocumentDto
import com.example.bspos.data.micatalogo.dto.PurchaseProductDto
import com.example.bspos.data.micatalogo.dto.PurchaseSupplierDto

@Composable
fun PurchaseModuleScreen(
    feature: String,
    modifier: Modifier = Modifier,
    viewModel: PurchaseModuleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(feature) { viewModel.load(feature) }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading && state.workspace == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BSPOSTheme.colors.primary)
            state.error != null && state.workspace == null -> PurchaseError(state.error!!, onRetry = { viewModel.load(feature) })
            state.workspace != null -> PurchaseContent(feature, state.workspace!!, state, viewModel)
        }
    }
}

@Composable
private fun PurchaseContent(
    feature: String,
    workspace: com.example.bspos.data.micatalogo.dto.PurchaseWorkspaceDto,
    state: PurchaseModuleUiState,
    viewModel: PurchaseModuleViewModel
) {
    var showForm by remember(feature) { mutableStateOf(false) }
    var documentNumber by remember(feature) { mutableStateOf("") }
    var debtMode by remember(feature) { mutableStateOf(false) }
    var debtAmount by remember(feature) { mutableStateOf("") }
    var invoiceDate by remember(feature) { mutableStateOf("") }
    var dueAt by remember(feature) { mutableStateOf("") }
    var notes by remember(feature) { mutableStateOf("") }
    var supplier by remember(feature) { mutableStateOf<PurchaseSupplierDto?>(null) }
    var supplierMenu by remember(feature) { mutableStateOf(false) }
    var parentLoad by remember(feature) { mutableStateOf<PurchaseDocumentDto?>(null) }
    var parentLoadMenu by remember(feature) { mutableStateOf(false) }
    var currencyMenu by remember(feature) { mutableStateOf(false) }
    var carrierMenu by remember(feature) { mutableStateOf(false) }
    var currency by remember(feature) { mutableStateOf(if (feature == "loads") "USD" else "DOP") }
    var exchangeRate by remember(feature) { mutableStateOf("") }
    var carrier by remember(feature) { mutableStateOf("") }
    var trackingNumber by remember(feature) { mutableStateOf("") }
    var expectedAt by remember(feature) { mutableStateOf("") }
    var shippingPounds by remember(feature) { mutableStateOf("") }
    var freightAmount by remember(feature) { mutableStateOf("") }
    var customsAmount by remember(feature) { mutableStateOf("") }
    var mode by remember(feature) { mutableStateOf("draft") }
    var lines by remember(feature) { mutableStateOf(listOf(PurchaseLineDraft())) }
    val invoiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::previewInvoice)
    }
    if (feature == "suppliers") {
        SupplierManagerContent(workspace, state, viewModel)
        return
    }
    val type = when {
        feature == "purchase_invoices" && debtMode -> "supplier_debt"
        feature == "purchase_invoices" -> "purchase_invoice"
        feature == "loads" -> "load"
        else -> "container"
    }
    val documents = workspace.documents.filter { document ->
        when (feature) {
        "purchase_invoices" -> document.type == "purchase_invoice" || document.type == "supplier_debt"
            "loads" -> document.type == "load"
            else -> document.type == "container"
        }
    }
    val validLines = lines.isNotEmpty() && lines.all { it.productId.isNotBlank() && (it.quantity.toIntOrNull() ?: 0) > 0 && it.unitCost.replace(',', '.').toDoubleOrNull()?.let { cost -> cost >= 0 } == true }
    val validDebt = supplier != null && debtAmount.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true && invoiceDate.length == 10 && (dueAt.isBlank() || dueAt.length == 10)
    val canSave = (debtMode || documentNumber.isNotBlank()) &&
        (if (debtMode) validDebt else (feature == "loads" || validLines)) &&
        (exchangeRate.isBlank() || exchangeRate.replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true) &&
        (shippingPounds.isBlank() || shippingPounds.replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true) &&
        (freightAmount.isBlank() || freightAmount.replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true) &&
        (customsAmount.isBlank() || customsAmount.replace(',', '.').toDoubleOrNull()?.let { it >= 0 } == true)
    val loadDocuments = workspace.documents.filter { it.type == "load" }
    val currencyOptions = listOf(
        "DOP" to "DOP · Peso dominicano",
        "USD" to "USD · US Dollar",
        "EUR" to "EUR · Euro",
        "MXN" to "MXN · Peso mexicano",
        "COP" to "COP · Peso colombiano"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (feature == "loads") "Agrupa contenedores de un mismo envío y conserva el costo real puesto en inventario."
                        else "Cada compra conserva su costo y puede recibirse una sola vez.",
                        color = BSPOSTheme.colors.textSecondary
                    )
                }
                IconButton(onClick = { viewModel.load(feature) }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = BSPOSTheme.colors.primary) }
            }
        }
        state.message?.let { message -> item { Notice(message, success = true) } }
        state.error?.let { error -> item { Notice(error, success = false) } }
        item {
            Button(onClick = { showForm = !showForm }, modifier = Modifier.fillMaxWidth()) {
                Icon(if (showForm) Icons.Default.DeleteOutline else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (showForm) "Cerrar nueva compra" else when (feature) {
                    "loads" -> "Nueva carga"
                    "purchase_invoices" -> "Nueva factura"
                    else -> "Nuevo contenedor"
                })
            }
        }
        if (feature == "purchase_invoices" && showForm) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { debtMode = false }, modifier = Modifier.weight(1f)) { Text("Nueva factura") }
                    OutlinedButton(onClick = { debtMode = true }, modifier = Modifier.weight(1f)) { Text("Registrar deuda") }
                }
            }
        }
        if (showForm) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Preparar compra", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        if (!debtMode) OutlinedTextField(
                            documentNumber,
                            { documentNumber = it },
                            Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(if (feature == "loads") "Nombre de la carga" else "No. documento") },
                            supportingText = if (feature == "loads") {{ Text("Ej.: Miami septiembre o Pedido Dewan") }} else null
                        )
                        if (debtMode) {
                            Text("Deuda de suplidor", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                            OutlinedTextField(debtAmount, { debtAmount = purchaseDecimalInput(it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Monto que debes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(invoiceDate, { invoiceDate = it.filter { char -> char.isDigit() || char == '-' }.take(10) }, Modifier.weight(1f), singleLine = true, label = { Text("Fecha de la factura") }, supportingText = { Text("AAAA-MM-DD") })
                                OutlinedTextField(dueAt, { dueAt = it.filter { char -> char.isDigit() || char == '-' }.take(10) }, Modifier.weight(1f), singleLine = true, label = { Text("Vence") }, supportingText = { Text("Opcional") })
                            }
                        } else if (feature == "loads") {
                            Text("Datos del envío", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    SelectorButton("Moneda", currencyOptions.firstOrNull { it.first == currency }?.second ?: currency, Modifier.fillMaxWidth()) { currencyMenu = true }
                                    DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                                        currencyOptions.forEach { (code, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = { currency = code; currencyMenu = false })
                                        }
                                    }
                                }
                                OutlinedTextField(exchangeRate, { exchangeRate = purchaseDecimalInput(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Tasa a DOP") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    SelectorButton("Courier o naviera", carrier.ifBlank { "Sin indicar" }, Modifier.fillMaxWidth()) { carrierMenu = true }
                                    DropdownMenu(expanded = carrierMenu, onDismissRequest = { carrierMenu = false }) {
                                        DropdownMenuItem(text = { Text("Sin indicar") }, onClick = { carrier = ""; carrierMenu = false })
                                        workspace.suppliers.forEach { item ->
                                            DropdownMenuItem(text = { Text(item.name) }, onClick = { carrier = item.name; carrierMenu = false })
                                        }
                                    }
                                }
                                OutlinedTextField(trackingNumber, { trackingNumber = it }, Modifier.weight(1f), singleLine = true, label = { Text("Guía o BL") })
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(expectedAt, { expectedAt = it.filter { char -> char.isDigit() || char == '-' }.take(10) }, Modifier.weight(1f), singleLine = true, label = { Text("Llega aproximadamente") }, supportingText = { Text("AAAA-MM-DD") })
                                OutlinedTextField(shippingPounds, { shippingPounds = purchaseDecimalInput(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Libras del envío") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(freightAmount, { freightAmount = purchaseDecimalInput(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Flete") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                OutlinedTextField(customsAmount, { customsAmount = purchaseDecimalInput(it) }, Modifier.weight(1f), singleLine = true, label = { Text("Aduana") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                        } else {
                            Box {
                                SelectorButton("Moneda de la factura", currencyOptions.firstOrNull { it.first == currency }?.second ?: currency, Modifier.fillMaxWidth()) { currencyMenu = true }
                                DropdownMenu(expanded = currencyMenu, onDismissRequest = { currencyMenu = false }) {
                                    currencyOptions.forEach { (code, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = { currency = code; currencyMenu = false })
                                    }
                                }
                            }
                        }
                        Box {
                            SelectorButton("Suplidor", supplier?.name ?: "Sin suplidor", Modifier.fillMaxWidth()) { supplierMenu = true }
                            DropdownMenu(expanded = supplierMenu, onDismissRequest = { supplierMenu = false }) {
                                DropdownMenuItem(text = { Text("Sin suplidor") }, onClick = { supplier = null; supplierMenu = false })
                                workspace.suppliers.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.name + (item.invoiceCurrency?.let { " · $it" } ?: "")) },
                                        onClick = {
                                            supplier = item
                                            item.invoiceCurrency?.takeIf { it.isNotBlank() }?.let { currency = it }
                                            supplierMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        if (feature == "containers" && loadDocuments.isNotEmpty()) {
                            Box {
                                SelectorButton("Carga relacionada", parentLoad?.documentNumber ?: "Sin carga", Modifier.fillMaxWidth()) { parentLoadMenu = true }
                                DropdownMenu(expanded = parentLoadMenu, onDismissRequest = { parentLoadMenu = false }) {
                                    DropdownMenuItem(text = { Text("Sin carga") }, onClick = { parentLoad = null; parentLoadMenu = false })
                                    loadDocuments.forEach { load ->
                                        DropdownMenuItem(text = { Text("${load.documentNumber} · ${load.currency}") }, onClick = { parentLoad = load; parentLoadMenu = false })
                                    }
                                }
                            }
                        }
                        if (feature != "loads" && !debtMode) {
                            workspace.products.takeIf { it.isNotEmpty() }?.let {
                                Text("Líneas de compra", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                            }
                            lines.forEachIndexed { index, line ->
                                PurchaseLineEditor(
                                    index = index,
                                    line = line,
                                    products = workspace.products,
                                    canRemove = lines.size > 1,
                                    onChange = { updated -> lines = lines.toMutableList().also { it[index] = updated } },
                                    onRemove = { lines = lines.toMutableList().also { it.removeAt(index) } }
                                )
                            }
                            TextButton(onClick = { lines = lines + PurchaseLineDraft() }) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Agregar otro producto") }
                        }
                        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Notas (opcional)") })
                        if (feature in setOf("containers", "purchase_invoices") && !debtMode) {
                            OutlinedButton(
                                onClick = {
                                    invoiceLauncher.launch(arrayOf(
                                        "text/csv",
                                        "text/plain",
                                        "application/pdf",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    ))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.saving
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Leer factura antes de recibir")
                            }
                            Text(
                                "La lectura prepara una revisión; no crea productos ni modifica inventario hasta que confirmes la recepción. Usa PDF con texto, Excel o CSV.",
                                color = BSPOSTheme.colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            state.preview?.let { preview ->
                                val detectedLines = preview.rows.filter { it.valid && !it.productId.isNullOrBlank() }
                                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Revisión: ${preview.file.name}", fontWeight = FontWeight.Bold)
                                        Text(
                                            "${preview.counts.matched} encontradas · ${preview.counts.valid} válidas · ${preview.counts.needsReview} por revisar",
                                            color = BSPOSTheme.colors.textSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        preview.warnings.take(2).forEach { warning ->
                                            Text("Aviso: $warning", color = BSPOSTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
                                        }
                                        preview.rows.take(4).forEach { row ->
                                            Text(
                                                "Línea ${row.line}: ${row.productName} · ${row.quantity} × ${row.unitCost ?: "sin costo"}${if (row.valid) "" else " · requiere revisión"}",
                                                color = if (row.valid) BSPOSTheme.colors.textPrimary else BSPOSTheme.colors.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (detectedLines.isNotEmpty()) {
                                                    lines = detectedLines.map { row ->
                                                        PurchaseLineDraft(row.productId.orEmpty(), row.quantity.toString(), row.unitCost ?: "0")
                                                    }
                                                }
                                            },
                                            enabled = detectedLines.isNotEmpty() && !state.saving,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Usar líneas válidas") }
                                    }
                                }
                            }
                        }
                        if (feature != "loads" && !debtMode) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { mode = "draft" }, modifier = Modifier.weight(1f), enabled = !state.saving) { Text(if (mode == "draft") "✓ Borrador" else "Borrador") }
                                Button(onClick = { mode = "received" }, modifier = Modifier.weight(1f), enabled = !state.saving) { Text(if (mode == "received") "✓ Recibir" else "Recibir") }
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.create(
                                    type = type,
                                    documentNumber = documentNumber,
                                    supplierId = supplier?.id,
                                    mode = if (feature == "loads") "draft" else mode,
                                    notes = notes,
                                    lines = if (feature == "loads" || debtMode) emptyList() else lines,
                                    exchangeRate = exchangeRate,
                                    currency = currency,
                                    carrier = carrier,
                                    trackingNumber = trackingNumber,
                                    expectedAt = expectedAt,
                                    shippingPounds = shippingPounds,
                                    freightAmount = freightAmount,
                                    customsAmount = customsAmount,
                                    parentDocumentId = parentLoad?.id,
                                    amount = debtAmount.takeIf { debtMode },
                                    invoiceDate = invoiceDate.takeIf { debtMode },
                                    dueAt = dueAt.takeIf { debtMode }
                                )
                                showForm = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSave && !state.saving
                        ) {
                            if (state.saving) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (debtMode) "Registrar deuda" else if (feature == "loads" || mode == "draft") "Guardar borrador" else "Recibir inventario")
                        }
                    }
                }
            }
        }
        item { Text("Compras recientes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary) }
        if (documents.isEmpty()) {
            item { Notice("Todavía no hay compras registradas.", success = true) }
        } else {
            itemsIndexed(documents, key = { _, document -> document.id }) { _, document ->
                PurchaseDocumentCard(document, state.saving) { documentId -> viewModel.receive(documentId, feature) }
            }
        }
    }
}

@Composable
private fun SupplierManagerContent(
    workspace: com.example.bspos.data.micatalogo.dto.PurchaseWorkspaceDto,
    state: PurchaseModuleUiState,
    viewModel: PurchaseModuleViewModel
) {
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var invoiceCurrency by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { viewModel.load("suppliers") }) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = BSPOSTheme.colors.primary) }
            }
        }
        state.message?.let { message -> item { Notice(message, success = true) } }
        state.error?.let { error -> item { Notice(error, success = false) } }
        item {
            Button(onClick = { showForm = !showForm }, modifier = Modifier.fillMaxWidth(), enabled = !state.saving) {
                Icon(if (showForm) Icons.Default.DeleteOutline else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (showForm) "Cerrar nuevo suplidor" else "Nuevo suplidor")
            }
        }
        if (showForm) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Nuevo suplidor", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nombre") })
                        OutlinedTextField(invoiceCurrency, { invoiceCurrency = it.filter(Char::isLetter).uppercase().take(3) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Moneda de la factura") }, supportingText = { Text("Ej.: DOP, USD o EUR") })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(phone, { phone = it }, Modifier.weight(1f), singleLine = true, label = { Text("Teléfono") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            OutlinedTextField(email, { email = it }, Modifier.weight(1f), singleLine = true, label = { Text("Correo") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        }
                        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Dirección") })
                        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Notas (opcional)") })
                        Button(
                            onClick = { viewModel.createSupplier(name, invoiceCurrency, phone, email, address, notes) },
                            enabled = name.trim().isNotBlank() && !state.saving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.saving) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.saving) "Guardando…" else "Guardar suplidor")
                        }
                    }
                }
            }
        }
        if (workspace.suppliers.isEmpty()) {
            item { Notice("Todavía no hay suplidores registrados.", success = true) }
        } else {
            itemsIndexed(workspace.suppliers, key = { _, supplier -> supplier.id }) { _, supplier ->
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(supplier.name, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                        supplier.invoiceCurrency?.takeIf { it.isNotBlank() }?.let { Text("Factura en $it", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                        supplier.phone?.takeIf { it.isNotBlank() }?.let { Text(it, color = BSPOSTheme.colors.textSecondary) }
                        supplier.email?.takeIf { it.isNotBlank() }?.let { Text(it, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                        supplier.address?.takeIf { it.isNotBlank() }?.let { Text(it, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseLineEditor(
    index: Int,
    line: PurchaseLineDraft,
    products: List<PurchaseProductDto>,
    canRemove: Boolean,
    onChange: (PurchaseLineDraft) -> Unit,
    onRemove: () -> Unit
) {
    var menu by remember(line.productId) { mutableStateOf(false) }
    val selected = products.firstOrNull { it.id == line.productId }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Producto ${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, enabled = canRemove) { Icon(Icons.Default.DeleteOutline, contentDescription = "Quitar producto") }
            }
            Box {
                SelectorButton("Producto", selected?.let { if (it.code.isBlank()) it.name else "${it.name} · ${it.code}" } ?: "Selecciona un producto", Modifier.fillMaxWidth()) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    products.forEach { product -> DropdownMenuItem(text = { Text(if (product.code.isBlank()) product.name else "${product.name} · ${product.code}") }, onClick = { onChange(line.copy(productId = product.id, unitCost = line.unitCost.ifBlank { product.cost })); menu = false }) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(line.quantity, { onChange(line.copy(quantity = it.filter(Char::isDigit))) }, Modifier.weight(1f), singleLine = true, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(line.unitCost, { onChange(line.copy(unitCost = it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Costo unitario") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        }
    }
}

@Composable
private fun PurchaseDocumentCard(document: PurchaseDocumentDto, saving: Boolean, onReceive: (String) -> Unit) {
    val statusLabel = when (document.status) {
        "draft" -> "Borrador"
        "debt" -> "Pendiente de pago"
        else -> "Recibida"
    }
    val statusColor = if (document.status == "received") BSPOSTheme.colors.success else BSPOSTheme.colors.warning
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(document.documentNumber, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(document.supplier?.name ?: "Sin suplidor", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = .12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(BSPOSTheme.colors.outline))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Total", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                    Text("${document.currency} ${document.total}", color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${document.items.size} producto(s)", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    if (document.status == "debt") Text("Factura ${document.invoiceDate ?: "sin fecha"}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (document.items.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    document.items.take(3).forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .background(BSPOSTheme.colors.surfaceVariant, RoundedCornerShape(7.dp))
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${item.quantity} ×", color = BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(item.productName.ifBlank { "Producto sin nombre" }, color = BSPOSTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                            Spacer(Modifier.width(8.dp))
                            Text("${document.currency} ${item.lineTotal}", color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    if (document.items.size > 3) {
                        Text("+ ${document.items.size - 3} producto(s) más", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (document.type == "load") {
                val logistics = listOfNotNull(
                    document.carrier?.takeIf { it.isNotBlank() },
                    document.trackingNumber?.takeIf { it.isNotBlank() },
                    document.shippingPounds?.takeIf { it.isNotBlank() }?.let { "$it lb" }
                ).joinToString(" · ")
                Column(
                    Modifier.fillMaxWidth().background(BSPOSTheme.colors.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (logistics.isNotBlank()) Text(logistics, color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.bodySmall)
                    Text("${document.currency}${document.exchangeRate?.let { " · tasa $it" } ?: ""} · Flete/aduana: ${document.freightAmount ?: "0.00"}/${document.customsAmount ?: "0.00"}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            } else if (document.status == "draft") {
                OutlinedButton(onClick = { onReceive(document.id) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Recibir inventario") }
            }
        }
    }
}

private fun purchaseDecimalInput(value: String): String {
    val normalized = value.replace(',', '.')
    val filtered = normalized.filter { it.isDigit() || it == '.' }
    val dot = filtered.indexOf('.')
    return if (dot >= 0) filtered.take(dot + 1) + filtered.substring(dot + 1).replace(".", "") else filtered
}

@Composable
private fun SelectorButton(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
        Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) { Text(value, color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Notice(message: String, success: Boolean) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (success) BSPOSTheme.colors.primaryLight else BSPOSTheme.colors.error.copy(alpha = .10f))) {
        Text(message, Modifier.padding(14.dp), color = if (success) BSPOSTheme.colors.textPrimary else BSPOSTheme.colors.error)
    }
}

@Composable
private fun PurchaseError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Inventory2, contentDescription = null, tint = BSPOSTheme.colors.error)
        Spacer(Modifier.height(10.dp))
        Text(message, color = BSPOSTheme.colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Reintentar") }
    }
}
