package com.example.bspos.presentation.catalog

import android.content.Intent
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
import com.example.bspos.domain.model.UnitOfMeasure
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import com.example.bspos.domain.usecase.ProductInput
import com.example.bspos.presentation.common.DialogScrollableColumn
import coil.compose.AsyncImage
import java.util.Locale

@Composable
fun CatalogHomeScreen(
    isTablet: Boolean,
    presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    showCost: Boolean = false,
    showProductCode: Boolean = true,
    photosOnly: Boolean = false,
    initialDecantMode: Boolean = false,
    viewModel: ProductCatalogViewModel = hiltViewModel()
) {
    var baseCatalog by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(initialDecantMode) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deleteTarget by remember { mutableStateOf<Product?>(null) }
    if (baseCatalog) {
        Column(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp)) {
            TextButton({ baseCatalog = false }) { Text(presentation.term("products", "Productos")) }
            CatalogSettingsScreen(Modifier.weight(1f))
        }
        return
    }
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val units by viewModel.units.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val message by viewModel.message.collectAsState()
    val shops by viewModel.shops.collectAsState()
    val sourceProducts = products.filter {
        it.isActive && it.deletedAt == null && it.remoteShopId != null && it.remoteProductId != null &&
            it.remoteSaleUnit in setOf("bottle", "ml") && it.remoteVolumeMl != null
    }
    val formReady = categories.isNotEmpty() && units.isNotEmpty()
    var query by remember { mutableStateOf("") }
    val quantities = stock.associate { it.productId to it.quantity }
    val categoryNames = categories.associate { it.id to it.name }
    val filtered = products.filter {
        it.isActive && it.deletedAt == null &&
            (!photosOnly || it.imagePath.isNullOrBlank()) &&
            (it.name.contains(query, true) || it.internalCode.contains(query, true))
    }

    Column(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(if (isTablet) 28.dp else 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (photosOnly) "CATÁLOGO / FOTOS" else if (initialDecantMode) "CATÁLOGO / DECANTS" else presentation.term("products", "Catalogo comercial"), color = BSPOSTheme.colors.textSecondary)
                if (photosOnly) Text("Productos sin foto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
            if (!photosOnly) TextButton({ baseCatalog = true }) { Text("Categorias") }
        }
        Spacer(Modifier.height(14.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = BSPOSTheme.colors.primary)
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text(presentation.catalogSearchPlaceholder) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (photosOnly) "${filtered.size} pendientes de fotografía" else "${filtered.size} productos",
                color = BSPOSTheme.colors.textSecondary,
                style = MaterialTheme.typography.labelLarge
            )
            Button({ showForm = true }, enabled = categories.any { it.isActive } && units.any { it.isActive }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text(if (initialDecantMode) "Nueva presentación" else "Nuevo producto") }
        }
        if (initialDecantMode && !formReady) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BSPOSTheme.colors.warning.copy(alpha = .12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.warning.copy(alpha = .35f))
            ) {
                Text(
                    "Para crear una presentación decant necesitas al menos una categoría y una unidad activa. Créala desde Productos > Categorías y vuelve a Decants.",
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
                        photosOnly -> "Todos tus productos tienen foto"
                        else -> presentation.catalogEmptyMessage
                    },
                    color = BSPOSTheme.colors.textSecondary
                )
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(if (isTablet) 4 else 2), Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { product -> ProductCard(product, quantities[product.id] ?: 0L, categoryNames[product.categoryId], presentation.catalogShowStock, showCost, showProductCode,
                    { if (viewModel.canEdit(product)) editingProduct = product else viewModel.reportReadOnly() },
                    { if (viewModel.canEdit(product)) deleteTarget = product else viewModel.reportReadOnly() }) }
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
        onSave = { input, initialQuantity -> if (editingProduct == null) viewModel.add(input, initialQuantity) else viewModel.update(editingProduct!!, input); showForm = false; editingProduct = null },
        onDismiss = { showForm = false; editingProduct = null },
        shops = shops,
        decantMode = initialDecantMode,
        sourceProducts = sourceProducts
    )
    deleteTarget?.let { product ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Eliminar producto") }, text = { Text("¿Eliminar ${product.name}? El producto dejará de aparecer en el catálogo.") }, confirmButton = { TextButton({ viewModel.delete(product); deleteTarget = null }) { Text("Eliminar", color = BSPOSTheme.colors.error) } }, dismissButton = { TextButton({ deleteTarget = null }) { Text("Cancelar") } })
    }
    message?.let { text -> AlertDialog(onDismissRequest = viewModel::consumeMessage, title = { Text("No se pudo completar") }, text = { Text(text) }, confirmButton = { TextButton(viewModel::consumeMessage) { Text("Cerrar") } }) }
}

