package com.example.bspos.presentation.purchase

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
    LaunchedEffect(feature) { viewModel.load() }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        when {
            state.loading && state.workspace == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BSPOSTheme.colors.primary)
            state.error != null && state.workspace == null -> PurchaseError(state.error!!, onRetry = viewModel::load)
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
    var notes by remember(feature) { mutableStateOf("") }
    var supplier by remember(feature) { mutableStateOf<PurchaseSupplierDto?>(null) }
    var supplierMenu by remember(feature) { mutableStateOf(false) }
    var mode by remember(feature) { mutableStateOf("draft") }
    var lines by remember(feature) { mutableStateOf(listOf(PurchaseLineDraft())) }
    val title = if (feature == "purchase_invoices") "Facturas de compra" else "Contenedores"
    val type = if (feature == "purchase_invoices") "purchase_invoice" else "container"
    val canSave = documentNumber.isNotBlank() && lines.isNotEmpty() && lines.all { it.productId.isNotBlank() && (it.quantity.toIntOrNull() ?: 0) > 0 && it.unitCost.replace(',', '.').toDoubleOrNull()?.let { cost -> cost >= 0 } == true }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("COMPRAS", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                    Text("Cada compra conserva su costo y puede recibirse una sola vez.", color = BSPOSTheme.colors.textSecondary)
                }
                IconButton(onClick = viewModel::load) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = BSPOSTheme.colors.primary) }
            }
        }
        state.message?.let { message -> item { Notice(message, success = true) } }
        state.error?.let { error -> item { Notice(error, success = false) } }
        item {
            Button(onClick = { showForm = !showForm }, modifier = Modifier.fillMaxWidth()) {
                Icon(if (showForm) Icons.Default.DeleteOutline else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (showForm) "Cerrar nueva compra" else "Nueva compra")
            }
        }
        if (showForm) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Preparar compra", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("Guarda un borrador para revisarlo o recibe el inventario de inmediato.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(documentNumber, { documentNumber = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("No. documento") })
                        Box {
                            SelectorButton("Suplidor", supplier?.name ?: "Sin suplidor", Modifier.fillMaxWidth()) { supplierMenu = true }
                            DropdownMenu(expanded = supplierMenu, onDismissRequest = { supplierMenu = false }) {
                                DropdownMenuItem(text = { Text("Sin suplidor") }, onClick = { supplier = null; supplierMenu = false })
                                workspace.suppliers.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { supplier = item; supplierMenu = false }) }
                            }
                        }
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
                        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Notas (opcional)") })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { mode = "draft" }, modifier = Modifier.weight(1f), enabled = !state.saving) { Text(if (mode == "draft") "✓ Borrador" else "Borrador") }
                            Button(onClick = { mode = "received" }, modifier = Modifier.weight(1f), enabled = !state.saving) { Text(if (mode == "received") "✓ Recibir" else "Recibir") }
                        }
                        Button(
                            onClick = { viewModel.create(type, documentNumber, supplier?.id, mode, notes, lines); showForm = false },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSave && !state.saving
                        ) {
                            if (state.saving) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (mode == "draft") "Guardar borrador" else "Recibir inventario")
                        }
                    }
                }
            }
        }
        item { Text("Compras recientes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary) }
        if (workspace.documents.isEmpty()) {
            item { Notice("Todavía no hay compras registradas.", success = true) }
        } else {
            itemsIndexed(workspace.documents, key = { _, document -> document.id }) { _, document ->
                PurchaseDocumentCard(document, state.saving, viewModel::receive)
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
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(document.documentNumber, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                    Text(document.supplier?.name ?: "Sin suplidor", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Text(if (document.status == "draft") "Borrador" else "Recibida", color = if (document.status == "draft") BSPOSTheme.colors.warning else BSPOSTheme.colors.success, fontWeight = FontWeight.Bold)
            }
            Text("${document.items.size} producto(s) · ${document.currency} ${document.total}", color = BSPOSTheme.colors.textSecondary)
            if (document.status == "draft") {
                Button(onClick = { onReceive(document.id) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Recibir inventario") }
            }
        }
    }
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

