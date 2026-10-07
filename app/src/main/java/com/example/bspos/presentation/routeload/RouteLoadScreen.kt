package com.example.bspos.presentation.routeload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.usecase.RouteLoadLineInput
import java.util.UUID

@Composable
fun RouteLoadScreen(viewModel: RouteLoadViewModel = hiltViewModel()) {
    val routes by viewModel.routes.collectAsState()
    val products by viewModel.products.collectAsState()
    val activeRoutes = routes.filter { it.isActive }
    val activeProducts = products.filter { it.isActive && it.deletedAt == null }
    var route by remember(activeRoutes) { mutableStateOf(activeRoutes.firstOrNull()) }
    var product by remember(activeProducts) { mutableStateOf(activeProducts.firstOrNull()) }
    var quantity by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var routeOpen by remember { mutableStateOf(false) }
    var productOpen by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val lines = remember { mutableStateListOf<RouteLoadLineInput>() }
    val names = activeProducts.associate { it.id to it.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, null, tint = BSPOSTheme.colors.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Carga de ruta", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text("Traslado de inventario a una ruta", color = BSPOSTheme.colors.textSecondary)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Selector("Ruta", route?.name ?: "Sin rutas activas", routeOpen, { routeOpen = true }, { routeOpen = false }) {
                        activeRoutes.forEach { item -> DropdownMenuItem({ Text(item.name) }, { route = item; routeOpen = false }) }
                    }
                    Selector("Producto", product?.name ?: "Sin productos activos", productOpen, { productOpen = true }, { productOpen = false }) {
                        activeProducts.forEach { item -> DropdownMenuItem({ Text(item.name) }, { product = item; productOpen = false }) }
                    }
                    OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit); validationError = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = validationError != null)
                    Button(onClick = {
                        val selected = product
                        val value = quantity.toLongOrNull()
                        when {
                            selected == null -> validationError = "Selecciona un producto."
                            value == null || value <= 0 -> validationError = "Indica una cantidad mayor que cero."
                            lines.any { it.productId == selected.id } -> validationError = "Ese producto ya está en la carga."
                            else -> {
                                lines += RouteLoadLineInput(selected.id, value)
                                quantity = ""
                                validationError = null
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = activeProducts.isNotEmpty()) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar producto")
                    }
                    validationError?.let { Text(it, color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Text("Productos en la carga (${lines.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
        if (lines.isEmpty()) {
            item { Text("Agrega productos para preparar el traslado.", color = BSPOSTheme.colors.textSecondary) }
        } else {
            items(lines, key = { it.productId }) { line ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(names[line.productId] ?: "Producto", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Cantidad: ${line.quantity}", color = BSPOSTheme.colors.textSecondary)
                        }
                        TextButton(onClick = { lines.remove(line) }) {
                            Icon(Icons.Default.DeleteOutline, "Quitar", tint = BSPOSTheme.colors.error)
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notas opcionales") }, minLines = 2)
            Spacer(Modifier.padding(top = 2.dp))
            Button(onClick = { route?.let { selected -> viewModel.create(selected.id, lines.toList(), notes); lines.clear(); notes = "" } }, modifier = Modifier.fillMaxWidth(), enabled = route != null && lines.isNotEmpty()) {
                Text("Crear carga de ruta")
            }
        }
    }
}

@Composable
private fun Selector(label: String, value: String, expanded: Boolean, onOpen: () -> Unit, onClose: () -> Unit, menu: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(label, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedTextField(value, { onOpen() }, modifier = Modifier.fillMaxWidth(), readOnly = true)
            androidx.compose.material3.DropdownMenu(expanded, onDismissRequest = onClose, content = menu)
        }
    }
}
