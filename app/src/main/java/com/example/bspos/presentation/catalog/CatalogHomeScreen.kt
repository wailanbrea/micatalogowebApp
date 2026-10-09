package com.example.bspos.presentation.catalog

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.ProductComboComponent
import com.example.bspos.domain.model.UnitOfMeasure
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import com.example.bspos.domain.usecase.ProductInput
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.common.BSPOSModalBottomSheet
import com.example.bspos.presentation.common.DialogScrollableColumn
import coil.compose.AsyncImage
import java.util.Locale
import java.util.UUID

@Composable
fun CatalogHomeScreen(
    isTablet: Boolean,
    presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    showCost: Boolean = false,
    showProductCode: Boolean = true,
    photosOnly: Boolean = false,
    initialDecantMode: Boolean = false,
    initialServiceMode: Boolean = false,
    startWithForm: Boolean = false,
    viewModel: ProductCatalogViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showForm by remember { mutableStateOf(startWithForm || ((initialDecantMode || initialServiceMode) && startWithForm)) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deleteTarget by remember { mutableStateOf<Product?>(null) }
    var decantOverlay by remember { mutableStateOf<String?>(null) }
    var decantFocusProduct by remember { mutableStateOf<Product?>(null) }
    var preselectedSourceRemoteId by remember { mutableStateOf<String?>(null) }
    var photoFilter by remember { mutableStateOf("Pendientes") }
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val units by viewModel.units.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val message by viewModel.message.collectAsState()
    val shops by viewModel.shops.collectAsState()
    val activeShopId by viewModel.activeShopId.collectAsState()
    val sourceProducts = products.filter {
        it.isActive && it.deletedAt == null && it.remoteShopId != null && it.remoteProductId != null &&
            it.remoteSaleUnit in setOf("bottle", "ml") && it.remoteVolumeMl != null
    }
    val sourceByRemoteId = sourceProducts.associateBy { it.remoteProductId }
    val comboComponentProducts = products.filter {
        it.isActive && it.deletedAt == null && it.remoteShopId != null && it.remoteProductId != null &&
            !it.remoteIsCombo && it.remoteSaleUnit !in setOf("service", "decant")
    }
    val formReady = categories.isNotEmpty() && units.isNotEmpty()
    var query by remember { mutableStateOf("") }
    val quantities = stock.associate { it.productId to it.quantity }
    val categoryNames = categories.associate { it.id to it.name }
    val filtered = products.filter {
        it.isActive && it.deletedAt == null &&
            (!initialDecantMode || it.remoteSaleUnit == "decant") &&
            (!initialServiceMode || it.remoteSaleUnit == "service") &&
            (!photosOnly || when (photoFilter) {
                "Con foto" -> !it.imagePath.isNullOrBlank()
                "Todos" -> true
                else -> it.imagePath.isNullOrBlank()
            }) &&
            (it.name.contains(query, true) || it.internalCode.contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(if (isTablet) 28.dp else 20.dp)) {
        if (photosOnly) {
            Text("Gestión de fotografías", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(14.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = BSPOSTheme.colors.primary)
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text(presentation.catalogSearchPlaceholder) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (initialDecantMode) {
            val availableMl = sourceProducts.sumOf { it.decantPoolMl() ?: 0 }
            val openBottleCount = sourceProducts.count { source ->
                source.remoteOpenedBottles?.let { it > 0 } ?: run {
                    val total = source.remoteVolumeMl
                    val available = source.remoteAvailableMl
                    total != null && available != null && available in 1 until total
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecantMetricCard("Listos", quantities.filterKeys { id -> filtered.any { it.id == id } }.values.sum().toString(), Modifier.weight(1f))
                DecantMetricCard("Botellas abiertas", openBottleCount.toString(), Modifier.weight(1f))
                DecantMetricCard("Ml disponibles", availableMl.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ decantOverlay = "report" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Reporte") }
                OutlinedButton({ decantOverlay = "vials" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Frascos") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ decantOverlay = "open" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Abrir botella") }
                Button({ decantOverlay = "prepare" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Preparar decant") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (photosOnly) "$photoFilter · ${filtered.size} producto(s)" else "${filtered.size} productos",
                color = BSPOSTheme.colors.textSecondary,
                style = MaterialTheme.typography.labelLarge
            )
            if (!photosOnly && !initialDecantMode) {
                Button({ showForm = true }, enabled = categories.any { it.isActive } && units.any { it.isActive }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text(if (initialServiceMode) "Nuevo servicio" else "Nuevo producto") }
            }
        }
        if (photosOnly) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Pendientes", "Con foto", "Todos").forEach { option ->
                    FilterChip(
                        selected = photoFilter == option,
                        onClick = { photoFilter = option },
                        label = { Text(option, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BSPOSTheme.colors.secondaryNavy,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                            containerColor = BSPOSTheme.colors.surface
                        )
                    )
                }
            }
            val missingPhotoProducts = filtered.filter { it.imagePath.isNullOrBlank() }
            if (missingPhotoProducts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val search = missingPhotoProducts.joinToString(", ") { it.name }
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=${Uri.encode(search)}")))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Buscar los ${missingPhotoProducts.size} en Google")
                }
            }
        }
        if (initialDecantMode && !formReady) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BSPOSTheme.colors.warning.copy(alpha = .12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.warning.copy(alpha = .35f))
            ) {
                Text(
                    "Para crear una presentación decant necesitas al menos una categoría y una unidad activa. Créala desde Catálogo > Categorías y unidades y vuelve a Decants.",
                    modifier = Modifier.padding(16.dp),
                    color = BSPOSTheme.colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        query.isNotBlank() -> "Sin coincidencias"
                        photosOnly -> if (photoFilter == "Pendientes") "Todos tus productos tienen foto" else "No hay productos en este filtro"
                        else -> presentation.catalogEmptyMessage
                    },
                    color = BSPOSTheme.colors.textSecondary
                )
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(if (isTablet) 4 else 2), Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { product -> ProductCard(product, quantities[product.id] ?: 0L, categoryNames[product.categoryId], presentation.catalogShowStock, showCost, showProductCode,
                    { if (viewModel.canEdit(product)) editingProduct = product else viewModel.reportReadOnly() },
                    { if (viewModel.canEdit(product)) deleteTarget = product else viewModel.reportReadOnly() },
                    decantMode = initialDecantMode,
                    decantSource = sourceByRemoteId[product.remoteSourceProductId],
                    onPrepare = { decantFocusProduct = product; decantOverlay = "prepare" },
                    photosOnly = photosOnly,
                    onImageSearch = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=${Uri.encode(product.name)}")))
                    }) }
            }
        }
    }
    if ((showForm || editingProduct != null) && formReady) ProductForm(
        current = editingProduct,
        currentQuantity = quantities[editingProduct?.id] ?: 0L,
        categories = categories.filter { it.isActive },
        units = units.filter { it.isActive },
        presentation = presentation,
        showCost = showCost,
        showInventoryFields = presentation.inventoryEnabled,
        showProductCode = showProductCode,
        onSave = { input, initialQuantity -> if (editingProduct == null) viewModel.add(input, initialQuantity) else viewModel.update(editingProduct!!, input); showForm = false; editingProduct = null; preselectedSourceRemoteId = null },
        onDismiss = { showForm = false; editingProduct = null; preselectedSourceRemoteId = null },
        shops = shops,
        activeShopId = activeShopId,
        decantMode = initialDecantMode,
        serviceMode = initialServiceMode,
        sourceProducts = sourceProducts,
        initialSourceRemoteId = preselectedSourceRemoteId,
        comboComponentProducts = comboComponentProducts
    )
    deleteTarget?.let { product ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Eliminar producto") }, text = { Text("¿Eliminar ${product.name}? El producto dejará de aparecer en el catálogo.") }, confirmButton = { TextButton({ viewModel.delete(product); deleteTarget = null }) { Text("Eliminar", color = BSPOSTheme.colors.error) } }, dismissButton = { TextButton({ deleteTarget = null }) { Text("Cancelar") } })
    }
    message?.let { text -> AlertDialog(onDismissRequest = viewModel::consumeMessage, title = { Text("No se pudo completar") }, text = { Text(text) }, confirmButton = { TextButton(viewModel::consumeMessage) { Text("Cerrar") } }) }
    if (initialDecantMode) {
        when (decantOverlay) {
            "report" -> DecantReportDialog(
                products = filtered,
                quantities = quantities,
                sourceProducts = sourceProducts,
                onDismiss = { decantOverlay = null }
            )
            "vials" -> DecantVialsDialog(
                products = filtered,
                quantities = quantities,
                onDismiss = { decantOverlay = null }
            )
            "open" -> DecantBottleDialog(
                sourceProducts = sourceProducts,
                quantities = quantities,
                onOpen = { source, quantity ->
                    viewModel.openBottle(source, quantity, quantities[source.id] ?: 0L)
                    decantOverlay = null
                },
                onDismiss = { decantOverlay = null }
            )
            "prepare" -> DecantPrepareDialog(
                products = filtered,
                quantities = quantities,
                sourceProducts = sourceProducts,
                focusProduct = decantFocusProduct,
                onCreatePresentation = { sourceRemoteId ->
                    decantOverlay = null
                    preselectedSourceRemoteId = sourceRemoteId
                    showForm = true
                },
                onDismiss = { decantOverlay = null; decantFocusProduct = null }
            )
        }
    }
}

