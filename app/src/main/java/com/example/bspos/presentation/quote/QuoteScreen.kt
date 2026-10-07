package com.example.bspos.presentation.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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

    Box(Modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = BSPOSTheme.colors.primary)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
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

                items(products, key = { "product-${it.id}" }) { product ->
                    QuoteProductCard(product, state.cart[product] ?: 0, viewModel::add, viewModel::remove)
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Cotización actual", fontWeight = FontWeight.ExtraBold)
                }
                if (state.cart.isEmpty()) {
                    item { Text("Agrega productos para preparar una cotización.", color = BSPOSTheme.colors.textSecondary) }
                } else {
                    items(state.cart.entries.toList(), key = { "cart-${it.key.id}" }) { (product, quantity) ->
                        QuoteCartRow(product, quantity, { viewModel.remove(product) }, { viewModel.add(product) })
                    }
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Total estimado: RD$ ${money(total)}", fontWeight = FontWeight.ExtraBold)
                                OutlinedTextField(customerName, { customerName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cliente (opcional)") })
                                OutlinedTextField(customerPhone, { customerPhone = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Teléfono (opcional)") })
                                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Notas") })
                                Button(
                                    onClick = { viewModel.save(customerName, customerPhone, notes) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.saving
                                ) { Text(if (state.saving) "Guardando…" else "Guardar cotización") }
                            }
                        }
                    }
                }

                if (state.rows.isNotEmpty()) {
                    item { Text("Cotizaciones recientes", fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp)) }
                    items(state.rows, key = { "recent-${it.id ?: it.primary}" }) { row ->
                        RecentQuoteRow(row, state.convertingId == row.id, { row.id?.let(viewModel::convert) })
                    }
                }
                state.message?.let { message -> item { Text(message, color = BSPOSTheme.colors.success, fontWeight = FontWeight.Bold) } }
                state.error?.let { error -> item { Text(error, color = BSPOSTheme.colors.error, fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

@Composable
private fun QuoteProductCard(product: FeatureProductDto, quantity: Int, onAdd: (FeatureProductDto) -> Unit, onRemove: (FeatureProductDto) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.primaryLight),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(product.imageUrl, product.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Icon(Icons.Default.Inventory2, null, tint = BSPOSTheme.colors.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (product.category.isNotBlank()) Text(product.category, color = BSPOSTheme.colors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Text("RD$ ${product.price}", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary)
                product.stock?.let { Text("Existencia: $it", color = BSPOSTheme.colors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
            }
            if (quantity > 0) {
                IconButton(onClick = { onRemove(product) }) { Icon(Icons.Default.Remove, "Quitar") }
                Text(quantity.toString(), fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { onAdd(product) }) { Icon(Icons.Default.Add, "Agregar") }
        }
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
