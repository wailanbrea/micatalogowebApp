package com.example.bspos.presentation.inventory

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.InventoryAdjustmentReason
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.MiCatalogoInventoryImportPreview
import com.example.bspos.domain.model.Product
import com.example.bspos.presentation.common.DialogScrollableColumn
import java.util.Locale
import java.util.UUID

@Composable
fun InventoryScreen(
    onOpenProducts: (() -> Unit)? = null,
    onOpenPrices: (() -> Unit)? = null,
    initialImport: Boolean = false,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val stock by viewModel.stock.collectAsState()
    val products by viewModel.productNames.collectAsState()
    val movements by viewModel.movements.collectAsState()
    val reasons by viewModel.reasons.collectAsState()
    val editableShopIds by viewModel.editableShopIds.collectAsState()
    val importShop by viewModel.importShop.collectAsState()
    val importPreview by viewModel.importPreview.collectAsState()
    val importMappingPreview by viewModel.importMappingPreview.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isLoadingImport by viewModel.isLoadingImport.collectAsState()
    val message by viewModel.message.collectAsState()
    var filter by remember { mutableStateOf(0) }
    var initialForm by remember { mutableStateOf(false) }
    var receiptForm by remember { mutableStateOf(false) }
    var receiptProductId by remember { mutableStateOf<UUID?>(null) }
    var countForm by remember { mutableStateOf(false) }
    var adjustmentForm by remember { mutableStateOf(false) }
    var reasonForm by remember { mutableStateOf(false) }
    var importRequested by remember(initialImport) { mutableStateOf(initialImport) }
    var selectedProduct by remember { mutableStateOf<UUID?>(null) }
    val names = products.associate { it.id to it.name }
    val productById = products.associateBy { it.id }
    val filtered = stock.filter { filter == 0 || (filter == 1 && it.quantity <= 0L) || (filter == 2 && it.quantity > 0L) }
    val unavailable = stock.count { it.quantity <= 0L }
    val lowStock = stock.count { row -> productById[row.productId]?.let { product -> product.minimumStock > 0L && row.quantity <= product.minimumStock } == true }
    val totalUnits = stock.sumOf { it.quantity }
    val capitalAtCost = stock.sumOf { row -> row.quantity * (productById[row.productId]?.averageCost ?: 0L) }
    val productsWithoutPhoto = products.count { it.isActive && it.deletedAt == null && it.imagePath.isNullOrBlank() }
    val stockEditableProducts = products.filter { it.remoteSaleUnit != "decant" && it.remoteSaleUnit != "service" && (it.remoteShopId == null || it.remoteShopId in editableShopIds) }
    val productsWithoutStock = stockEditableProducts.filter { product -> stock.none { it.productId == product.id } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            val csv = buildInventoryCsv(products, stock)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(csv.toByteArray(Charsets.UTF_8))
            } ?: error("No se pudo abrir el destino del archivo.")
        }
        viewModel.showMessage(result.fold({ "Inventario exportado correctamente." }, { "No se pudo exportar el inventario: ${it.message ?: "error desconocido"}" }))
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val file = context.readInventoryFile(uri)
        if (file == null) {
            viewModel.showMessage("No se pudo leer el archivo. Usa un CSV, XLSX o PDF de hasta 10 MB.")
        } else {
            viewModel.previewImport(file.name, file.mimeType, file.bytes)
        }
    }
    LaunchedEffect(importRequested, importShop?.id) {
        if (importRequested && importShop != null && !isLoadingImport) {
            importRequested = false
            fileLauncher.launch(
                arrayOf(
                    "text/*",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "application/pdf"
                )
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("CATÁLOGO", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(if (initialImport) "Importar inventario" else "Inventario", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
            Text(
                if (initialImport) "Carga Excel, CSV o PDF y revisa los datos antes de guardarlos."
                else "Tus productos y lo que tienes en existencia.",
                color = BSPOSTheme.colors.textSecondary
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenProducts?.invoke() }, enabled = onOpenProducts != null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir del catálogo")
                }
                OutlinedButton(onClick = { onOpenPrices?.invoke() }, enabled = onOpenPrices != null, modifier = Modifier.weight(1f)) {
                    Text("Precios y costos")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { stock.firstOrNull()?.productId?.let { id -> selectedProduct = id; viewModel.select(id) } }, enabled = stock.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Movimientos")
                }
                OutlinedButton(
                    onClick = { exportLauncher.launch("inventario-${System.currentTimeMillis()}.csv") },
                    enabled = products.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Exportar")
                }
                OutlinedButton(
                    onClick = {
                        fileLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "application/pdf"
                            )
                        )
                    },
                    enabled = importShop != null && !isLoadingImport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLoadingImport) "Leyendo..." else "Importar")
                }
            }
        }
        item {
            InventorySummaryRow(
                capitalAtCost = capitalAtCost,
                productCount = products.count { it.isActive && it.deletedAt == null },
                totalUnits = totalUnits,
                lowStock = lowStock
            )
        }
        if (productsWithoutPhoto > 0) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.warningLight)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, null, tint = BSPOSTheme.colors.warning)
                        Spacer(Modifier.width(10.dp))
                        Text("$productsWithoutPhoto ${if (productsWithoutPhoto == 1) "producto sin foto" else "productos sin foto"} · revisa el catálogo", color = BSPOSTheme.colors.warning, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (unavailable > 0) Icons.Default.WarningAmber else Icons.Default.Inventory2, null, tint = if (unavailable > 0) BSPOSTheme.colors.error else BSPOSTheme.colors.success)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Estado del almacén", color = BSPOSTheme.colors.textSecondary)
                        Text(if (unavailable > 0) "$unavailable productos agotados" else "Inventario disponible", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(filter == 0, { filter = 0 }, SegmentedButtonDefaults.itemShape(0, 3)) { Text("Todos") }
                SegmentedButton(filter == 1, { filter = 1 }, SegmentedButtonDefaults.itemShape(1, 3)) { Text("Agotados") }
                SegmentedButton(filter == 2, { filter = 2 }, SegmentedButtonDefaults.itemShape(2, 3)) { Text("Disponibles") }
            }
        }
        if (filtered.isEmpty()) {
            item { Text("No hay existencias para este filtro", color = BSPOSTheme.colors.textSecondary) }
        } else {
            items(filtered, key = { it.id }) { item ->
                StockCard(
                    name = names[item.productId] ?: item.productId.toString(),
                    quantity = item.quantity,
                    onClick = { selectedProduct = item.productId; viewModel.select(item.productId) },
                    onReceive = if (stockEditableProducts.any { it.id == item.productId }) {
                        { receiptProductId = item.productId; receiptForm = true }
                    } else null
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ reasonForm = true }, Modifier.weight(1f)) { Text("Motivos") }
                OutlinedButton({ adjustmentForm = true }, Modifier.weight(1f), enabled = stockEditableProducts.isNotEmpty() && reasons.any { it.isActive }) { Text("Ajuste") }
            }
            Spacer(Modifier.size(8.dp))
            Button({ receiptProductId = null; receiptForm = true }, Modifier.fillMaxWidth(), enabled = stockEditableProducts.isNotEmpty()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Aumentar existencias")
            }
            OutlinedButton({ countForm = true }, Modifier.fillMaxWidth(), enabled = stockEditableProducts.isNotEmpty()) { Text("Conteo físico") }
            Text("Cada entrada conserva su costo. Los lotes y precios Pro se calculan al sincronizar con la tienda.",
                color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Button({ initialForm = true }, Modifier.fillMaxWidth(), enabled = productsWithoutStock.isNotEmpty()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Registrar inventario inicial")
            }
        }
    }

    if (initialForm) InitialInventoryForm(productsWithoutStock, { id, quantity, cost -> viewModel.initialize(id, quantity, cost); initialForm = false }, { initialForm = false })
    if (receiptForm && stockEditableProducts.isNotEmpty()) {
        val receiptProducts = receiptProductId?.let { selectedId ->
            stockEditableProducts.sortedBy { if (it.id == selectedId) 0 else 1 }
        } ?: stockEditableProducts
        InitialInventoryForm(
            products = receiptProducts,
            onSave = { id, quantity, cost -> viewModel.receive(id, quantity, cost); receiptForm = false; receiptProductId = null },
            onDismiss = { receiptForm = false; receiptProductId = null },
            title = if (receiptProductId == null) "Aumentar existencias" else "Entrada de mercancía"
        )
    }
    if (countForm && stockEditableProducts.isNotEmpty()) PhysicalCountForm(stockEditableProducts, { id, quantity, notes -> viewModel.count(id, quantity, notes); countForm = false }, { countForm = false })
    if (reasonForm) AdjustmentReasonForm({ name, direction -> viewModel.addReason(name, direction); reasonForm = false }, { reasonForm = false })
    if (adjustmentForm && stockEditableProducts.isNotEmpty()) InventoryAdjustmentForm(stockEditableProducts, reasons, { product, type, quantity, cost, reason, notes -> viewModel.adjust(product.id, type, quantity, cost, reason.id, notes); adjustmentForm = false }, { adjustmentForm = false })
    selectedProduct?.let { id ->
        AlertDialog(
            onDismissRequest = { selectedProduct = null; viewModel.select(null) },
            title = { Text("Kardex: ${names[id] ?: "Producto"}") },
            text = { LazyColumn { items(movements, key = { it.id }) { movement -> androidx.compose.material3.ListItem({ Text(movement.type.label()) }, supportingContent = { Text("Cantidad ${movement.quantity} / Saldo ${movement.newQuantity}") }) } } },
            confirmButton = { TextButton({ selectedProduct = null; viewModel.select(null) }) { Text("Cerrar") } }
        )
    }
    importPreview?.let { preview ->
        InventoryImportPreviewDialog(
            preview = preview,
            importing = isImporting,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImportPreview
        )
    }
    importMappingPreview?.let { preview ->
        InventoryImportMappingDialog(
            preview = preview,
            loading = isLoadingImport,
            onApply = viewModel::applyImportMapping,
            onDismiss = viewModel::dismissImportMapping
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            title = { Text("Inventario") },
            text = { Text(text) },
            confirmButton = { TextButton(viewModel::consumeMessage) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun InventorySummaryRow(capitalAtCost: Long, productCount: Int, totalUnits: Long, lowStock: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val metrics = listOf(
            Triple("Capital al costo", MoneyUtils.formatCents(capitalAtCost, LocalCurrency.current), BSPOSTheme.colors.primary),
            Triple("Productos", "$productCount · $totalUnits unidades", BSPOSTheme.colors.textPrimary),
            Triple("Nivel bajo", lowStock.toString(), if (lowStock > 0) BSPOSTheme.colors.warning else BSPOSTheme.colors.success)
        )
        if (maxWidth < 540.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { metrics.forEach { (label, value, color) -> InventoryMetricCard(label, value, color) } }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { metrics.forEach { (label, value, color) -> InventoryMetricCard(label, value, color, Modifier.weight(1f)) } }
        }
    }
}

@Composable
private fun InventoryMetricCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2)
        }
    }
}

