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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.InventoryAdjustmentReason
import com.example.bspos.domain.model.InventoryMovement
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.MiCatalogoInventoryImportPreview
import com.example.bspos.domain.model.Product
import com.example.bspos.presentation.common.DialogScrollableColumn
import coil.compose.AsyncImage
import java.util.Locale
import java.util.UUID

@Composable
fun InventoryScreen(
    onOpenProducts: (() -> Unit)? = null,
    onOpenPrices: (() -> Unit)? = null,
    onOpenImport: (() -> Unit)? = null,
    onOpenQuickCreate: (() -> Unit)? = null,
    onOpenStore: ((Product) -> Unit)? = null,
    initialImport: Boolean = false,
    initialAdjustment: Boolean = false,
    initialStockFilter: String? = null,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val stock by viewModel.stock.collectAsState()
    val products by viewModel.productNames.collectAsState()
    val movements by viewModel.movements.collectAsState()
    val allMovements by viewModel.allMovements.collectAsState()
    val reasons by viewModel.reasons.collectAsState()
    val editableShopIds by viewModel.editableShopIds.collectAsState()
    val importShop by viewModel.importShop.collectAsState()
    val importPreview by viewModel.importPreview.collectAsState()
    val importMappingPreview by viewModel.importMappingPreview.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val isLoadingImport by viewModel.isLoadingImport.collectAsState()
    val message by viewModel.message.collectAsState()
    var filter by remember(initialStockFilter) { mutableStateOf(when (initialStockFilter) { "in_stock" -> 2; "out" -> 1; else -> 0 }) }
    var inventoryTab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var photoFilter by remember { mutableStateOf(false) }
    var lowStockFilter by remember(initialStockFilter) { mutableStateOf(initialStockFilter == "low") }
    var initialForm by remember { mutableStateOf(false) }
    var receiptForm by remember { mutableStateOf(false) }
    var receiptProductId by remember { mutableStateOf<UUID?>(null) }
    var countForm by remember { mutableStateOf(false) }
    var adjustmentForm by remember(initialAdjustment) { mutableStateOf(initialAdjustment) }
    var adjustmentProductId by remember { mutableStateOf<UUID?>(null) }
    var reasonForm by remember { mutableStateOf(false) }
    var importRequested by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<UUID?>(null) }
    var showGlobalMovements by remember(initialStockFilter) { mutableStateOf(initialStockFilter == "movements") }
    val names = products.associate { it.id to it.name }
    val productById = products.associateBy { it.id }
    val quantityByProductId = stock.groupBy { it.productId }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    val activeProducts = products.filter { it.isActive && it.deletedAt == null }
    val archivedProducts = products.filter { !it.isActive || it.deletedAt != null }
    val comboProducts = activeProducts.filter { it.remoteIsCombo }
    val productsForTab = when (inventoryTab) {
        1 -> archivedProducts
        2 -> comboProducts
        else -> activeProducts
    }
    val filteredProducts = productsForTab.filter { product ->
        val quantity = quantityByProductId[product.id] ?: 0L
        val matchesQuery = query.isBlank() || product.name.contains(query, true) || product.internalCode.contains(query, true) || product.barcode.orEmpty().contains(query, true)
        val matchesStock = filter == 0 || (filter == 1 && quantity <= 0L) || (filter == 2 && quantity > 0L)
        val matchesPhoto = !photoFilter || !product.imagePath.isNullOrBlank()
        val matchesLow = !lowStockFilter || (product.minimumStock > 0L && quantity <= product.minimumStock)
        matchesQuery && matchesStock && matchesPhoto && matchesLow
    }
    val unavailable = activeProducts.count { (quantityByProductId[it.id] ?: 0L) <= 0L }
    val productsWithStock = activeProducts.count { (quantityByProductId[it.id] ?: 0L) > 0L }
    val lowStock = activeProducts.count { product -> product.minimumStock > 0L && (quantityByProductId[product.id] ?: 0L) <= product.minimumStock }
    val totalUnits = stock.sumOf { it.quantity }
    val capitalAtCost = stock.sumOf { row -> row.quantity * (productById[row.productId]?.averageCost ?: 0L) }
    val productsWithoutPhoto = products.count { it.isActive && it.deletedAt == null && it.imagePath.isNullOrBlank() }
    val stockEditableProducts = products.filter { !it.remoteIsCombo && it.remoteSaleUnit != "decant" && it.remoteSaleUnit != "service" && (it.remoteShopId == null || it.remoteShopId in editableShopIds) }
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (initialImport) item {
            Text("Carga Excel, CSV o PDF y revisa los datos antes de guardarlos.", color = BSPOSTheme.colors.textSecondary)
        }
        item {
            if (initialImport) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportChoiceButton(
                        title = "Desde archivo",
                        description = "Excel, CSV o PDF con previsualización",
                        onClick = { importRequested = true },
                        enabled = importShop != null && !isLoadingImport,
                        primary = true
                    )
                    ImportChoiceButton(
                        title = "Desde catálogo",
                        description = "Elige productos de la galería",
                        onClick = { onOpenProducts?.invoke() },
                        enabled = onOpenProducts != null
                    )
                    ImportChoiceButton(
                        title = "Crear rápido",
                        description = "Escribe nombre, precio y existencia",
                        onClick = { onOpenQuickCreate?.invoke() },
                        enabled = onOpenQuickCreate != null
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onOpenImport?.invoke() },
                        enabled = onOpenImport != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("Añadir del catálogo", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    OutlinedButton(
                        onClick = { importRequested = true },
                        enabled = importShop != null && !isLoadingImport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("Importar", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    Button(onClick = { onOpenProducts?.invoke() }, enabled = onOpenProducts != null, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Nuevo producto", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showGlobalMovements = true }, enabled = allMovements.isNotEmpty(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Movimientos", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                OutlinedButton(onClick = { onOpenPrices?.invoke() }, enabled = onOpenPrices != null, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Precios y costos", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                OutlinedButton(onClick = { exportLauncher.launch("inventario-${System.currentTimeMillis()}.csv") }, enabled = products.isNotEmpty(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Exportar", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
            }
        }
        item {
            OutlinedButton(
                onClick = { onOpenProducts?.invoke() },
                enabled = onOpenProducts != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Configurar combos desde Productos", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        item {
            InventorySummaryRow(
                capitalAtCost = capitalAtCost,
                productCount = activeProducts.size,
                productsWithStock = productsWithStock,
                totalUnits = totalUnits,
                lowStock = lowStock,
                exhausted = unavailable,
                averageMargin = averageMargin(activeProducts)
            )
        }
        if (productsWithoutPhoto > 0) {
            item {
                Card(onClick = { onOpenProducts?.invoke() }, enabled = onOpenProducts != null, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.warningLight)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, null, tint = BSPOSTheme.colors.warning)
                        Spacer(Modifier.width(10.dp))
                        Text("$productsWithoutPhoto ${if (productsWithoutPhoto == 1) "producto sin foto" else "productos sin foto"} · revisar", color = BSPOSTheme.colors.warning, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = BSPOSTheme.colors.warning)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InventoryTabChip("Activos", activeProducts.size, inventoryTab == 0, Modifier.weight(1f)) { inventoryTab = 0 }
                InventoryTabChip("Archivados", archivedProducts.size, inventoryTab == 1, Modifier.weight(1f)) { inventoryTab = 1 }
                InventoryTabChip("Combos", comboProducts.size, inventoryTab == 2, Modifier.weight(1f)) { inventoryTab = 2 }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = BSPOSTheme.colors.primary) },
                    placeholder = { Text("Buscar por nombre o SKU...") },
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedButton(onClick = { showFilters = !showFilters }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.FilterList, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Filtros")
                }
            }
        }
        if (showFilters) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("Todos") })
                    FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("Con existencia") })
                    FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("Agotados") })
                    FilterChip(selected = lowStockFilter, onClick = { lowStockFilter = !lowStockFilter }, label = { Text("Nivel bajo") })
                    FilterChip(selected = photoFilter, onClick = { photoFilter = !photoFilter }, label = { Text("Con foto") })
                }
            }
        }
        if (filteredProducts.isEmpty()) {
            item { EmptyInventoryState("No hay productos para este filtro", if (query.isBlank()) "Prueba otro estado de inventario." else "No encontramos coincidencias para \"$query\".") }
        } else {
            items(filteredProducts, key = { it.id }) { product ->
                val quantity = quantityByProductId[product.id] ?: 0L
                StockCard(
                    product = product,
                    quantity = quantity,
                    onClick = { selectedProduct = product.id; viewModel.select(product.id) },
                    onReceive = if (stockEditableProducts.any { it.id == product.id }) { { receiptProductId = product.id; receiptForm = true } } else null,
                    onEdit = { onOpenProducts?.invoke() }
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ reasonForm = true }, Modifier.weight(1f)) { Text("Motivos") }
                OutlinedButton({ adjustmentForm = true }, Modifier.weight(1f), enabled = stockEditableProducts.isNotEmpty() && reasons.any { it.isActive }) { Text("Ajuste") }
                OutlinedButton({ countForm = true }, Modifier.weight(1f), enabled = stockEditableProducts.isNotEmpty()) { Text("Conteo") }
            }
            Spacer(Modifier.size(4.dp))
            Button({ receiptProductId = null; receiptForm = true }, Modifier.fillMaxWidth(), enabled = stockEditableProducts.isNotEmpty(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Añadir inventario")
            }
            OutlinedButton({ initialForm = true }, Modifier.fillMaxWidth(), enabled = productsWithoutStock.isNotEmpty()) { Text("Registrar inventario inicial") }
            OutlinedButton(
                onClick = {
                    fileLauncher.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "application/pdf"))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = importShop != null && !isLoadingImport
            ) { Text(if (isLoadingImport) "Leyendo archivo..." else "Importar Excel, CSV o PDF") }
            Text("Cada entrada conserva su costo y se refleja en los lotes. El inventario se actualiza al registrar la operación.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
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
    if (adjustmentForm && stockEditableProducts.isNotEmpty()) InventoryAdjustmentForm(
        products = stockEditableProducts,
        reasons = reasons,
        initialProductId = adjustmentProductId,
        onSave = { product, type, quantity, cost, reason, notes ->
            viewModel.adjust(product.id, type, quantity, cost, reason.id, notes)
            adjustmentForm = false
            adjustmentProductId = null
        },
        onDismiss = { adjustmentForm = false; adjustmentProductId = null }
    )
    selectedProduct?.let { id ->
        InventoryProductDetailDialog(
            product = productById[id],
            quantity = quantityByProductId[id] ?: 0L,
            movements = movements,
            onDismiss = { selectedProduct = null; viewModel.select(null) },
            onReceive = if (stockEditableProducts.any { it.id == id }) {
                { selectedProduct = null; viewModel.select(null); receiptProductId = id; receiptForm = true }
            } else null,
            onEdit = { onOpenProducts?.invoke() },
            onOpenStore = onOpenStore,
            onCorrectLot = {
                adjustmentProductId = id
                selectedProduct = null
                viewModel.select(null)
                adjustmentForm = true
            },
            onConvertToService = {
                productById[id]?.let(viewModel::convertToService)
                selectedProduct = null
                viewModel.select(null)
            }
        )
    }
    if (showGlobalMovements) {
        InventoryGlobalMovementsDialog(
            movements = allMovements,
            productNames = names,
            onDismiss = { showGlobalMovements = false }
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
private fun InventoryGlobalMovementsDialog(
    movements: List<InventoryMovement>,
    productNames: Map<UUID, String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Movimientos de inventario") },
        text = {
            if (movements.isEmpty()) {
                Text("Todavía no hay movimientos registrados.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(movements, key = { it.id }) { movement ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surfaceVariant)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(productNames[movement.productId] ?: "Producto", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(movement.type.label(), color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Text(movement.createdAt.toString().replace('T', ' ').take(16), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${if (movement.quantity > 0) "+" else ""}${movement.quantity} · saldo ${movement.newQuantity}", color = if (movement.quantity > 0) BSPOSTheme.colors.success else BSPOSTheme.colors.error, fontWeight = FontWeight.Bold)
                                    Text(MoneyUtils.formatCents(movement.totalCost, LocalCurrency.current), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun ImportChoiceButton(
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean = false
) {
    val container = if (primary) BSPOSTheme.colors.primary else BSPOSTheme.colors.surface
    val titleColor = if (primary) BSPOSTheme.colors.textOnPrimary else BSPOSTheme.colors.textPrimary
    val descriptionColor = if (primary) BSPOSTheme.colors.textOnPrimary.copy(alpha = 0.8f) else BSPOSTheme.colors.textSecondary
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = titleColor)
            Spacer(Modifier.height(3.dp))
            Text(description, color = descriptionColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InventoryProductDetailDialog(
    product: Product?,
    quantity: Long,
    movements: List<InventoryMovement>,
    onDismiss: () -> Unit,
    onReceive: (() -> Unit)?,
    onEdit: () -> Unit,
    onOpenStore: ((Product) -> Unit)?,
    onCorrectLot: (() -> Unit)?,
    onConvertToService: (() -> Unit)?
) {
    if (product == null) return
    var tab by remember(product.id) { mutableStateOf(0) }
    val inventoryValue = quantity * product.averageCost
    val unitProfit = product.salePrice - product.averageCost
    val margin = if (product.salePrice > 0L) ((unitProfit.toDouble() / product.salePrice) * 100.0).toInt().coerceAtLeast(0) else 0
    val wholesale = product.wholesalePrice ?: product.salePrice
    val wholesaleProfit = wholesale - product.averageCost
    val positiveMovements = movements.filter { it.quantity > 0L }.sortedByDescending { it.createdAt }
    val saleMovements = movements.filter { it.type == InventoryMovementType.SALE || it.type == InventoryMovementType.ROUTE_SALE }.sortedByDescending { it.createdAt }
    var convertToServiceConfirm by remember(product.id) { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = BSPOSTheme.colors.background) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text("‹  Volver") }
                        Spacer(Modifier.weight(1f))
                        Text("Inventario", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) {
                            if (!product.imagePath.isNullOrBlank()) AsyncImage(product.imagePath, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(44.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Surface(shape = RoundedCornerShape(50), color = BSPOSTheme.colors.success.copy(alpha = .12f)) {
                                    Text(if (product.isActive) "PUBLICADO" else "ARCHIVADO", color = BSPOSTheme.colors.success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                }
                            }
                            Text("SKU ${product.internalCode}", color = BSPOSTheme.colors.textSecondary)
                            product.barcode?.takeIf { it.isNotBlank() }?.let { Text("Código de barras $it", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Editar") }
                        Button(onClick = { onReceive?.invoke() }, enabled = onReceive != null, modifier = Modifier.weight(1.5f), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Añadir inventario", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    OutlinedButton(
                        onClick = { onOpenStore?.invoke(product) },
                        enabled = onOpenStore != null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ver en tienda")
                    }
                    if (!product.remoteIsCombo && product.remoteSaleUnit != "service") {
                        OutlinedButton(
                            onClick = { convertToServiceConfirm = true },
                            enabled = onConvertToService != null,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Convertir a servicio") }
                    }
                }
                item {
                    InventoryDetailMetrics(quantity, inventoryValue, product.averageCost, movements.count { it.type == InventoryMovementType.SALE && it.createdAt.isAfter(java.time.Instant.now().minusSeconds(30L * 24L * 60L * 60L)) })
                }
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Detalle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(50), color = BSPOSTheme.colors.primaryLight) { Text("Predeterminado", color = BSPOSTheme.colors.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall) }
                                Spacer(Modifier.width(10.dp))
                                Text(MoneyUtils.formatCents(product.salePrice, LocalCurrency.current), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                InventoryDetailPrice("Costo", product.averageCost, Modifier.weight(1f))
                                InventoryDetailText("Ganancia", "${MoneyUtils.formatCents(unitProfit, LocalCurrency.current)} · $margin%", Modifier.weight(1f), BSPOSTheme.colors.success)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = BSPOSTheme.colors.outline)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                Column(Modifier.weight(1f)) { Text("Mayoreo", fontWeight = FontWeight.Bold); Text(MoneyUtils.formatCents(wholesale, LocalCurrency.current), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold) }
                                InventoryDetailText("Ganancia", "${MoneyUtils.formatCents(wholesaleProfit, LocalCurrency.current)} · ${if (wholesale > 0L) ((wholesaleProfit.toDouble() / wholesale) * 100.0).toInt().coerceAtLeast(0) else 0}%", Modifier.weight(1f), BSPOSTheme.colors.success)
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        product.description?.takeIf { it.isNotBlank() }?.let {
                            Text("Descripción", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text(it, color = BSPOSTheme.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                            Spacer(Modifier.height(14.dp))
                        }
                        Text("Información del producto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        InventoryInfoRow("Existencia mínima", "${product.minimumStock} unidades")
                        InventoryInfoRow("Último costo", MoneyUtils.formatCents(product.lastPurchaseCost, LocalCurrency.current))
                        InventoryInfoRow("Unidad de venta", product.remoteSaleUnit ?: "Unidad")
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        InventoryDetailTab("LOTES", tab == 0) { tab = 0 }
                        InventoryDetailTab("MOVIMIENTOS", tab == 1) { tab = 1 }
                        InventoryDetailTab("VENTAS", tab == 2) { tab = 2 }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = BSPOSTheme.colors.outline)
                }
                when (tab) {
                    0 -> if (positiveMovements.isEmpty()) item { EmptyInventoryState("No hay lotes registrados", "Añade inventario para ver aquí cada entrada y su costo.") } else items(positiveMovements, key = { it.id }) { movement -> InventoryLotCard(movement, onCorrectLot) }
                    1 -> if (movements.isEmpty()) item { EmptyInventoryState("No hay movimientos", "Las entradas, ventas y ajustes aparecerán aquí.") } else items(movements, key = { it.id }) { movement -> InventoryMovementCard(movement) }
                    else -> if (saleMovements.isEmpty()) {
                        item { EmptyInventoryState("Sin ventas registradas", "Las ventas de este producto aparecerán aquí cuando se registren.") }
                    } else {
                        items(saleMovements, key = { it.id }) { movement -> InventoryMovementCard(movement) }
                    }
                }
            }
        }
    }
    if (convertToServiceConfirm) {
        AlertDialog(
            onDismissRequest = { convertToServiceConfirm = false },
            title = { Text("Convertir a servicio") },
            text = { Text("Este producto dejará de descontar inventario y se venderá como un servicio. Sus movimientos históricos se conservarán.") },
            confirmButton = {
                TextButton(onClick = { convertToServiceConfirm = false; onConvertToService?.invoke() }) { Text("Convertir") }
            },
            dismissButton = { TextButton(onClick = { convertToServiceConfirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun InventoryDetailMetrics(quantity: Long, inventoryValue: Long, averageCost: Long, sales30d: Int) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                InventoryMetricCell("EXISTENCIA", "$quantity", Modifier.weight(1f), BSPOSTheme.colors.primary)
                InventoryMetricCell("VALOR INVENTARIO", MoneyUtils.formatCents(inventoryValue, LocalCurrency.current), Modifier.weight(1f), BSPOSTheme.colors.textPrimary)
            }
            HorizontalDivider(color = BSPOSTheme.colors.outline)
            Row(Modifier.fillMaxWidth()) {
                InventoryMetricCell("COSTO FIFO PROM.", MoneyUtils.formatCents(averageCost, LocalCurrency.current), Modifier.weight(1f), BSPOSTheme.colors.textPrimary)
                InventoryMetricCell("VENTAS 30D", sales30d.toString(), Modifier.weight(1f), BSPOSTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun InventoryMetricCell(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    Column(modifier.padding(16.dp)) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InventoryDetailPrice(label: String, cents: Long, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Text(MoneyUtils.formatCents(cents, LocalCurrency.current), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun InventoryDetailText(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color) {
    Column(modifier) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InventoryInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BSPOSTheme.colors.textSecondary)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InventoryDetailTab(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = if (selected) BSPOSTheme.colors.primary else BSPOSTheme.colors.textSecondary, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium)
    }
}

@Composable
private fun InventoryLotCard(movement: InventoryMovement, onCorrect: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(movement.createdAt.toString().substringBefore("T"), color = BSPOSTheme.colors.textSecondary)
                Text(movement.type.label(), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${movement.quantity}/${movement.newQuantity}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(MoneyUtils.formatCents(movement.totalCost, LocalCurrency.current), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            Text("Costo unitario  ${MoneyUtils.formatCents(movement.unitCost, LocalCurrency.current)}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            onCorrect?.let {
                TextButton(onClick = it, modifier = Modifier.align(Alignment.End)) {
                    Text("Corregir")
                }
            }
        }
    }
}

@Composable
private fun InventoryMovementCard(movement: InventoryMovement) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(movement.type.label(), fontWeight = FontWeight.Bold)
                Text(movement.createdAt.toString().substringBefore("T"), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${if (movement.quantity >= 0) "+" else ""}${movement.quantity}", color = if (movement.quantity >= 0) BSPOSTheme.colors.success else BSPOSTheme.colors.error, fontWeight = FontWeight.ExtraBold)
                Text("Saldo ${movement.newQuantity}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InventorySummaryRow(capitalAtCost: Long, productCount: Int, productsWithStock: Int, totalUnits: Long, lowStock: Int, exhausted: Int, averageMargin: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val metrics = listOf(
            InventoryMetric("Capital al costo", MoneyUtils.formatCents(capitalAtCost, LocalCurrency.current), "Valor de tu existencia", BSPOSTheme.colors.primary),
            InventoryMetric("Productos", productCount.toString(), "$totalUnits unidades · $productsWithStock con existencia", BSPOSTheme.colors.textPrimary),
            InventoryMetric("Nivel bajo", lowStock.toString(), "$exhausted agotados", if (lowStock > 0) BSPOSTheme.colors.warning else BSPOSTheme.colors.success),
            InventoryMetric("Margen prom.", "$averageMargin%", "Sobre el precio de venta", BSPOSTheme.colors.success)
        )
        if (maxWidth < 540.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { metric -> InventoryMetricCard(metric, Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { metrics.forEach { metric -> InventoryMetricCard(metric, Modifier.weight(1f)) } }
        }
    }
}

private data class InventoryMetric(val label: String, val value: String, val subtitle: String, val color: androidx.compose.ui.graphics.Color)

@Composable
private fun InventoryMetricCard(metric: InventoryMetric, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(metric.label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(metric.value, color = metric.color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metric.subtitle, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun averageMargin(products: List<Product>): Int {
    val margins = products.mapNotNull { product ->
        if (product.salePrice > 0L && product.averageCost >= 0L) {
            (((product.salePrice - product.averageCost).toDouble() / product.salePrice.toDouble()) * 100.0).toInt().coerceAtLeast(0)
        } else null
    }
    return if (margins.isEmpty()) 0 else margins.average().toInt()
}

@Composable
private fun InventoryTabChip(label: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label $count", fontWeight = FontWeight.Bold, maxLines = 1) },
        modifier = modifier,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = BSPOSTheme.colors.secondaryNavy,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
            containerColor = BSPOSTheme.colors.surface
        )
    )
}

@Composable
private fun EmptyInventoryState(title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
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
private fun StockCard(product: Product, quantity: Long, onClick: () -> Unit, onReceive: (() -> Unit)?, onEdit: () -> Unit) {
    val color = when {
        quantity <= 0 -> BSPOSTheme.colors.error
        product.minimumStock > 0L && quantity <= product.minimumStock -> BSPOSTheme.colors.warning
        else -> BSPOSTheme.colors.success
    }
    var menuOpen by remember(product.id) { mutableStateOf(false) }
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) {
                if (!product.imagePath.isNullOrBlank()) AsyncImage(product.imagePath, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (product.remoteIsCombo) {
                    Text("Combo · stock calculado por componentes", color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("SKU ${product.internalCode}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(76.dp).height(5.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = .18f))) {
                        Box(Modifier.fillMaxWidth((quantity.toFloat() / product.minimumStock.coerceAtLeast(10L)).coerceIn(.08f, 1f)).height(5.dp).background(color))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("$quantity uds.", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(MoneyUtils.formatCents(product.salePrice, LocalCurrency.current), fontWeight = FontWeight.ExtraBold)
                Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f)) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (quantity <= 0) "Agotado" else if (product.minimumStock > 0L && quantity <= product.minimumStock) "Bajo" else "OK", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Acciones") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Ver lotes / movimientos") }, onClick = { menuOpen = false; onClick() })
                    DropdownMenuItem(text = { Text("Añadir inventario") }, enabled = onReceive != null, onClick = { menuOpen = false; onReceive?.invoke() })
                    DropdownMenuItem(text = { Text("Editar producto") }, onClick = { menuOpen = false; onEdit() })
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
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit); error = null }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = error != null)
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
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit); error = false }, label = { Text("Cantidad contada (puede ser 0)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
private fun InventoryAdjustmentForm(products: List<Product>, reasons: List<InventoryAdjustmentReason>, initialProductId: UUID? = null, onSave: (Product, InventoryMovementType, Long, Long, InventoryAdjustmentReason, String) -> Unit, onDismiss: () -> Unit) {
    var product by remember(products, initialProductId) { mutableStateOf(products.firstOrNull { it.id == initialProductId } ?: products.first()) }
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
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit); error = null }, label = { Text("Cantidad absoluta") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = error != null)
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
