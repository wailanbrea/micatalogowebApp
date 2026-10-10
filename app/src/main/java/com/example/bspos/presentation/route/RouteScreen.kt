package com.example.bspos.presentation.route

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import com.example.bspos.presentation.common.BSPOSButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.RouteCustomer
import com.example.bspos.domain.usecase.RouteInput
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.common.DialogScrollableColumn

@Composable
fun RouteScreen(viewModel: RouteViewModel = hiltViewModel()) {
    val routes by viewModel.routes.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CommercialRoute?>(null) }
    var managing by remember { mutableStateOf<CommercialRoute?>(null) }
    val active = routes.count { it.isActive }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BSPOSTheme.colors.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Distribución y orden de visita", color = BSPOSTheme.colors.textSecondary)
                }
                Button({ creating = true }, shape = RoundedCornerShape(14.dp)) { Text("Nueva ruta") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BoxBadge()
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Rutas activas", color = BSPOSTheme.colors.textSecondary); Text("$active de ${routes.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
        if (routes.isEmpty()) {
            item { Text("Aún no hay rutas configuradas.", color = BSPOSTheme.colors.textSecondary) }
        } else {
            items(routes, key = { it.id }) { route ->
                RouteCard(route, onCustomers = { managing = route; viewModel.selectRoute(route) }, onActive = { viewModel.setActive(route, it) }, onEdit = { editing = route })
            }
        }
    }

    if (creating) RouteForm(null, { viewModel.add(it); creating = false }, { creating = false })
    editing?.let { route -> RouteForm(route, { viewModel.update(route, it); editing = null }, { editing = null }) }
    managing?.let { route -> RouteCustomersDialog(route, customers, assignments, { viewModel.assign(route, it) }, viewModel::unassign, viewModel::move) { viewModel.selectRoute(null); managing = null } }
}

@Composable
private fun RouteCard(route: CommercialRoute, onCustomers: () -> Unit, onActive: (Boolean) -> Unit, onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (route.isActive) BSPOSTheme.colors.successLight else BSPOSTheme.colors.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocationOn, null, tint = if (route.isActive) BSPOSTheme.colors.success else BSPOSTheme.colors.textSecondary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(route.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(route.code, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(route.description ?: "Sin descripción", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Switch(route.isActive, onActive)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onCustomers) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(5.dp)); Text("Clientes") }
                IconButton(onEdit) { Icon(Icons.Default.Edit, "Editar", tint = BSPOSTheme.colors.primary) }
            }
        }
    }
}

@Composable
private fun RouteCustomersDialog(route: CommercialRoute, customers: List<Customer>, assignments: List<RouteCustomer>, onAssign: (java.util.UUID) -> Unit, onUnassign: (java.util.UUID) -> Unit, onMove: (java.util.UUID, Int) -> Unit, onDismiss: () -> Unit) {
    val assigned = assignments.map { it.customerId }.toSet()
    val available = customers.filter { it.id !in assigned && it.isActive }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Clientes de ${route.name}") }, text = {
        DialogScrollableColumn {
            if (assignments.isEmpty()) Text("Aún no hay clientes asignados.", color = BSPOSTheme.colors.textSecondary)
            assignments.forEachIndexed { index, assignment ->
                val customer = customers.find { it.id == assignment.customerId }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = BSPOSTheme.colors.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(customer?.businessName ?: "Cliente", Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({ onMove(assignment.customerId, -1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "Subir") }
                    IconButton({ onMove(assignment.customerId, 1) }, enabled = index < assignments.lastIndex) { Icon(Icons.Default.ArrowDownward, "Bajar") }
                    IconButton({ onUnassign(assignment.customerId) }) { Icon(Icons.Default.RemoveCircleOutline, "Quitar", tint = BSPOSTheme.colors.error) }
                }
            }
            if (available.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text("Agregar cliente", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                available.forEach { customer -> TextButton({ onAssign(customer.id) }, Modifier.fillMaxWidth()) { Text(customer.businessName, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Listo") } })
}

@Composable
private fun RouteForm(current: CommercialRoute?, onSave: (RouteInput) -> Unit, onDismiss: () -> Unit) {
    var name by remember(current?.id) { mutableStateOf(current?.name.orEmpty()) }
    var code by remember(current?.id) { mutableStateOf(current?.code.orEmpty()) }
    var description by remember(current?.id) { mutableStateOf(current?.description.orEmpty()) }
    var error by remember(current?.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (current == null) "Nueva ruta" else "Editar ruta") }, text = {
        DialogScrollableColumn {
            OutlinedTextField(name, { name = it; error = false }, label = { Text("Nombre") }, isError = error)
            OutlinedTextField(code, { code = it; error = false }, label = { Text("Código") }, isError = error)
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, minLines = 2)
            if (error) Text("El nombre y el código son obligatorios.", color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ if (name.isBlank() || code.isBlank()) error = true else onSave(RouteInput(name, code, description)) }) { Text("Guardar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun BoxBadge() {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.LocationOn, null, tint = BSPOSTheme.colors.primary) }
}