@Composable
private fun DecantMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
        }
    }
}

private fun Product.decantPoolMl(): Int? = remoteReservedDecantMl ?: remoteAvailableMl

@Composable
private fun ProductCard(product: Product, quantity: Long, category: String?, showStock: Boolean, showCost: Boolean, showProductCode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, decantMode: Boolean = false, decantSource: Product? = null, onPrepare: (() -> Unit)? = null, photosOnly: Boolean = false, onImageSearch: (() -> Unit)? = null) {
    val statusColor = when {
        quantity <= 0 -> BSPOSTheme.colors.error
        product.minimumStock > 0 && quantity <= product.minimumStock -> BSPOSTheme.colors.warning
        else -> BSPOSTheme.colors.success
    }
    val statusText = when {
        quantity <= 0 -> "Agotado"
        product.minimumStock > 0 && quantity <= product.minimumStock -> "Stock bajo"
        else -> "En stock"
    }
    Card(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(16.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) { if (product.imagePath != null) AsyncImage(product.imagePath, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(42.dp), tint = BSPOSTheme.colors.primary) }
            if (photosOnly && product.imagePath.isNullOrBlank() && onImageSearch != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onImageSearch, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Buscar en Google", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(product.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            if (decantMode) {
                Text("Decant · ${product.remoteVolumeMl ?: "—"} ml", color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Fuente: ${decantSource?.name ?: "Pendiente de sincronizar"}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Reservado: ${decantSource?.decantPoolMl() ?: "—"} ml para decants", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            } else if (showProductCode) Text(category ?: product.internalCode, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            Text("Venta: ${money(product.salePrice)}", color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            if (showCost) Text("Compra: ${money(product.lastPurchaseCost)}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            if (showStock) { Spacer(Modifier.height(9.dp)); Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = .12f)) { Text("$statusText ($quantity)", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }
            if (decantMode && onPrepare != null) {
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onPrepare, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(vertical = 7.dp)) { Text("Preparar") }
            }
        }
    }
}

