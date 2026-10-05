package com.example.bspos.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.InvoiceConfig
import com.example.bspos.domain.model.MiCatalogoConnectionState
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.SellerMenuOptions
import com.example.bspos.domain.model.MiCatalogoSeller

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val miCatalogoConnection by viewModel.miCatalogoConnection.collectAsState()
    val miCatalogoUi by viewModel.miCatalogoUi.collectAsState()
    val pendingOperations by viewModel.pendingOperations.collectAsState()
    val pendingSales by viewModel.pendingSales.collectAsState()
    val pendingPayments by viewModel.pendingPayments.collectAsState()
    val pendingCatalogs by viewModel.pendingCatalogs.collectAsState()
    LaunchedEffect(miCatalogoConnection.isConfigured) {
        if (miCatalogoConnection.isConfigured) viewModel.loadShops()
    }
    Column(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(BSPOSTheme.colors.primaryLight), contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, null, tint = BSPOSTheme.colors.primary) }
            Spacer(Modifier.width(12.dp))
            Column { Text("Preferencias operativas", color = BSPOSTheme.colors.textSecondary) }
        }
        settings?.let { current ->
            Text("Moneda", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            CurrencyCard(current.currency, viewModel::setCurrency)
            Text("Factura y PDF", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            InvoiceConfigCard(current.invoice, viewModel::saveInvoiceConfig)
            Text("Operación", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            SettingCard("Permitir stock negativo", "Al activarlo, ventas y ajustes podrán superar la existencia disponible.", Icons.Default.Inventory2, BSPOSTheme.colors.warning, BSPOSTheme.colors.warningLight, current.allowNegativeStock, viewModel::setAllowNegativeStock)
            SettingCard("Respaldos automáticos", "Activa la política de respaldo cuando esté configurada.", Icons.Default.Backup, BSPOSTheme.colors.success, BSPOSTheme.colors.successLight, current.automaticBackupsEnabled, viewModel::setAutomaticBackups)
            SettingCard("Módulo de rutas", "Habilita rutas comerciales, cargas y operaciones de distribución.", Icons.Default.Inventory2, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight, current.routesEnabled, viewModel::setRoutesEnabled)
         }
        miCatalogoUi.shops.filter { it.canManageSellers }.forEach { shop ->
            SellerCreateCard(shop, miCatalogoUi.isCreatingSeller, viewModel::createSeller)
            SellerMenuPermissionsCard(shop, miCatalogoUi.updatingSellerMenuId, viewModel::updateSellerMenus)
        }
        MiCatalogoConnectionCard(miCatalogoConnection, miCatalogoUi, viewModel::connectMiCatalogo, viewModel::logout, viewModel::loadShops, viewModel::syncShop, viewModel::syncPosSales)
        if (pendingOperations.isNotEmpty() || pendingSales.isNotEmpty() || pendingPayments.isNotEmpty()) {
            Text("Sincronización pendiente", fontWeight = FontWeight.Bold)
            Text("Un conflicto detiene las operaciones posteriores de esa tienda. Reintentar conserva el identificador y los importes originales; no corrige ni descarta un conteo o precio en conflicto.", style = MaterialTheme.typography.bodySmall)
            pendingSales.forEach { row -> PendingSyncCard("Venta · ${row.remoteShopId}",row.saleId.toString(),row.lastError,
                row.state == com.example.bspos.domain.model.PosSaleOutboxState.BLOCKED) { viewModel.retrySale(row.saleId) } }
            pendingOperations.forEach { row -> PendingSyncCard("Operación · ${row.shopId}",row.id,row.error,row.state == "BLOCKED") { viewModel.retryOperation(row.id) } }
            pendingPayments.forEach { row -> PendingSyncCard("Abono · ${row.shopId}",row.id,row.error,row.state == "BLOCKED") { viewModel.retryPayment(row.id) } }
        }
        pendingCatalogs.forEach { row -> PendingSyncCard("Descarga de catálogo · ${row.shopId}", row.revision,
            row.error ?: "La operación ya está confirmada; falta descargar precios y existencias actualizados.", true) { viewModel.syncPosSales() } }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.secondaryNavy)) { Column(Modifier.padding(18.dp)) { Text("MiCatalogo", color = BSPOSTheme.colors.textOnPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text("Tu catálogo en movimiento", color = BSPOSTheme.colors.textOnNavy); Spacer(Modifier.size(8.dp)); Text("Creada por BSolutions.Dev", color = BSPOSTheme.colors.primary) } }
    }
}

