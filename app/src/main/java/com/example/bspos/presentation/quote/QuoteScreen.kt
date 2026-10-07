package com.example.bspos.presentation.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureProductDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QuoteScreen(viewModel: QuoteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        viewModel.load()
    }

    val products = state.products.filter { product ->
        (selectedCategory == null || product.category == selectedCategory) &&
            (query.isBlank() || product.name.contains(query, true) || product.brand.contains(query, true) || product.code.contains(query, true))
    }
    val categories = state.products.mapNotNull { it.category.takeIf(String::isNotBlank) }.distinct().sorted()
    val total = state.cart.entries.sumOf { (product, quantity) -> parseMoney(product.price) * quantity }
    val cartQuantity = state.cart.values.sum()
    var showCart by remember { mutableStateOf(false) }
    val cartSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(Modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showCart = true },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    text = { Text(if (cartQuantity == 0) "Cotizar" else "Cotización ($cartQuantity)") },
                    containerColor = BSPOSTheme.colors.primary,
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            }
        ) { contentPadding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BSPOSTheme.colors.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 108.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Presupuestos guardados para convertirlos en venta.", color = BSPOSTheme.colors.textSecondary)
                    Spacer(Modifier.height(14.dp))
                    QuoteSummary(state.rows)
                    Spacer(Modifier.height(14.dp))
                    Text("Busca y agrega productos al presupuesto", color = BSPOSTheme.colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Buscar por nombre, marca o código") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                    if (categories.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("Todos") })
                            categories.take(5).forEach { category ->
                                FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                            }
                        }
                    }
                }

                gridItems(products, key = { "product-${it.id}" }) { product ->
                    QuoteProductCard(product, state.cart[product] ?: 0, viewModel::add, viewModel::remove)
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (state.rows.isNotEmpty()) {
                        Text("Cotizaciones recientes", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (state.rows.isNotEmpty()) {
                    gridItems(state.rows, key = { "recent-${it.id ?: it.primary}" }, span = { GridItemSpan(maxLineSpan) }) { row ->
                        RecentQuoteRow(row, state.convertingId == row.id, { row.id?.let(viewModel::convert) })
                    }
                }
                state.message?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) { Text(message, color = BSPOSTheme.colors.success, fontWeight = FontWeight.Bold) } }
                state.error?.let { error -> item(span = { GridItemSpan(maxLineSpan) }) { Text(error, color = BSPOSTheme.colors.error, fontWeight = FontWeight.Bold) } }
            }
        }
        }
    }

    if (showCart) {
        ModalBottomSheet(
            onDismissRequest = { showCart = false },
            sheetState = cartSheetState
        ) {
            QuoteCartSheet(
                cart = state.cart,
                total = total,
                customerName = customerName,
                customerPhone = customerPhone,
                notes = notes,
                saving = state.saving,
                message = state.message,
                error = state.error,
                onCustomerNameChange = { customerName = it },
                onCustomerPhoneChange = { customerPhone = it },
                onNotesChange = { notes = it },
                onMinus = viewModel::remove,
                onPlus = viewModel::add,
                onSave = { viewModel.save(customerName, customerPhone, notes) }
            )
        }
    }
}

@Composable
private fun QuoteProductCard(product: FeatureProductDto, quantity: Int, onAdd: (FeatureProductDto) -> Unit, onRemove: (FeatureProductDto) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.primaryLight),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(product.imageUrl, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary)
                }
                IconButton(
                    onClick = { onAdd(product) },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) { Icon(Icons.Default.Add, "Agregar") }
                if (quantity > 0) {
                    Text(
                        "En cotización: $quantity",
                        modifier = Modifier.align(Alignment.BottomStart)
                            .background(BSPOSTheme.colors.primary, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(product.name, fontWeight = FontWeight.Bold, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (product.category.isNotBlank()) Text(product.category, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("RD$ ${product.price}", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary)
            product.stock?.let { Text("Existencia: $it", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall) }
            if (quantity > 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { onRemove(product) }) { Icon(Icons.Default.Remove, "Quitar") }
                    Text(quantity.toString(), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onAdd(product) }) { Icon(Icons.Default.Add, "Agregar") }
                }
            }
        }
    }
}

