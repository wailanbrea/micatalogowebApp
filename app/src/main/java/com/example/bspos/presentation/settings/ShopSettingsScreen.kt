package com.example.bspos.presentation.settings

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.ShopHoursDto
import com.example.bspos.data.micatalogo.dto.ShopSettingsDto
import com.example.bspos.data.micatalogo.dto.ShopSettingsUpdateDto
import coil.compose.AsyncImage

@Composable
fun ShopSettingsScreen(
    modifier: Modifier = Modifier,
    initialSection: String? = null,
    viewModel: ShopSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    when {
        state.loading -> androidx.compose.foundation.layout.Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BSPOSTheme.colors.primary)
        }
        state.settings == null -> androidx.compose.foundation.layout.Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "No se pudo cargar la configuración.", color = BSPOSTheme.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.load() }) { Text("Reintentar") }
            }
        }
        else -> ShopSettingsContent(state.settings!!, state.saving, viewModel::save, viewModel::uploadLogo, viewModel::load, modifier, initialSection)
    }
}

@Composable
private fun ShopSettingsContent(
    initial: ShopSettingsDto,
    saving: Boolean,
    onSave: (ShopSettingsUpdateDto) -> Unit,
    onUploadLogo: (String, String?, ByteArray) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier,
    initialSection: String?
) {
    var section by remember(initial, initialSection) { mutableStateOf(initialSection ?: "General") }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var businessType by remember(initial) { mutableStateOf(initial.businessType) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var address by remember(initial) { mutableStateOf(initial.address) }
    var mapsUrl by remember(initial) { mutableStateOf(initial.mapsUrl) }
    var instagram by remember(initial) { mutableStateOf(initial.instagram) }
    var countryCode by remember(initial) { mutableStateOf(initial.whatsappCountryCode) }
    var whatsapp by remember(initial) { mutableStateOf(initial.whatsappNumber) }
    var shipping by remember(initial) { mutableStateOf(initial.offersShipping) }
    var primaryColor by remember(initial) { mutableStateOf(initial.primaryColor) }
    var secondaryColor by remember(initial) { mutableStateOf(initial.secondaryColor) }
    var hours by remember(initial) { mutableStateOf(initial.businessHours.withDefaults()) }
    var operational by remember(initial) { mutableStateOf(initial.operationalSettings) }
    val context = LocalContext.current
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.size > 2 * 1024 * 1024) {
            com.example.bspos.presentation.common.UiErrorBus.show("El logo debe ser una imagen de hasta 2 MB.")
        } else {
            onUploadLogo(context.displayName(uri), context.contentResolver.getType(uri), bytes)
        }
    }
    val sections = listOf(
        "General", "Rubros", "Recibo", "Comprobantes fiscales", "Cuentas bancarias", "Crédito",
        "Encargos", "Servicio rápido", "Recetas", "Tienda mayorista", "Compras", "Envíos",
        "Decants", "Vitrinas", "Imágenes", "Negocio", "Contacto y horario", "Apariencia", "Catálogo", "Anuncios", "Google"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Actualizar", tint = BSPOSTheme.colors.primary) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEach { value -> FilterChip(selected = section == value, onClick = { section = value }, label = { Text(value, maxLines = 1) }) }
            }
        }
        when (section) {
            "General" -> {
                item {
                    SettingsCard("Datos principales", "Se muestran en tu tienda y en los comprobantes.") {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre de la tienda") }, singleLine = true)
                        BusinessTypeField(initial.businessTypes, businessType) { businessType = it }
                        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descripción") }, minLines = 3)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(operational.currency, { operational = operational.copy(currency = it.uppercase().take(3)) }, Modifier.weight(0.35f), label = { Text("Moneda") }, singleLine = true)
                            OutlinedTextField(operational.currencySymbol, { operational = operational.copy(currencySymbol = it.take(5)) }, Modifier.weight(0.65f), label = { Text("Símbolo") }, singleLine = true)
                        }
                        OutlinedTextField(operational.timezone, { operational = operational.copy(timezone = it.trim()) }, Modifier.fillMaxWidth(), label = { Text("Zona horaria") }, singleLine = true)
                        OutlinedTextField(operational.taxRate?.toString().orEmpty(), { operational = operational.copy(taxRate = it.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0)) }, Modifier.fillMaxWidth(), label = { Text("ITBIS (%)") }, singleLine = true)
                        OutlinedTextField(operational.businessRnc.orEmpty(), { operational = operational.copy(businessRnc = it.ifBlank { null }) }, Modifier.fillMaxWidth(), label = { Text("RNC (opcional)") }, singleLine = true)
                        OutlinedTextField(operational.employeeCount?.toString().orEmpty(), { operational = operational.copy(employeeCount = it.filter(Char::isDigit).toIntOrNull()) }, Modifier.fillMaxWidth(), label = { Text("Empleados") }, singleLine = true)
                        Text("Métodos de pago", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                        val activePaymentMethods = initial.availablePaymentMethods.filter { operational.paymentMethods.contains(it.key) }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            activePaymentMethods.forEachIndexed { index, method ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}. ${method.label}", Modifier.weight(1f), color = BSPOSTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                    TextButton(onClick = { if (index > 0) operational = operational.copy(paymentMethods = movePaymentMethod(operational.paymentMethods, index, -1)) }, enabled = index > 0) { Text("↑") }
                                    TextButton(onClick = { if (index < activePaymentMethods.lastIndex) operational = operational.copy(paymentMethods = movePaymentMethod(operational.paymentMethods, index, 1)) }, enabled = index < activePaymentMethods.lastIndex) { Text("↓") }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            initial.availablePaymentMethods.forEach { method ->
                                FilterChip(
                                    selected = operational.paymentMethods.contains(method.key),
                                    onClick = { if (method.key != "cash") operational = operational.copy(paymentMethods = operational.paymentMethods.toggle(method.key)) },
                                    label = { Text(method.label) }
                                )
                            }
                        }
                    }
                }
            }
            "Rubros" -> item {
                SettingsCard("Tipo de negocio", "El rubro adapta términos, módulos y acciones disponibles.") {
                    BusinessTypeField(initial.businessTypes, businessType) { businessType = it }
                }
            }
            "Recibo" -> item {
                SettingsCard("Contenido del recibo", "Define la información que se imprime o comparte después de cobrar.") {
                    SettingSwitchRow("Mostrar logo", operational.receipt.showLogo) { operational = operational.copy(receipt = operational.receipt.copy(showLogo = it)) }
                    SettingSwitchRow("Mostrar cliente", operational.receipt.showCustomer) { operational = operational.copy(receipt = operational.receipt.copy(showCustomer = it)) }
                    SettingSwitchRow("Mostrar vendedor", operational.receipt.showSeller) { operational = operational.copy(receipt = operational.receipt.copy(showSeller = it)) }
                    SettingSwitchRow("Mostrar notas", operational.receipt.showNotes) { operational = operational.copy(receipt = operational.receipt.copy(showNotes = it)) }
                }
            }
            "Comprobantes fiscales" -> item {
                SettingsCard("Comprobantes fiscales", "Prepara la tienda para documentar comprobantes cuando completes sus datos fiscales.") {
                    SettingSwitchRow("Activar comprobantes fiscales", operational.fiscal.enabled) { operational = operational.copy(fiscal = operational.fiscal.copy(enabled = it)) }
                    OutlinedTextField(operational.fiscal.invoiceType, { operational = operational.copy(fiscal = operational.fiscal.copy(invoiceType = it)) }, Modifier.fillMaxWidth(), label = { Text("Tipo de comprobante") }, singleLine = true)
                }
            }
            "Cuentas bancarias" -> item {
                SettingsCard("Cuentas bancarias", "Estas cuentas aparecen como referencia cuando cobras por transferencia. Puedes agregarlas o editarlas desde la configuración web de la tienda.") {
                    if (initial.paymentAccounts.isEmpty()) {
                        Text("No hay cuentas bancarias activas. El método Transferencia bancaria sí está disponible en el POS.", color = BSPOSTheme.colors.textSecondary)
                    } else {
                        initial.paymentAccounts.forEach { account ->
                            Column(Modifier.fillMaxWidth().background(BSPOSTheme.colors.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Text(account.name, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                                Text(listOfNotNull(account.bankName, account.accountNumber).joinToString(" · ").ifBlank { "Datos bancarios pendientes" }, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                account.accountHolder?.takeIf { it.isNotBlank() }?.let { Text("Titular: $it", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                                account.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
            "Crédito" -> item {
                SettingsCard("Crédito y abonos", "Controla si la tienda puede vender a crédito y cómo se propone el vencimiento.") {
                    SettingSwitchRow("Permitir crédito", operational.credit.enabled) { operational = operational.copy(credit = operational.credit.copy(enabled = it)) }
                    OutlinedTextField(operational.credit.defaultDays.toString(), { operational = operational.copy(credit = operational.credit.copy(defaultDays = it.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 3650) ?: 0)) }, Modifier.fillMaxWidth(), label = { Text("Días predeterminados") }, singleLine = true)
                    SettingSwitchRow("Permitir abonos parciales", operational.credit.allowPartialPayments) { operational = operational.copy(credit = operational.credit.copy(allowPartialPayments = it)) }
                }
            }
            "Encargos" -> item { ToggleSettingsCard("Encargos", "Define si los pedidos requieren confirmación antes de procesarse.", operational.orders.enabled) { operational = operational.copy(orders = operational.orders.copy(enabled = it)) } }
            "Servicio rápido" -> item { ToggleSettingsCard("Servicio rápido", "Activa el flujo rápido para registrar servicios sin inventario.", operational.quickService.enabled) { operational = operational.copy(quickService = operational.quickService.copy(enabled = it)) } }
            "Recetas" -> item { ToggleSettingsCard("Recetas", "Activa recetas para negocios que trabajan con insumos y preparaciones.", operational.recipes.enabled) { operational = operational.copy(recipes = operational.recipes.copy(enabled = it)) } }
            "Tienda mayorista" -> item {
                SettingsCard("Tienda mayorista", "Configura ventas por volumen sin alterar el precio minorista.") {
                    SettingSwitchRow("Activar mayorista", operational.wholesale.enabled) { operational = operational.copy(wholesale = operational.wholesale.copy(enabled = it)) }
                    OutlinedTextField(operational.wholesale.minimumQuantity.toString(), { operational = operational.copy(wholesale = operational.wholesale.copy(minimumQuantity = it.filter(Char::isDigit).toIntOrNull()?.coerceAtLeast(1) ?: 1)) }, Modifier.fillMaxWidth(), label = { Text("Cantidad mínima") }, singleLine = true)
                }
            }
            "Compras" -> item {
                SettingsCard("Compras e inventario", "Estas reglas protegen la recepción de lotes y la trazabilidad del costo.") {
                    SettingSwitchRow("Permitir recepción parcial", operational.purchases.allowPartialReceive) { operational = operational.copy(purchases = operational.purchases.copy(allowPartialReceive = it)) }
                    SettingSwitchRow("Exigir suplidor", operational.purchases.requireSupplier) { operational = operational.copy(purchases = operational.purchases.copy(requireSupplier = it)) }
                }
            }
            "Envíos" -> item { ToggleSettingsCard("Envíos", "Muestra las opciones de retiro y entrega en el flujo de pedidos.", operational.shipping.enabled) { operational = operational.copy(shipping = operational.shipping.copy(enabled = it)) } }
            "Decants" -> item {
                SettingsCard("Decants", "Configura las presentaciones de ml que se ofrecen desde una botella origen.") {
                    SettingSwitchRow("Activar decants", operational.decants.enabled) { operational = operational.copy(decants = operational.decants.copy(enabled = it)) }
                    OutlinedTextField(operational.decants.defaultMl.joinToString(", "), { operational = operational.copy(decants = operational.decants.copy(defaultMl = it.split(',').mapNotNull { value -> value.trim().toIntOrNull()?.takeIf { ml -> ml > 0 } }.distinct())) }, Modifier.fillMaxWidth(), label = { Text("Tamaños predeterminados (ml)") }, singleLine = true)
                }
            }
            "Vitrinas" -> item {
                SettingsCard("Vitrinas", "Controla las entradas especiales de tu tienda pública.") {
                    SettingSwitchRow("Activar tienda mayorista", operational.wholesale.enabled) { operational = operational.copy(wholesale = operational.wholesale.copy(enabled = it)) }
                    SettingSwitchRow("Mostrar decants en la tienda", operational.decants.enabled) { operational = operational.copy(decants = operational.decants.copy(enabled = it)) }
                    SettingSwitchRow("Abrir el enlace en Decants", operational.decants.asCover) { operational = operational.copy(decants = operational.decants.copy(asCover = it)) }
                    OutlinedTextField(operational.decants.sectionText.orEmpty(), { operational = operational.copy(decants = operational.decants.copy(sectionText = it.ifBlank { null })) }, Modifier.fillMaxWidth(), label = { Text("Texto de la sección de decants") }, minLines = 2)
                    val storeUrl = initial.slug.trim().takeIf { it.isNotBlank() }?.let { "https://micatalogo.bsolutions.dev/tienda/$it" }
                    if (storeUrl != null) {
                        Text("Enlace directo a tu tienda", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                        Text(storeUrl, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Vitrina pública", storeUrl))
                                com.example.bspos.presentation.common.UiErrorBus.show("Enlace copiado.")
                            }, modifier = Modifier.weight(1f)) { Text("Copiar") }
                            Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))) }, modifier = Modifier.weight(1f)) { Text("Ver tienda") }
                        }
                        OutlinedButton(onClick = {
                            val metricsUrl = "https://micatalogo.bsolutions.dev/panel/tiendas/${initial.id}/metricas"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(metricsUrl)))
                        }, modifier = Modifier.fillMaxWidth()) { Text("Ver métricas y QR") }
                    }
                }
            }
            "Imágenes" -> item {
                SettingsCard("Imágenes de productos", "Mantén imágenes consistentes y optimizadas para el catálogo y el POS.") {
                    OutlinedTextField(operational.images.maxPerProduct.toString(), { operational = operational.copy(images = operational.images.copy(maxPerProduct = it.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 20) ?: 1)) }, Modifier.fillMaxWidth(), label = { Text("Máximo por producto") }, singleLine = true)
                    SettingSwitchRow("Optimizar automáticamente", operational.images.autoOptimize) { operational = operational.copy(images = operational.images.copy(autoOptimize = it)) }
                }
            }
            "Negocio" -> {
                item {
                    SettingsCard("Perfil del negocio", "El rubro adapta menús, términos y funciones disponibles.") {
                        BusinessTypeField(initial.businessTypes, businessType) { businessType = it }
                        Text("Los datos de contacto y las redes se administran en Contacto y horario para que el cliente los encuentre juntos.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            "Contacto y horario" -> {
                item {
                    SettingsCard("Contacto", "El cliente usará estos datos para preguntarte o pedir por WhatsApp.") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(countryCode, { countryCode = it.filter(Char::isDigit) }, Modifier.weight(0.34f), label = { Text("Código") }, singleLine = true)
                            OutlinedTextField(whatsapp, { whatsapp = it }, Modifier.weight(0.66f), label = { Text("WhatsApp") }, singleLine = true)
                        }
                        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Dirección") }, singleLine = true)
                        OutlinedTextField(mapsUrl, { mapsUrl = it }, Modifier.fillMaxWidth(), label = { Text("Enlace de Google Maps") }, supportingText = { Text("Opcional. Usa el enlace Compartir de Google Maps.") }, singleLine = true)
                        OutlinedTextField(instagram, { instagram = it }, Modifier.fillMaxWidth(), label = { Text("Instagram") }, singleLine = true)
                        SettingSwitchRow("Ofrecemos envíos", shipping) { shipping = it }
                    }
                }
                item {
                    SettingsCard("Horario", "Indica cuándo estás abierto y cuándo pueden escribirte.") {
                        TextButton(
                            onClick = {
                                val monday = hours["monday"] ?: ShopHoursDto()
                                hours = hours + listOf("tuesday", "wednesday", "thursday", "friday", "saturday").associateWith { monday }
                            },
                            enabled = hours.containsKey("monday")
                        ) { Text("Copiar el lunes de lunes a sábado") }
                        hours.toSortedMap().forEach { (day, dayHours) ->
                            HourRow(day, dayHours) { updated -> hours = hours + (day to updated) }
                        }
                    }
                }
            }
            "Apariencia" -> {
                item {
                    SettingsCard("Logo", "Cuadrado se ve mejor. JPG, PNG o WebP, hasta 2 MB.") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) {
                                if (!initial.logoUrl.isNullOrBlank()) {
                                    AsyncImage(initial.logoUrl, initial.name, Modifier.size(76.dp), contentScale = ContentScale.Crop)
                                } else {
                                    Text(initial.name.take(1).uppercase().ifBlank { "M" }, Modifier.padding(24.dp), fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (initial.logoUrl.isNullOrBlank()) "Todavía sin logo" else "Logo actual", fontWeight = FontWeight.Bold)
                                Text("Aparece en tu tienda pública y ayuda a reconocer tu negocio.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { logoPicker.launch("image/*") }, enabled = !saving) { Text("Subir logo") }
                            }
                        }
                    }
                }
                item {
                    SettingsCard("Color de tu tienda", "El color principal pinta botones, carrito y filtros de la vitrina pública.") {
                        val presets = listOf(
                            "Azul" to "#1D4ED8",
                            "Vino" to "#9F1239",
                            "Tinta" to "#0F172A",
                            "Verde" to "#15803D",
                            "Rosa" to "#DB2777",
                            "Morado" to "#7E22CE",
                            "Turquesa" to "#0F766E"
                        )
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.forEach { (label, color) ->
                                FilterChip(selected = primaryColor.equals(color, ignoreCase = true), onClick = { primaryColor = color }, label = { Text(label) })
                            }
                        }
                        OutlinedTextField(primaryColor, { primaryColor = it }, Modifier.fillMaxWidth(), label = { Text("Color principal (#RRGGBB)") }, singleLine = true)
                        OutlinedTextField(secondaryColor, { secondaryColor = it }, Modifier.fillMaxWidth(), label = { Text("Color secundario (#RRGGBB)") }, singleLine = true)
                    }
                }
                item {
                    SettingsCard("Sobre tu negocio", "Esta descripción aparece al pie de tu tienda pública.") {
                        OutlinedTextField(description, { description = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Descripción") }, minLines = 4, supportingText = { Text("${description.length}/500") })
                    }
                }
            }
            "Catálogo" -> {
                item {
                    SettingsCard("Presentación del catálogo", "Los productos, imágenes y disponibilidad se administran desde Inventario.") {
                        OutlinedTextField(operational.catalog.sort, { operational = operational.copy(catalog = operational.catalog.copy(sort = it)) }, Modifier.fillMaxWidth(), label = { Text("Orden inicial (name_asc, recent, price_asc, price_desc)") }, singleLine = true)
                        SettingSwitchRow("Ofertas primero", operational.catalog.offersFirst) { operational = operational.copy(catalog = operational.catalog.copy(offersFirst = it)) }
                        SettingSwitchRow("Ocultar productos agotados", operational.catalog.hideOutOfStock) { operational = operational.copy(catalog = operational.catalog.copy(hideOutOfStock = it)) }
                        SettingSwitchRow("Mostrar existencias", operational.catalog.showStock) { operational = operational.copy(catalog = operational.catalog.copy(showStock = it)) }
                        SettingSwitchRow("Vender por encargo", operational.catalog.allowBackorder) { operational = operational.copy(catalog = operational.catalog.copy(allowBackorder = it)) }
                        Text("La tienda pública se actualiza automáticamente al guardar productos o cambiar precios.", color = BSPOSTheme.colors.textSecondary)
                    }
                }
            }
            "Anuncios" -> item {
                SettingsCard("Mide tus anuncios", "Los píxeles se cargan solo después de aceptar las cookies.") {
                    OutlinedTextField(operational.marketing.metaPixel.orEmpty(), { operational = operational.copy(marketing = operational.marketing.copy(metaPixel = it.ifBlank { null })) }, Modifier.fillMaxWidth(), label = { Text("Meta Pixel") }, singleLine = true)
                    OutlinedTextField(operational.marketing.tiktokPixel.orEmpty(), { operational = operational.copy(marketing = operational.marketing.copy(tiktokPixel = it.ifBlank { null })) }, Modifier.fillMaxWidth(), label = { Text("TikTok Pixel") }, singleLine = true)
                    OutlinedTextField(operational.marketing.ga4.orEmpty(), { operational = operational.copy(marketing = operational.marketing.copy(ga4 = it.ifBlank { null })) }, Modifier.fillMaxWidth(), label = { Text("Google Analytics (GA4)") }, singleLine = true)
                }
            }
            "Google" -> item {
                SettingsCard("Qué tan fácil te encuentra Google", "Completa la información pública de tu negocio y verifica tu dominio cuando estés listo.") {
                    val google = initial.google
                    Text("${google.score} / 100 · ${google.pendingCount} pendiente(s)", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.primary)
                    LinearProgressIndicator(
                        progress = { (google.score / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = BSPOSTheme.colors.primary,
                        trackColor = BSPOSTheme.colors.surfaceVariant
                    )
                    google.checks.forEach { check ->
                        Card(colors = CardDefaults.cardColors(containerColor = if (check.done) BSPOSTheme.colors.primaryLight else BSPOSTheme.colors.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (check.done) "✓ ${check.label}" else check.label, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                                Text(check.description, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                if (!check.done) {
                                    TextButton(onClick = { section = check.section }) { Text("Completar en ${check.section}") }
                                }
                            }
                        }
                    }
                    Text("Google Search Console", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                    OutlinedTextField(operational.marketing.googleSiteVerification.orEmpty(), { operational = operational.copy(marketing = operational.marketing.copy(googleSiteVerification = it.ifBlank { null })) }, Modifier.fillMaxWidth(), label = { Text("Etiqueta de Google") }, minLines = 2)
                    Text("Entra a Search Console, agrega tu tienda como prefijo de URL y pega aquí la etiqueta HTML completa.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Button(
                onClick = {
                    onSave(ShopSettingsUpdateDto(name.trim(), businessType, description.trim(), address.trim(), mapsUrl.trim().ifBlank { null }, instagram.trim().ifBlank { null }, countryCode.trim(), whatsapp.trim(), shipping, primaryColor.trim(), secondaryColor.trim(), hours, operational))
                },
                enabled = !saving && name.trim().isNotBlank() && countryCode.trim().isNotBlank() && whatsapp.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (saving) "Guardando…" else "Guardar cambios") }
        }
    }
}

private fun Context.displayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0).orEmpty().ifBlank { "logo" }
    }
    return "logo"
}

@Composable
private fun SettingsCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
            Text(description, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun ToggleSettingsCard(title: String, description: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingsCard(title, description) { SettingSwitchRow("Activar módulo", enabled, onChange) }
}

private fun movePaymentMethod(methods: List<String>, index: Int, direction: Int): List<String> {
    val target = index + direction
    if (index !in methods.indices || target !in methods.indices) return methods
    return methods.toMutableList().also { values ->
        val value = values[index]
        values[index] = values[target]
        values[target] = value
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BusinessTypeField(options: List<com.example.bspos.data.micatalogo.dto.ShopTypeOptionDto>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tipo de negocio", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option -> FilterChip(selected = selected == option.key, onClick = { onSelect(option.key) }, label = { Text(option.label) }) }
        }
    }
}

@Composable
private fun HourRow(day: String, value: ShopHoursDto, onChange: (ShopHoursDto) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(day.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value.open.orEmpty(), { onChange(value.copy(open = it)) }, Modifier.weight(1f), label = { Text("Abre") }, singleLine = true, enabled = !value.closed && !value.allDay)
            OutlinedTextField(value.close.orEmpty(), { onChange(value.copy(close = it)) }, Modifier.weight(1f), label = { Text("Cierra") }, singleLine = true, enabled = !value.closed && !value.allDay)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("24 horas", color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.bodySmall)
                Switch(checked = value.allDay, onCheckedChange = { onChange(value.copy(allDay = it, closed = false)) })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cerrado", color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.bodySmall)
                Switch(checked = value.closed, onCheckedChange = { onChange(value.copy(closed = it, allDay = false)) })
            }
        }
    }
}

private fun Map<String, ShopHoursDto>.withDefaults(): Map<String, ShopHoursDto> {
    val days = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    return days.associateWith { this[it] ?: ShopHoursDto() }
}

private fun List<String>.toggle(value: String): List<String> = if (contains(value)) filterNot { it == value } else this + value