@Composable
private fun PendingSyncCard(title:String,id:String,error:String?,blocked:Boolean,onRetry:()->Unit) {
    Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title,fontWeight=FontWeight.Bold)
            androidx.compose.foundation.text.selection.SelectionContainer { Text("ID: $id",style=MaterialTheme.typography.bodySmall) }
            Text(if(blocked) "Requiere revisión" else "Pendiente de confirmación",color=if(blocked) BSPOSTheme.colors.error else BSPOSTheme.colors.warning)
            error?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            if(blocked) TextButton(onRetry) { Text("Reintentar sin cambiar los datos") }
        }
    }
}

@Composable
private fun SellerCreateCard(
    shop: MiCatalogoShop,
    isCreating: Boolean,
    onCreate: (MiCatalogoShop, String, String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var commissionType by remember { mutableStateOf("percentage") }
    var commissionValue by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Crear vendedor · ${shop.name}", fontWeight = FontWeight.ExtraBold)
            Text("Crea la cuenta y envía la invitación para que establezca su contraseña.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo del vendedor") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(commissionType == "percentage", { commissionType = "percentage" }, label = { Text("Porcentaje") })
                FilterChip(commissionType == "fixed", { commissionType = "fixed" }, label = { Text("Monto fijo") })
            }
            OutlinedTextField(commissionValue, { commissionValue = it }, Modifier.fillMaxWidth(), label = { Text("Comisión") }, singleLine = true)
            Button(
                onClick = { onCreate(shop, email, commissionType, commissionValue) },
                enabled = !isCreating && email.isNotBlank() && commissionValue.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isCreating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Crear vendedor")
            }
        }
    }
}