@Composable
private fun QuoteCartSheet(
    cart: Map<FeatureProductDto, Int>,
    total: Long,
    customerName: String,
    customerPhone: String,
    notes: String,
    saving: Boolean,
    message: String?,
    error: String?,
    onCustomerNameChange: (String) -> Unit,
    onCustomerPhoneChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onMinus: (FeatureProductDto) -> Unit,
    onPlus: (FeatureProductDto) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Carrito de cotización", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                if (cart.isEmpty()) "Todavía no has agregado productos." else "${cart.size} productos · ${cart.values.sum()} unidades",
                color = BSPOSTheme.colors.textSecondary
            )
        }
        if (cart.isEmpty()) {
            item { Text("Cierra este panel para seguir explorando el catálogo.", color = BSPOSTheme.colors.textSecondary) }
        } else {
            lazyItems(cart.entries.toList(), key = { "cart-${it.key.id}" }) { (product, quantity) ->
                QuoteCartRow(product, quantity, { onMinus(product) }, { onPlus(product) })
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Total estimado: RD$ ${money(total)}", fontWeight = FontWeight.ExtraBold)
                        OutlinedTextField(customerName, onCustomerNameChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cliente (opcional)") })
                        OutlinedTextField(customerPhone, onCustomerPhoneChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Teléfono (opcional)") })
                        OutlinedTextField(notes, onNotesChange, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Notas") })
                        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
                            Text(if (saving) "Guardando…" else "Guardar cotización")
                        }
                    }
                }
            }
        }
        message?.let { item { Text(it, color = BSPOSTheme.colors.success, fontWeight = FontWeight.Bold) } }
        error?.let { item { Text(it, color = BSPOSTheme.colors.error, fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun QuoteCartRow(product: FeatureProductDto, quantity: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(BSPOSTheme.colors.primaryLight),
            contentAlignment = Alignment.Center
        ) {
            if (!product.imageUrl.isNullOrBlank()) {
                AsyncImage(product.imageUrl, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Bold); Text("RD$ ${product.price} · $quantity", color = BSPOSTheme.colors.textSecondary) }
        IconButton(onClick = onMinus) { Icon(Icons.Default.Remove, "Disminuir") }
        IconButton(onClick = onPlus) { Icon(Icons.Default.Add, "Aumentar") }
    }
}

@Composable
private fun RecentQuoteRow(row: FeatureRowDto, converting: Boolean, onConvert: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.primary.ifBlank { "Cotización" }, fontWeight = FontWeight.Bold)
                if (row.secondary.isNotBlank()) Text(row.secondary, color = BSPOSTheme.colors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                if (row.value.isNotBlank()) Text(row.value, fontWeight = FontWeight.ExtraBold)
            }
            if (row.canConvert) TextButton(onClick = onConvert, enabled = !converting) { Text(if (converting) "…" else "Convertir") }
        }
    }
}

private fun parseMoney(value: String): Long = value.replace(",", "").toDoubleOrNull()?.let { (it * 100).toLong() } ?: 0L
private fun money(cents: Long): String = String.format(Locale.US, "%,.2f", cents / 100.0)

@Composable
private fun QuoteSummary(rows: List<FeatureRowDto>) {
    val active = rows.count { it.status.lowercase(Locale.US).contains("vigente") || it.status.lowercase(Locale.US).contains("activo") }
    val expired = rows.count { it.status.lowercase(Locale.US).contains("venc") }
    val pendingTotal = rows.filter { it.canConvert }.sumOf { parseMoney(it.value) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val metrics = listOf(
                Triple("Vigentes", active.toString(), BSPOSTheme.colors.textPrimary),
                Triple("Por convertir", "RD$ ${money(pendingTotal)}", BSPOSTheme.colors.primary),
                Triple("Vencidas", expired.toString(), BSPOSTheme.colors.warning)
            )
            if (maxWidth < 520.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    metrics.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { (label, value, color) -> QuoteMetric(label, value, color, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    metrics.forEach { (label, value, color) -> QuoteMetric(label, value, color, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun QuoteMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