@Composable
private fun InventoryImportMappingDialog(
    preview: MiCatalogoInventoryImportPreview,
    loading: Boolean,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var mapping by remember(preview.headers, preview.mapping) { mutableStateOf(preview.mapping) }
    val canPreview = mapping["name"].orEmpty().isNotBlank() && mapping["price"].orEmpty().isNotBlank()
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Relaciona las columnas") },
        text = {
            DialogScrollableColumn {
                Text("Revisamos los encabezados del archivo. Esta selección se guardará para esta tienda.", color = BSPOSTheme.colors.textSecondary)
                preview.fields.forEach { (field, label) ->
                    var open by remember(field) { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("$label: ${mapping[field]?.ifBlank { "No importar" } ?: "No importar"}")
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("No importar") }, onClick = { mapping = mapping + (field to ""); open = false })
                            preview.headers.forEach { header ->
                                DropdownMenuItem(text = { Text(header) }, onClick = { mapping = mapping + (field to header); open = false })
                            }
                        }
                    }
                }
                if (!canPreview) Text("Nombre y precio son obligatorios.", color = BSPOSTheme.colors.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(mapping) }, enabled = canPreview && !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Ver vista previa")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } }
    )
}

@Composable
private fun InventoryImportPreviewDialog(
    preview: MiCatalogoInventoryImportPreview,
    importing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val exceedsQuota = preview.validRows > preview.quota.productsRemaining
    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text("Vista previa del inventario") },
        text = {
            DialogScrollableColumn {
                Text("${preview.validRows} filas listas y ${preview.invalidRows} necesitan revisión.", fontWeight = FontWeight.Bold)
                Text("Cupo disponible: ${preview.quota.productsRemaining} productos.", color = BSPOSTheme.colors.textSecondary)
                if (exceedsQuota) Text("El archivo supera el cupo de tu plan.", color = BSPOSTheme.colors.error, fontWeight = FontWeight.Bold)
                preview.rows.take(30).forEach { row ->
                    Text(
                        text = "Fila ${row.line}: ${row.name.ifBlank { "Sin nombre" }} · ${row.price?.let { "RD$ $it" } ?: "Sin precio"}",
                        color = if (row.valid) BSPOSTheme.colors.textPrimary else BSPOSTheme.colors.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (row.errors.isNotEmpty()) Text(row.errors.joinToString(" "), color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall)
                }
                if (preview.rows.size > 30) Text("Se muestran las primeras 30 filas.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !importing && preview.validRows > 0 && !exceedsQuota) {
                if (importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Importar ${preview.validRows} productos")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !importing) { Text("Cancelar") } }
    )
}

private data class InventoryFile(val name: String, val mimeType: String?, val bytes: ByteArray)

private fun Context.readInventoryFile(uri: Uri): InventoryFile? {
    val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    if (size > 10 * 1024 * 1024) return null
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.ifBlank { null } ?: "inventario.csv"
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > 10 * 1024 * 1024) return null
    return InventoryFile(name, contentResolver.getType(uri), bytes)
}

private fun buildInventoryCsv(products: List<Product>, stock: List<com.example.bspos.domain.model.InventoryStock>): String {
    val quantities = stock.groupBy { it.productId }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    return buildString {
        appendLine("nombre,codigo,barras,existencias,precio_venta,costo_promedio,ultimo_costo,stock_minimo,unidad")
        products.filter { it.isActive && it.deletedAt == null }.forEach { product ->
            appendLine(
                listOf(
                    product.name,
                    product.internalCode,
                    product.barcode.orEmpty(),
                    quantities[product.id]?.toString().orEmpty(),
                    MoneyUtils.formatCentsCompact(product.salePrice),
                    MoneyUtils.formatCentsCompact(product.averageCost),
                    MoneyUtils.formatCentsCompact(product.lastPurchaseCost),
                    product.minimumStock.toString(),
                    product.remoteSaleUnit.orEmpty()
                ).joinToString(",", transform = ::escapeCsv)
            )
        }
    }
}

private fun escapeCsv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

@Composable
private fun StockCard(name: String, quantity: Long, onClick: () -> Unit, onReceive: (() -> Unit)?) {
    val color = if (quantity <= 0) BSPOSTheme.colors.error else BSPOSTheme.colors.success
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, null, tint = color) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.ExtraBold); Text(if (quantity <= 0) "Agotado" else "Disponible", color = color, style = MaterialTheme.typography.labelMedium) }
            Text(quantity.toString(), color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            onReceive?.let { receive ->
                OutlinedButton(
                    onClick = receive,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Entrada", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun InitialInventoryForm(products: List<Product>, onSave: (UUID, Long, Long) -> Unit, onDismiss: () -> Unit, title: String = "Inventario inicial") {
    var selected by remember { mutableStateOf(products.first()) }
    var open by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        DialogScrollableColumn {
            Box { TextButton({ open = true }) { Text("Producto: ${selected.name}") }; DropdownMenu(open, { open = false }) { products.forEach { item -> DropdownMenuItem({ Text(item.name) }, { selected = item; open = false }) } } }
            OutlinedTextField(quantity, { quantity = it; error = null }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = error != null)
            OutlinedTextField(cost, { cost = it; error = null }, label = { Text("Costo unitario (${LocalCurrency.current.symbol})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error != null)
            error?.let { Text(it, color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton({ val q = quantity.toLongOrNull(); val c = MoneyUtils.parseDecimalToCents(cost); if (q == null || q !in 1..1_000_000L || c == null || c !in 0..100_000_000_000L) error = "Indica una cantidad mayor que cero y un costo válido" else onSave(selected.id, q, c) }) { Text("Registrar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun PhysicalCountForm(products: List<Product>, onSave: (UUID, Long, String) -> Unit, onDismiss: () -> Unit) {
    var product by remember { mutableStateOf(products.first()) }
    var open by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Conteo físico") }, text = {
        DialogScrollableColumn {
            Box { TextButton({ open = true }) { Text(product.name) }; DropdownMenu(open, { open = false }) {
                products.forEach { item -> DropdownMenuItem({ Text(item.name) }, { product = item; open = false }) }
            } }
            OutlinedTextField(quantity, { quantity = it; error = false }, label = { Text("Cantidad contada (puede ser 0)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(notes, { notes = it; error = false }, label = { Text("Nota obligatoria") })
            Text("Si el saldo remoto cambió, la operación quedará pendiente de revisión. Un conteo no acredita costo de compra.", style = MaterialTheme.typography.bodySmall)
            if (error) Text("Indica cantidad válida y nota.", color = BSPOSTheme.colors.error)
        }
    }, confirmButton = { TextButton({ val counted = quantity.toLongOrNull(); if (counted == null || counted !in 0..1_000_000L || notes.isBlank()) error = true else onSave(product.id, counted, notes) }) { Text("Guardar conteo") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun AdjustmentReasonForm(onSave: (String, AdjustmentDirection) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(AdjustmentDirection.OUT) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuevo motivo") }, text = { DialogScrollableColumn { OutlinedTextField(name, { name = it }, label = { Text("Nombre") }); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AdjustmentDirection.entries.forEach { value -> FilterChip(value == direction, { direction = value }, label = { Text(value.label()) }) } } } }, confirmButton = { TextButton({ if (name.isNotBlank()) onSave(name, direction) }) { Text("Guardar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun InventoryAdjustmentForm(products: List<Product>, reasons: List<InventoryAdjustmentReason>, onSave: (Product, InventoryMovementType, Long, Long, InventoryAdjustmentReason, String) -> Unit, onDismiss: () -> Unit) {
    var product by remember { mutableStateOf(products.first()) }
    var type by remember { mutableStateOf(InventoryMovementType.LOSS) }
    var quantity by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var productOpen by remember { mutableStateOf(false) }
    var typeOpen by remember { mutableStateOf(false) }
    var reasonOpen by remember { mutableStateOf(false) }
    val compatible = reasons.filter { it.isActive && (it.direction == type.direction || it.direction == AdjustmentDirection.BOTH) }
    var reason by remember(type, compatible) { mutableStateOf(compatible.firstOrNull()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ajuste o merma") }, text = {
        DialogScrollableColumn {
            Box { TextButton({ productOpen = true }) { Text("Producto: ${product.name}") }; DropdownMenu(productOpen, { productOpen = false }) { products.forEach { item -> DropdownMenuItem({ Text(item.name) }, { product = item; productOpen = false }) } } }
            Box { TextButton({ typeOpen = true }) { Text("Tipo: ${type.label()}") }; DropdownMenu(typeOpen, { typeOpen = false }) { listOf(InventoryMovementType.ADJUSTMENT_IN, InventoryMovementType.ADJUSTMENT_OUT, InventoryMovementType.DAMAGED, InventoryMovementType.LOSS, InventoryMovementType.EXPIRED, InventoryMovementType.INTERNAL_USE).forEach { item -> DropdownMenuItem({ Text(item.label()) }, { type = item; typeOpen = false }) } } }
            Box { TextButton({ reasonOpen = true }) { Text("Motivo: ${reason?.name ?: "Seleccione"}") }; DropdownMenu(reasonOpen, { reasonOpen = false }) { compatible.forEach { item -> DropdownMenuItem({ Text(item.name) }, { reason = item; reasonOpen = false }) } } }
            OutlinedTextField(quantity, { quantity = it; error = null }, label = { Text("Cantidad absoluta") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = error != null)
            OutlinedTextField(cost, { cost = it.filter(Char::isDigit); error = null }, label = { Text("Costo unitario (${LocalCurrency.current.symbol})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = error != null)
            OutlinedTextField(notes, { notes = it; error = null }, label = { Text("Nota obligatoria") }, isError = error != null)
            error?.let { Text(it, color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton({ val q = quantity.toLongOrNull(); val c = MoneyUtils.parseWholeUnitsToCents(cost); if (q == null || q <= 0 || c == null || c < 0 || reason == null || notes.isBlank()) error = "Completa cantidad, costo, motivo y nota" else onSave(product, type, if (type.direction == AdjustmentDirection.OUT) -q else q, c, reason!!, notes) }) { Text("Registrar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

private fun AdjustmentDirection.label(): String = when (this) {
    AdjustmentDirection.IN -> "Entrada"
    AdjustmentDirection.OUT -> "Salida"
    AdjustmentDirection.BOTH -> "Entrada o salida"
}

private fun InventoryMovementType.label(): String = when (this) {
    InventoryMovementType.INITIAL -> "Inventario inicial"
    InventoryMovementType.PURCHASE -> "Compra"
    InventoryMovementType.PURCHASE_RETURN -> "Devolución de compra"
    InventoryMovementType.SALE -> "Venta"
    InventoryMovementType.SALE_RETURN -> "Devolución de venta"
    InventoryMovementType.ADJUSTMENT_IN -> "Ajuste de entrada"
    InventoryMovementType.ADJUSTMENT_OUT -> "Ajuste de salida"
    InventoryMovementType.DAMAGED -> "Producto dañado"
    InventoryMovementType.LOSS -> "Pérdida"
    InventoryMovementType.EXPIRED -> "Vencido"
    InventoryMovementType.INTERNAL_USE -> "Uso interno"
    InventoryMovementType.PHYSICAL_COUNT_IN -> "Conteo físico de entrada"
    InventoryMovementType.PHYSICAL_COUNT_OUT -> "Conteo físico de salida"
    InventoryMovementType.ROUTE_LOAD_OUT -> "Salida a ruta"
    InventoryMovementType.ROUTE_LOAD_IN -> "Entrada desde ruta"
    InventoryMovementType.ROUTE_SALE -> "Venta en ruta"
    InventoryMovementType.ROUTE_RETURN -> "Devolución de ruta"
    InventoryMovementType.TRANSFER_IN -> "Transferencia de entrada"
    InventoryMovementType.TRANSFER_OUT -> "Transferencia de salida"
}