@Composable
private fun SellerMenuPermissionsCard(
    shop: MiCatalogoShop,
    updatingSellerMenuId: String?,
    onUpdate: (MiCatalogoShop, String, List<String>) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Menús de vendedores · ${shop.name}", fontWeight = FontWeight.ExtraBold)
            Text("Decide qué módulos puede ver cada vendedor en la web y en la app.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            shop.sellers.filter { it.isActive }.forEach { seller ->
                var enabled by remember(seller.id, seller.menuPermissions) { mutableStateOf(seller.menuPermissions.toSet()) }
                Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.background)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(seller.name, fontWeight = FontWeight.Bold)
                        Text(seller.email, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        SellerMenuOptions.forEach { option ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(option.label, Modifier.weight(1f))
                                Switch(
                                    checked = option.key in enabled,
                                    onCheckedChange = { checked ->
                                        enabled = if (checked) enabled + option.key else enabled - option.key
                                        onUpdate(shop, seller.id, enabled.toList())
                                    },
                                    enabled = updatingSellerMenuId == null || updatingSellerMenuId == seller.id
                                )
                            }
                        }
                        if (updatingSellerMenuId == seller.id) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
            if (shop.sellers.none { it.isActive }) {
                Text("No hay vendedores activos asignados a esta tienda.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MiCatalogoConnectionCard(
    connection: MiCatalogoConnectionState,
    state: MiCatalogoSettingsUiState,
    onConnect: (String, String) -> Unit,
    onLogout: () -> Unit,
    onReloadShops: () -> Unit,
    onSync: (MiCatalogoShop) -> Unit,
    onSyncPosSales: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MiCatalogo", fontWeight = FontWeight.ExtraBold)
            Text(
                if (connection.isConfigured) "La importacion no modifica el catalogo remoto. Las ventas POS solo envian cierres locales elegibles."
                else "Conecta una cuenta para importar un catalogo remoto en esta instalacion local.",
                color = BSPOSTheme.colors.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            if (connection.baseUrl.isNotBlank()) {
                Text(connection.baseUrl, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            if (!connection.isConfigured) {
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Contrasena") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(
                    onClick = { onConnect(email, password) },
                    enabled = !state.isConnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isConnecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Conectar MiCatalogo")
                }
            } else {
                Button(onClick = onReloadShops, enabled = !state.isLoadingShops, modifier = Modifier.fillMaxWidth()) {
                    if (state.isLoadingShops) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Actualizar tiendas")
                }
                Button(onClick = onSyncPosSales, enabled = !state.isSyncingPosSales, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSyncingPosSales) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Enviar ventas, entradas y devoluciones")
                }
                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesion") }
                state.shops.forEach { shop -> MiCatalogoShopCard(shop, state.syncingShopId == shop.id, state.syncingShopId != null, onSync) }
                if (!state.isLoadingShops && state.shops.isEmpty()) {
                    Text("No hay tiendas disponibles para esta cuenta.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            state.successMessage?.let { Text(it, color = BSPOSTheme.colors.success, style = MaterialTheme.typography.bodySmall) }
            state.errorMessage?.let { Text(it, color = BSPOSTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun MiCatalogoShopCard(
    shop: MiCatalogoShop,
    isSyncingThisShop: Boolean,
    isAnyShopSyncing: Boolean,
    onSync: (MiCatalogoShop) -> Unit
) {
    val shopContext = androidx.compose.ui.platform.LocalContext.current
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.background)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(shop.name, fontWeight = FontWeight.Bold)
                    shop.slug?.takeIf { it.isNotBlank() }?.let { Text(it, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall) }
                }
                Button(onClick = { onSync(shop) }, enabled = !isAnyShopSyncing) {
                    if (isSyncingThisShop) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Sincronizar")
                }
            }
            Button(onClick = {
                val uri = android.net.Uri.parse(com.example.bspos.BuildConfig.MICATALOGO_API_BASE_URL)
                    .buildUpon().appendPath("panel").appendPath("tiendas").appendPath(shop.id).appendPath("negocio").build()
                shopContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
            }) { Text("Reportes FIFO y precios Pro · Web") }
            Text("Los costos locales son estimados; consulta el costo FIFO confirmado en el panel. Propietarios y administradores pueden enviar productos, fotos y entradas. Las ventas y devoluciones se conservan hasta recibir confirmación; revisa los conflictos en Ajustes.", style = MaterialTheme.typography.bodySmall)
            shop.quota?.let { quota ->
                Text("Plan ${quota.planLabel}", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.primary)
                Text("${quota.productCount} de ${quota.productLimit} productos", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { if (quota.productLimit > 0) (quota.productCount.toFloat() / quota.productLimit).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (quota.canAddProducts) "${quota.productsRemaining} productos disponibles en el catálogo web"
                    else "Límite alcanzado: no se pueden agregar productos al catálogo web.",
                    color = if (quota.canAddProducts) BSPOSTheme.colors.textSecondary else BSPOSTheme.colors.warning,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("${quota.imageLimit} imágenes por producto", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                Text("Usuarios: ${quota.userCount}/${quota.userLimit} · Vendedores: ${quota.sellerCount}/${quota.sellerLimit}", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                Text("Cuenta adicional: US$ ${"%.2f".format(quota.additionalSeatPriceUsd)}/mes", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
            } ?: Text("Cuota remota no disponible. Actualiza las tiendas cuando el servidor esté actualizado.", style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun CurrencyCard(selected: CurrencyUnit, onChange: (CurrencyUnit) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Moneda de precios y operaciones", fontWeight = FontWeight.ExtraBold)
            Text("Los importes siguen guardándose como centavos; sólo cambia la moneda visual y de la factura.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CurrencyUnit.entries.forEach { currency -> FilterChip(selected == currency, { onChange(currency) }, label = { Text("${currency.symbol} ${currency.code}") }) } }
        }
    }
}

@Composable
private fun InvoiceConfigCard(current: InvoiceConfig, onSave: (InvoiceConfig) -> Unit) {
    var businessName by remember(current) { mutableStateOf(current.businessName) }
    var taxId by remember(current) { mutableStateOf(current.taxId) }
    var phone by remember(current) { mutableStateOf(current.phone) }
    var address by remember(current) { mutableStateOf(current.address) }
    var footer by remember(current) { mutableStateOf(current.footer) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = BSPOSTheme.colors.primary); Spacer(Modifier.width(8.dp)); Text("Datos que aparecerán en el PDF y factura", fontWeight = FontWeight.ExtraBold) }
            OutlinedTextField(businessName, { businessName = it }, Modifier.fillMaxWidth(), label = { Text("Nombre comercial") }, singleLine = true)
            OutlinedTextField(taxId, { taxId = it }, Modifier.fillMaxWidth(), label = { Text("RNC / ID fiscal") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Teléfono") }, singleLine = true)
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Dirección") }, minLines = 2)
            OutlinedTextField(footer, { footer = it }, Modifier.fillMaxWidth(), label = { Text("Pie de factura") }, minLines = 2)
            Button({ onSave(InvoiceConfig(businessName.trim().ifBlank { "MiCatalogo" }, taxId.trim(), phone.trim(), address.trim(), footer.trim().ifBlank { "Gracias por tu compra" })) }, Modifier.fillMaxWidth()) { Text("Guardar datos de factura") }
        }
    }
}

@Composable
private fun SettingCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: androidx.compose.ui.graphics.Color, soft: androidx.compose.ui.graphics.Color, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(soft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.ExtraBold); Text(description, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }; Switch(enabled, onChange) } }
}
