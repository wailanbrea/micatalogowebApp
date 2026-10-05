package com.example.bspos.presentation.adminshops

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.MiCatalogoManagedShop

@Composable
fun AdminShopsScreen(viewModel: AdminShopsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("all") }
    var editingShop by remember { mutableStateOf<MiCatalogoManagedShop?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    val visibleShops = state.shops.filter { shop ->
        (status == "all" || shop.status == status) && listOf(shop.name, shop.slug, shop.ownerName, shop.ownerEmail)
            .any { it.contains(query, ignoreCase = true) }
    }

    Column(
        Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Store, null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Administración de plataforma", color = BSPOSTheme.colors.textSecondary)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar tienda o propietario") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(status == "all", { status = "all" }, label = { Text("Todas") })
            FilterChip(status == "active", { status = "active" }, label = { Text("Activas") })
            FilterChip(status == "suspended", { status = "suspended" }, label = { Text("Suspendidas") })
        }
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleShops, key = { it.id }) { shop ->
                    AdminShopCard(shop) { editingShop = shop }
                }
                if (visibleShops.isEmpty()) item { Text("No hay tiendas para este filtro.", color = BSPOSTheme.colors.textSecondary) }
            }
        }
        state.error?.let { Text(it, color = BSPOSTheme.colors.error) }
    }
    editingShop?.let { shop ->
        EditAdminShopDialog(
            shop = shop,
            saving = state.savingShopId == shop.id,
            onDismiss = { if (state.savingShopId == null) editingShop = null },
            onSave = { viewModel.save(it) { editingShop = null } }
        )
    }
}

@Composable
private fun AdminShopCard(shop: MiCatalogoManagedShop, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shop.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(if (shop.status == "active") "Activa" else "Suspendida", color = if (shop.status == "active") BSPOSTheme.colors.success else BSPOSTheme.colors.error)
            }
            Text("${shop.ownerName} · ${shop.ownerEmail}", color = BSPOSTheme.colors.textSecondary)
            Text("${shop.productCount} productos · ${shop.slug}", color = BSPOSTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun EditAdminShopDialog(shop: MiCatalogoManagedShop, saving: Boolean, onDismiss: () -> Unit, onSave: (MiCatalogoManagedShop) -> Unit) {
    var name by remember(shop.id) { mutableStateOf(shop.name) }
    var countryCode by remember(shop.id) { mutableStateOf(shop.whatsappCountryCode) }
    var number by remember(shop.id) { mutableStateOf(shop.whatsappNumber) }
    var status by remember(shop.id) { mutableStateOf(shop.status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar tienda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(shop.ownerEmail, color = BSPOSTheme.colors.textSecondary)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(countryCode, { countryCode = it }, Modifier.fillMaxWidth(), label = { Text("Código WhatsApp") }, singleLine = true)
                OutlinedTextField(number, { number = it }, Modifier.fillMaxWidth(), label = { Text("WhatsApp") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(status == "active", { status = "active" }, label = { Text("Activa") })
                    FilterChip(status == "suspended", { status = "suspended" }, label = { Text("Suspender") })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(shop.copy(name = name, whatsappCountryCode = countryCode, whatsappNumber = number, status = status)) },
                enabled = !saving && name.isNotBlank() && countryCode.isNotBlank() && number.isNotBlank()
            ) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } }
    )
}