@Composable
private fun DecantReportDialog(products: List<Product>, quantities: Map<UUID, Long>, sourceProducts: List<Product>, onDismiss: () -> Unit) {
    val ready = products.sumOf { quantities[it.id] ?: 0L }
    val mlReady = products.sumOf { (quantities[it.id] ?: 0L) * (it.remoteVolumeMl ?: 0) }
    val mlAvailable = sourceProducts.sumOf { it.decantPoolMl() ?: 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reporte de decants", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Resumen calculado desde las botellas fuente. Las ventas descuentan ml automáticamente.", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                DecantSummaryRow("Presentaciones", products.size.toString())
                DecantSummaryRow("Frascos listos", ready.toString())
                DecantSummaryRow("Ml comprometidos en frascos", "$mlReady ml")
                DecantSummaryRow("Ml disponibles en botellas", "$mlAvailable ml")
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun DecantVialsDialog(products: List<Product>, quantities: Map<UUID, Long>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frascos y presentaciones", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MiCatalogo calcula los frascos vendibles con el stock real de la botella fuente para evitar duplicar inventario.", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                products.forEach { product ->
                    Surface(shape = RoundedCornerShape(12.dp), color = BSPOSTheme.colors.primaryLight, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${product.remoteVolumeMl ?: "—"} ml por frasco", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                            }
                            Text("${quantities[product.id] ?: 0} listos", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.primary)
                        }
                    }
                }
                if (products.isEmpty()) Text("Todavía no hay presentaciones creadas.", color = BSPOSTheme.colors.textSecondary)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecantBottleDialog(
    sourceProducts: List<Product>,
    quantities: Map<UUID, Long>,
    onOpen: (Product, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var quantityText by remember { mutableStateOf("1") }
    var expanded by remember { mutableStateOf(false) }
    val selected = sourceProducts.getOrNull(selectedIndex)
    val sealed = selected?.let { quantities[it.id] ?: 0L } ?: 0L
    val quantity = quantityText.toIntOrNull() ?: 0
    BSPOSModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Abrir botella", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Prepara ml para vender decants sin duplicar el inventario.", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.primaryLight) {
                Text("El costo permanece en el lote de la botella y la operación se sincroniza con MiCatalogo.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textPrimary)
            }
            if (sourceProducts.isEmpty()) {
                Surface(shape = RoundedCornerShape(14.dp), color = BSPOSTheme.colors.warning.copy(alpha = .12f), modifier = Modifier.fillMaxWidth()) {
                    Text("No hay botellas fuente sincronizadas. Crea primero una botella completa con costo, volumen e inventario.", Modifier.padding(14.dp), color = BSPOSTheme.colors.textPrimary)
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Entendido") }
            } else {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(selected?.let { "${it.name} · ${it.remoteVolumeMl ?: "—"} ml" } ?: "Selecciona una botella", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("⌄", fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sourceProducts.forEachIndexed { index, source ->
                            DropdownMenuItem(
                                text = { Text("${source.name} · ${quantities[source.id] ?: 0} sellada(s)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { selectedIndex = index; expanded = false; quantityText = "1" }
                            )
                        }
                    }
                }
                selected?.let { source ->
                    Surface(shape = RoundedCornerShape(14.dp), color = BSPOSTheme.colors.primaryLight, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            DecantSummaryRow("Botellas selladas", sealed.toString())
                            DecantSummaryRow("Botellas abiertas", (source.remoteOpenedBottles ?: 0).toString())
                            DecantSummaryRow("Ml reservados", "${source.decantPoolMl() ?: 0} ml")
                        }
                    }
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Botellas a abrir") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = quantity <= 0 || quantity.toLong() > sealed,
                        supportingText = { Text("Máximo disponible: $sealed") }
                    )
                    Text("Al confirmar, las botellas dejan de estar disponibles para venderse completas y sus ml quedan compartidos con los decants.", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.primary)
                    Button(
                        onClick = { onOpen(source, quantity) },
                        enabled = quantity > 0 && quantity.toLong() <= sealed,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Abrir botella") }
                }
            }
        }
    }
}

@Composable
private fun DecantPrepareDialog(products: List<Product>, quantities: Map<UUID, Long>, sourceProducts: List<Product>, focusProduct: Product?, onCreatePresentation: (String?) -> Unit, onDismiss: () -> Unit) {
    val selected = focusProduct ?: products.firstOrNull()
    val source = sourceProducts.firstOrNull { it.remoteProductId == selected?.remoteSourceProductId }
    val size = selected?.remoteVolumeMl ?: 0
    val stock = selected?.let { quantities[it.id] ?: 0L } ?: 0L
    val available = source?.decantPoolMl() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preparar decants", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Vista previa de preparación", style = MaterialTheme.typography.labelLarge, color = BSPOSTheme.colors.primary)
                if (selected == null) {
                    Text("Crea una presentación decant desde una botella fuente para comenzar.", color = BSPOSTheme.colors.textSecondary)
                } else {
                    Surface(shape = RoundedCornerShape(14.dp), color = BSPOSTheme.colors.primaryLight, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(selected.name, fontWeight = FontWeight.Bold)
                            Text("${size} ml · $stock listos para vender", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                            Text("Fuente: ${source?.name ?: "pendiente"}", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                             Text("Pool reservado: ${available} ml", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                        }
                    }
                    Text("La disponibilidad se calcula automáticamente. Preparar aquí no crea stock paralelo ni cambia el costo de la botella.", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (selected == null) onCreatePresentation(null) else onDismiss() }) { Text(if (selected == null) "Crear presentación" else "Cerrar") }
        },
        dismissButton = { if (selected != null) TextButton(onClick = { onCreatePresentation(source?.remoteProductId) }) { Text("Nueva presentación") } }
    )
}

@Composable
private fun DecantSummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
    }
}