@Composable
private fun ProductCard(product: Product, quantity: Long, category: String?, showStock: Boolean, showCost: Boolean, showProductCode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
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
            Spacer(Modifier.height(12.dp))
            Text(product.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            if (showProductCode) Text(category ?: product.internalCode, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            Text("Venta: ${money(product.salePrice)}", color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            if (showCost) Text("Compra: ${money(product.lastPurchaseCost)}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            if (showStock) { Spacer(Modifier.height(9.dp)); Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = .12f)) { Text("$statusText ($quantity)", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }
        }
    }
}

@Composable
private fun ProductForm(current: Product?, currentQuantity: Long, categories: List<Category>, units: List<UnitOfMeasure>, presentation: MiCatalogoBusinessPresentation, showCost: Boolean, showInventoryFields: Boolean, showProductCode: Boolean, onSave: (ProductInput, Long) -> Unit, onDismiss: () -> Unit, shops: List<com.example.bspos.domain.model.MiCatalogoShop>, decantMode: Boolean = false, sourceProducts: List<Product> = emptyList()) {
    val context = LocalContext.current
    var name by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
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
    var shopId by remember(current?.id, shops) { mutableStateOf(current?.remoteShopId ?: shops.singleOrNull()?.id) }
    var shopOpen by remember { mutableStateOf(false) }
    val effectiveDecantMode = decantMode || current?.remoteSaleUnit == "decant"
    var volumeMl by remember(current?.id, effectiveDecantMode) { mutableStateOf(current?.remoteVolumeMl?.toString().orEmpty()) }
    var saleUnit by remember(current?.id, effectiveDecantMode) {
        mutableStateOf(if (effectiveDecantMode) "decant" else (current?.remoteSaleUnit ?: "unit"))
    }
    var saleUnitOpen by remember { mutableStateOf(false) }
    var sourceProduct by remember(current?.id, sourceProducts) { mutableStateOf(sourceProducts.firstOrNull { it.remoteProductId == current?.remoteSourceProductId }) }
    var sourceOpen by remember { mutableStateOf(false) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        imagePath = uri.toString()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (effectiveDecantMode) "Nueva presentación decant" else if (current == null) "Nuevo producto" else "Editar producto") }, text = {
        DialogScrollableColumn {
            if (effectiveDecantMode) {
                Text("La presentación comparte el inventario de su producto fuente. Cada venta descuenta los ml correspondientes.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Box {
                    TextButton({ sourceOpen = true }) { Text("Fuente: ${sourceProduct?.name ?: "Seleccionar botella o producto en ml"}") }
                    DropdownMenu(sourceOpen, { sourceOpen = false }) {
                        sourceProducts.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} · ${item.remoteVolumeMl} ml") },
                                onClick = { sourceProduct = item; sourceOpen = false }
                            )
                        }
                    }
                }
                if (sourceProducts.isEmpty()) Text("Primero sincroniza o crea una botella fuente con volumen y control de inventario.", color = BSPOSTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(volumeMl, { volumeMl = it.filter(Char::isDigit) }, label = { Text("Tamaño del decant (ml)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Máximo: ${sourceProduct?.remoteVolumeMl ?: "—"} ml") })
            }
            if (current == null) Box {
                TextButton({ shopOpen = true }) { Text("Destino: ${shops.find { it.id == shopId }?.name ?: "Solo este dispositivo"}") }
                DropdownMenu(shopOpen, { shopOpen = false }) {
                    DropdownMenuItem({ Text("Solo este dispositivo") }, { shopId = null; shopOpen = false })
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
            if (!effectiveDecantMode) {
                Box {
                    TextButton({ saleUnitOpen = true }) {
                        Text("Se vende como: ${when (saleUnit) {
                            "bottle" -> "Botella completa"
                            "ml" -> "Por mililitro (ml)"
                            else -> "Unidad"
                        }}")
                    }
                    DropdownMenu(saleUnitOpen, { saleUnitOpen = false }) {
                        listOf("unit" to "Unidad", "bottle" to "Botella completa", "ml" to "Por mililitro (ml)").forEach { (value, label) ->
                            DropdownMenuItem({ Text(label) }, {
                                saleUnit = value
                                if (value == "unit") volumeMl = ""
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
            if (showCost && !effectiveDecantMode) OutlinedTextField(purchasePrice, { value -> if (current == null) purchasePrice = value; purchaseChanged = true; priceError = false }, label = { Text("Precio de compra (${LocalCurrency.current.symbol})") }, enabled = current == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = priceError, supportingText = { if (current != null) Text("Se actualiza desde una entrada de inventario") })
            OutlinedTextField(price, { value -> price = value; priceChanged = true; priceError = false }, label = { Text("Precio de venta (${LocalCurrency.current.symbol})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = priceError, supportingText = { if (priceError) Text("Indica un precio válido, con hasta dos decimales") })
            OutlinedTextField(wholesalePrice, { wholesalePrice = it; priceError = false }, label = { Text("Precio por mayor (${LocalCurrency.current.symbol})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), supportingText = { Text("Opcional; habilita el modo mayorista en el POS") })
            if (showInventoryFields && !effectiveDecantMode) OutlinedTextField(initialQuantity, { value -> if (current == null) initialQuantity = value.filter(Char::isDigit) }, label = { Text("Stock inicial") }, enabled = current == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text(if (current == null) "Se registra en 0 si lo dejas así" else "Edita existencias desde Inventario") })
            OutlinedTextField(minimumStock, { minimumStock = it.filter(Char::isDigit) }, label = { Text("Aviso de mínimo") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Te avisaremos al llegar a esta cantidad") })
            Box { TextButton({ categoryOpen = true }) { Text("Categoria: ${category.name}") }; DropdownMenu(categoryOpen, { categoryOpen = false }) { categories.forEach { item -> DropdownMenuItem({ Text(item.name) }, { category = item; categoryOpen = false }) } } }
            Box { TextButton({ unitOpen = true }) { Text("Unidad: ${unit.name}") }; DropdownMenu(unitOpen, { unitOpen = false }) { units.forEach { item -> DropdownMenuItem({ Text(item.name) }, { unit = item; unitOpen = false }) } } }
        }
    }, confirmButton = {
        TextButton({
            val sale = if (!priceChanged && current != null) current.salePrice else price.toPesosCents()
            val purchase = if (!purchaseChanged && current != null) current.lastPurchaseCost else purchasePrice.toPesosCents()
            val wholesale = wholesalePrice.toPesosCents()
            val quantity = initialQuantity.toLongOrNull()
            val minimum = minimumStock.toLongOrNull()
            val effectiveCode = code.ifBlank { "AUTO-" + name.trim().uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "-").take(24) }
            val effectivePurchase = if (effectiveDecantMode) 0L else if (showCost) purchase else (current?.lastPurchaseCost ?: 0L)
            val effectiveQuantity = if (showInventoryFields && !effectiveDecantMode) quantity else 0L
            val selectedVolume = volumeMl.toIntOrNull()
            val sourceVolume = sourceProduct?.remoteVolumeMl
            val validVolume = saleUnit == "unit" || (selectedVolume != null && selectedVolume > 0)
            val validPresentation = if (effectiveDecantMode) {
                shopId != null && sourceProduct?.remoteProductId != null && selectedVolume != null && selectedVolume > 0 && sourceVolume != null && selectedVolume <= sourceVolume
            } else {
                validVolume
            }
            if (name.isBlank() || (showProductCode && effectiveCode.isBlank()) || sale == null || sale < 0 || effectivePurchase == null || effectivePurchase < 0 || wholesale?.let { it < 0 } == true || effectiveQuantity == null || effectiveQuantity < 0 || minimum == null || minimum < 0 || !validPresentation) {
                priceError = true
            } else {
                onSave(ProductInput(name, effectiveCode, category.id, unit.id, sale, purchasePrice = if (effectiveDecantMode) 0L else effectivePurchase, wholesalePrice = wholesale, imagePath = imagePath, thumbnailPath = imagePath, minimumStock = minimum, tracksExpiration = current?.tracksExpiration ?: false, remoteShopId = shopId, remoteSaleUnit = if (effectiveDecantMode) "decant" else saleUnit, remoteVolumeMl = if (effectiveDecantMode || saleUnit == "bottle" || saleUnit == "ml") selectedVolume else null, remoteSourceProductId = if (effectiveDecantMode) sourceProduct?.remoteProductId else null), if (current == null) effectiveQuantity else 0L)
            }
        }) { Text("Guardar") }
    }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)

private fun String.toPesosCents(): Long? = MoneyUtils.parseDecimalToCents(this)
