package com.example.bspos.presentation.supplier

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Supplier
import com.example.bspos.domain.usecase.SupplierInput
import com.example.bspos.presentation.common.DialogScrollableColumn

@Composable
fun SupplierScreen(viewModel: SupplierViewModel = hiltViewModel()) {
    val suppliers by viewModel.suppliers.collectAsState()
    val message by viewModel.message.collectAsState()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Supplier?>(null) }
    val filtered = suppliers.filter { it.name.contains(query, true) || it.contactName.orEmpty().contains(query, true) || it.phone.orEmpty().contains(query) }

    Column(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Contactos y abastecimiento", color = BSPOSTheme.colors.textSecondary)
            }
            Button({ creating = true }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Nuevo") }
        }
        Spacer(Modifier.size(14.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = BSPOSTheme.colors.primary)
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Buscar proveedor, contacto o teléfono") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent))
            }
        }
        Spacer(Modifier.size(12.dp))
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (query.isBlank()) "Aún no hay proveedores" else "Sin coincidencias", color = BSPOSTheme.colors.textSecondary) }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { supplier -> SupplierCard(supplier, { editing = supplier }, { viewModel.delete(supplier) }) }
            }
        }
    }

    if (creating) SupplierForm(null, { viewModel.add(it); creating = false }, { creating = false })
    editing?.let { current -> SupplierForm(current, { viewModel.update(current, it); editing = null }, { editing = null }) }
    message?.let { text -> AlertDialog(onDismissRequest = viewModel::consumeMessage, title = { Text("No se pudo completar") }, text = { Text(text) }, confirmButton = { TextButton(viewModel::consumeMessage) { Text("Cerrar") } }) }
}

@Composable
private fun SupplierCard(supplier: Supplier, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) { Text(supplier.name.take(1).uppercase(), color = BSPOSTheme.colors.primary, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(supplier.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(supplier.contactName, supplier.phone).joinToString(" / ").ifBlank { "Sin contacto" }, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onEdit) { Icon(Icons.Default.Edit, "Editar", tint = BSPOSTheme.colors.primary) }
            IconButton(onDelete) { Icon(Icons.Default.DeleteOutline, "Eliminar", tint = BSPOSTheme.colors.error) }
        }
    }
}

@Composable
private fun SupplierForm(current: Supplier?, onSave: (SupplierInput) -> Unit, onDismiss: () -> Unit) {
    var name by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var contact by remember(current?.id) { mutableStateOf(current?.contactName.orEmpty()) }
    var phone by remember(current?.id) { mutableStateOf(current?.phone.orEmpty()) }
    var error by remember(current?.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (current == null) "Nuevo proveedor" else "Editar proveedor") }, text = {
        DialogScrollableColumn {
            OutlinedTextField(name, { name = it; error = false }, label = { Text("Nombre") }, isError = error)
            OutlinedTextField(contact, { contact = it }, label = { Text("Contacto") })
            OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") })
            if (error) Text("El nombre es obligatorio.", color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ if (name.isBlank()) error = true else onSave(SupplierInput(name, contact, phone)) }) { Text("Guardar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}