@Composable
private fun ProductForm(current: Product?, currentQuantity: Long, categories: List<Category>, units: List<UnitOfMeasure>, presentation: MiCatalogoBusinessPresentation, showCost: Boolean, showInventoryFields: Boolean, showProductCode: Boolean, onSave: (ProductInput, Long) -> Unit, onDismiss: () -> Unit, shops: List<com.example.bspos.domain.model.MiCatalogoShop>, activeShopId: String?, decantMode: Boolean = false, serviceMode: Boolean = false, sourceProducts: List<Product> = emptyList(), initialSourceRemoteId: String? = null, comboComponentProducts: List<Product> = emptyList()) {
    val context = LocalContext.current
    var name by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var description by remember(current?.id) { mutableStateOf(current?.description.orEmpty()) }
    var code by remember(current?.id) { mutableStateOf(current?.internalCode.orEmpty()) }
    var price by remember(current?.id) { mutableStateOf(current?.salePrice?.let { java.math.BigDecimal(it).movePointLeft(2).toPlainString() }.orEmpty()) }
    var priceChanged by remember(current?.id) { mutableStateOf(false) }
    var wholesalePrice by remember(current?.id) { mutableStateOf(current?.wholesalePrice?.let { java.math.BigDecimal(it).movePointLeft(2).toPlainString() }.orEmpty()) }
    var purchasePrice by remember(current?.id) { mutableStateOf(current?.lastPurchaseCost?.let { java.math.BigDecimal(it).movePointLeft(2).toPlainString() }.orEmpty()) }
    var purchaseChanged by remember(current?.id) { mutableStateOf(false) }
    var initialQuantity by remember(current?.id) { mutableStateOf(if (current == null) "0" else currentQuantity.toString()) }
    var minimumStock by remember(current?.id) { mutableStateOf((current?.minimumStock ?: 0L).toString()) }
    var priceError by remember { mutableStateOf(false) }
    var category by remember(current?.id) { mutableStateOf(categories.firstOrNull { it.id == current?.categoryId } ?: categories.first()) }
    var unit by remember(current?.id) { mutableStateOf(units.firstOrNull { it.id == current?.unitId } ?: units.first()) }
    var categoryOpen by remember { mutableStateOf(false) }
    var unitOpen by remember { mutableStateOf(false) }
    var imagePath by remember(current?.id) { mutableStateOf(current?.imagePath) }
    var shopId by remember(current?.id, shops, activeShopId) {
        mutableStateOf(current?.remoteShopId ?: shops.singleOrNull()?.id ?: activeShopId)
    }
    var shopOpen by remember { mutableStateOf(false) }
    val effectiveDecantMode = decantMode || current?.remoteSaleUnit == "decant"
    var volumeMl by remember(current?.id, effectiveDecantMode) { mutableStateOf(current?.remoteVolumeMl?.toString().orEmpty()) }
    var saleUnit by remember(current?.id, effectiveDecantMode) {
        mutableStateOf(if (effectiveDecantMode) "decant" else if (serviceMode && current == null) "service" else (current?.remoteSaleUnit ?: "unit"))
    }
    var saleUnitOpen by remember { mutableStateOf(false) }
    var sourceProduct by remember(current?.id, sourceProducts, initialSourceRemoteId) { mutableStateOf(sourceProducts.firstOrNull { it.remoteProductId == (current?.remoteSourceProductId ?: initialSourceRemoteId) }) }
    var sourceOpen by remember { mutableStateOf(false) }
    val availableComboComponents = comboComponentProducts.filter { it.remoteShopId == shopId && it.id != current?.id }
    var comboMode by remember(current?.id) { mutableStateOf(current?.remoteIsCombo == true) }
    var comboRows by remember(current?.id, comboComponentProducts) {
        mutableStateOf<List<ComboDraft>>(current?.remoteComboItems?.map { ComboDraft(it.productId, it.quantity.toString()) } ?: emptyList())
    }
    var comboMenuRow by remember { mutableStateOf<Int?>(null) }
    var comboError by remember { mutableStateOf(false) }
    var published by remember(current?.id) { mutableStateOf(current?.isActive ?: true) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        imagePath = uri.toString()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (effectiveDecantMode) "Nueva presentación decant" else if (saleUnit == "service") if (current == null) "Nuevo servicio" else "Editar servicio" else if (current == null) "Nuevo producto" else "Editar producto") }, text = {
        DialogScrollableColumn {
            if (effectiveDecantMode) {
                Text("La presentación comparte el inventario de su producto fuente. Cada venta descuenta los ml correspondientes.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Box {
                    TextButton({ sourceOpen = true }) { Text("Fuente: ${sourceProduct?.name ?: "Seleccionar botella o producto en ml"}") }
                    DropdownMenu(sourceOpen, { sourceOpen = false }) {
                        sourceProducts.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (showCost) "${item.remoteVolumeMl} ml · costo ${money(item.lastPurchaseCost)}"
                                            else "${item.remoteVolumeMl} ml · presentación disponible",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BSPOSTheme.colors.textSecondary
                                        )
                                    }
                                },
                                onClick = { sourceProduct = item; sourceOpen = false }
                            )
                        }
                    }
                }
                sourceProduct?.let { source ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = BSPOSTheme.colors.primaryLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.primary.copy(alpha = .25f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Botella fuente identificada", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                            Text(source.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${source.remoteVolumeMl ?: 0} ml de origen", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                                if (showCost) {
                                    Text("Costo ${money(source.lastPurchaseCost)}", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                                }
                            }
                            Text(
                                source.decantPoolMl()?.let { "$it ml reservados para decants" } ?: "Pool de ml pendiente de sincronizar",
                                style = MaterialTheme.typography.labelSmall,
                                color = BSPOSTheme.colors.textSecondary
                            )
                        }
                    }
                }
                if (sourceProducts.isEmpty()) Text("Primero sincroniza o crea una botella fuente con volumen y control de inventario.", color = BSPOSTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(volumeMl, { volumeMl = it.filter(Char::isDigit) }, label = { Text("Tamaño del decant (ml)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Máximo: ${sourceProduct?.remoteVolumeMl ?: "—"} ml") })
            }
            if (current == null) Box {
                val selectedShop = shops.find { it.id == shopId }
                val destinationLabel = selectedShop?.name
                    ?: if (shopId != null && shopId == activeShopId) "Tienda activa" else "Solo este dispositivo"
                TextButton({ shopOpen = true }) { Text("Destino: $destinationLabel") }
                DropdownMenu(shopOpen, { shopOpen = false }) {
                    DropdownMenuItem({ Text("Solo este dispositivo") }, { shopId = null; shopOpen = false })
                    if (activeShopId != null && shops.none { it.id == activeShopId }) {
                        DropdownMenuItem({ Text("Tienda activa") }, { shopId = activeShopId; shopOpen = false })
                    }
                    shops.forEach { shop -> DropdownMenuItem({ Text(shop.name) }, { shopId = shop.id; shopOpen = false }) }
                }
            } else if (current.remoteShopId != null) Text("Producto vinculado a MiCatalogo: se enviarán los cambios al sincronizar.", style = MaterialTheme.typography.bodySmall)
            if (imagePath != null) {
                AsyncImage(imagePath, "Foto del producto", Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(42.dp)) }
            }
            OutlinedButton({ imageLauncher.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(if (imagePath == null) "Subir foto" else "Cambiar foto") }
            if (shopId != null) Text("La foto se enviará como principal. Se conservan las anteriores y se respeta el límite de tu plan.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it }, label = { Text(presentation.term("product", "Nombre")) })
            if (serviceMode || current?.remoteSaleUnit == "service") {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descripción (opcional)") },
                    supportingText = { Text("Se muestra en la tienda y ayuda al cliente a entender el servicio.") },
                    minLines = 3
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (published) BSPOSTheme.colors.successLight else BSPOSTheme.colors.warningLight
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (published) "Publicado en la tienda" else "Borrador", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (published) "Los clientes pueden verlo y solicitarlo."
                                else "Solo queda disponible para revisión interna.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BSPOSTheme.colors.textSecondary
                            )
                        }
                        Switch(checked = published, onCheckedChange = { published = it })
                    }
                }
            }
            if (!effectiveDecantMode && !serviceMode && current?.remoteSaleUnit != "service" && shopId != null && availableComboComponents.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (comboMode) BSPOSTheme.colors.primaryLight else BSPOSTheme.colors.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Vender como combo", fontWeight = FontWeight.Bold)
                                Text("El stock se calcula con los componentes y se descuenta al vender.", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
                            }
                            Switch(checked = comboMode, onCheckedChange = {
                                comboMode = it
                                if (it) saleUnit = "unit"
                                comboError = false
                            })
                        }
                        if (comboMode) {
                            Spacer(Modifier.height(8.dp))
                            comboRows.forEachIndexed { index, row ->
                                val selected = availableComboComponents.firstOrNull { it.remoteProductId == row.productId }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.weight(1f)) {
                                        TextButton(onClick = { comboMenuRow = index }, modifier = Modifier.fillMaxWidth()) {
                                            Text(selected?.name ?: "Seleccionar componente", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        DropdownMenu(expanded = comboMenuRow == index, onDismissRequest = { comboMenuRow = null }) {
                                            availableComboComponents.filter { candidate -> comboRows.none { it.productId == candidate.remoteProductId && it.productId != row.productId } }.forEach { candidate ->
                                                DropdownMenuItem(
                                                    text = { Text(candidate.name) },
                                                    onClick = {
                                                        comboRows = comboRows.toMutableList().also { it[index] = row.copy(productId = candidate.remoteProductId.orEmpty()) }
                                                        comboMenuRow = null
                                                        comboError = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = row.quantity,
                                        onValueChange = { value -> comboRows = comboRows.toMutableList().also { it[index] = row.copy(quantity = value.filter(Char::isDigit)) }; comboError = false },
                                        modifier = Modifier.width(82.dp),
                                        label = { Text("Cantidad") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    IconButton(onClick = { comboRows = comboRows.filterIndexed { rowIndex, _ -> rowIndex != index }; comboError = false }) { Icon(Icons.Default.Delete, "Quitar componente", tint = BSPOSTheme.colors.error) }
                                }
                            }
                            OutlinedButton(onClick = { comboRows = comboRows + ComboDraft("", "1") }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Agregar componente")
                            }
                            if (comboError) Text("Agrega al menos un componente y una cantidad válida.", color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!effectiveDecantMode && !serviceMode && current?.remoteSaleUnit != "service" && !comboMode) {
                Box {
                    TextButton({ saleUnitOpen = true }) {
                        Text("Se vende como: ${when (saleUnit) {
                            "bottle" -> "Botella completa"
                            "ml" -> "Por mililitro (ml)"
                            "service" -> "Servicio (sin inventario)"
                            else -> "Unidad"
                        }}")
                    }
                    DropdownMenu(saleUnitOpen, { saleUnitOpen = false }) {
                        listOf("unit" to "Unidad", "bottle" to "Botella completa", "ml" to "Por mililitro (ml)", "service" to "Servicio (sin inventario)").forEach { (value, label) ->
                            DropdownMenuItem({ Text(label) }, {
                                saleUnit = value
                                if (value == "unit" || value == "service") volumeMl = ""
                                saleUnitOpen = false
                            })
                        }
                    }
                }
                if (saleUnit == "bottle" || saleUnit == "ml") {
                    OutlinedTextField(
                        volumeMl,
                        { volumeMl = it.filter(Char::isDigit) },
                        label = { Text("Contenido en ml") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("Ejemplo: 100 ml") }
                    )
                }
            }
            if (showProductCode) OutlinedTextField(code, { code = it }, label = { Text("Código interno") })
            if (showCost && !effectiveDecantMode && !comboMode) OutlinedTextField(purchasePrice, { value -> if (current == null) purchasePrice = value; purchaseChanged = true; priceError = false }, label = { Text(when (saleUnit) { "bottle" -> "Costo de compra por botella (${LocalCurrency.current.symbol})"; "service" -> "Costo de insumos (${LocalCurrency.current.symbol})"; else -> "Precio de compra (${LocalCurrency.current.symbol})" }) }, enabled = current == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = priceError, supportingText = { if (saleUnit == "bottle") Text("Obligatorio para calcular la ganancia y el costo recuperado por decants") else if (saleUnit == "service") Text("Opcional; se usa para calcular la ganancia del servicio") else if (current != null) Text("Se actualiza desde una entrada de inventario") })
            OutlinedTextField(price, { value -> price = value; priceChanged = true; priceError = false }, label = { Text(if (saleUnit == "service") "Precio del servicio (${LocalCurrency.current.symbol})" else "Precio de venta (${LocalCurrency.current.symbol})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = priceError, supportingText = { if (priceError) Text("Indica un precio válido, con hasta dos decimales") })
            if (!serviceMode && current?.remoteSaleUnit != "service") OutlinedTextField(wholesalePrice, { wholesalePrice = it; priceError = false }, label = { Text("Precio por mayor (${LocalCurrency.current.symbol})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), supportingText = { Text("Opcional; habilita el modo mayorista en el POS") })
            if (showInventoryFields && !effectiveDecantMode && saleUnit != "service" && !comboMode) OutlinedTextField(initialQuantity, { value -> if (current == null) initialQuantity = value.filter(Char::isDigit) }, label = { Text("Stock inicial") }, enabled = current == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text(if (current == null) "Se registra en 0 si lo dejas así" else "Edita existencias desde Inventario") })
            if (!serviceMode && current?.remoteSaleUnit != "service") {
                OutlinedTextField(minimumStock, { minimumStock = it.filter(Char::isDigit) }, label = { Text("Aviso de mínimo") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Te avisaremos al llegar a esta cantidad") })
                Box { TextButton({ categoryOpen = true }) { Text("Categoria: ${category.name}") }; DropdownMenu(categoryOpen, { categoryOpen = false }) { categories.forEach { item -> DropdownMenuItem({ Text(item.name) }, { category = item; categoryOpen = false }) } } }
                Box { TextButton({ unitOpen = true }) { Text("Unidad: ${unit.name}") }; DropdownMenu(unitOpen, { unitOpen = false }) { units.forEach { item -> DropdownMenuItem({ Text(item.name) }, { unit = item; unitOpen = false }) } } }
            }
        }
    }, confirmButton = {
        TextButton({
            val sale = if (!priceChanged && current != null) current.salePrice else price.toPesosCents()
            val purchase = if (!purchaseChanged && current != null) current.lastPurchaseCost else purchasePrice.toPesosCents()
            val wholesale = wholesalePrice.toPesosCents()
            val quantity = initialQuantity.toLongOrNull()
            val minimum = minimumStock.toLongOrNull()
            val effectiveCode = code.ifBlank { "AUTO-" + name.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "-").take(24) }
            val effectivePurchase = if (effectiveDecantMode || comboMode) 0L else if (saleUnit == "service" && purchasePrice.isBlank()) 0L else if (showCost) purchase else (current?.lastPurchaseCost ?: 0L)
            val effectiveQuantity = if (showInventoryFields && !effectiveDecantMode && saleUnit != "service" && !comboMode) quantity else 0L
            val selectedVolume = volumeMl.toIntOrNull()
            val sourceVolume = sourceProduct?.remoteVolumeMl
            val sourceCost = sourceProduct?.lastPurchaseCost ?: 0L
            val selectedComboItems = comboRows.mapNotNull { row -> row.productId.takeIf { it.isNotBlank() }?.let { id -> row.quantity.toIntOrNull()?.takeIf { it > 0 }?.let { ProductComboComponent(id, it) } } }
            val validCombo = !comboMode || (shopId != null && selectedComboItems.isNotEmpty() && selectedComboItems.size == comboRows.size)
            val validVolume = comboMode || saleUnit == "unit" || saleUnit == "service" || (selectedVolume != null && selectedVolume > 0)
            val validPresentation = if (effectiveDecantMode) {
                shopId != null && sourceProduct?.remoteProductId != null && (sourceCost > 0 || !showCost) && selectedVolume != null && selectedVolume > 0 && sourceVolume != null && selectedVolume <= sourceVolume
            } else {
                validVolume && validCombo && (comboMode || saleUnit != "bottle" || !showInventoryFields || effectivePurchase?.let { it > 0 } == true)
            }
            if (name.isBlank() || (showProductCode && effectiveCode.isBlank()) || sale == null || sale < 0 || effectivePurchase == null || effectivePurchase < 0 || wholesale?.let { it < 0 } == true || effectiveQuantity == null || effectiveQuantity < 0 || minimum == null || minimum < 0 || !validPresentation) {
                priceError = true
                comboError = comboMode && !validCombo
            } else {
                onSave(ProductInput(name, effectiveCode, category.id, unit.id, sale, purchasePrice = effectivePurchase, wholesalePrice = wholesale, description = description.trim().ifBlank { null }, imagePath = imagePath, thumbnailPath = imagePath, minimumStock = minimum, tracksExpiration = current?.tracksExpiration ?: false, remoteShopId = shopId, remoteSaleUnit = if (effectiveDecantMode) "decant" else if (comboMode) "unit" else saleUnit, remoteVolumeMl = if (effectiveDecantMode || saleUnit == "bottle" || saleUnit == "ml") selectedVolume else null, remoteSourceProductId = if (effectiveDecantMode) sourceProduct?.remoteProductId else null, isCombo = comboMode, comboItems = if (comboMode) selectedComboItems else emptyList(), isActive = if (serviceMode || current?.remoteSaleUnit == "service") published else (current?.isActive ?: true)), if (current == null && !comboMode) effectiveQuantity else 0L)
            }
        }) { Text("Guardar") }
    }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)

private data class ComboDraft(val productId: String, val quantity: String)

private fun String.toPesosCents(): Long? = MoneyUtils.parseDecimalToCents(this)
