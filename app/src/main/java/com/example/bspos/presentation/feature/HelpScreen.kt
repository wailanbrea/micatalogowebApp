package com.example.bspos.presentation.feature

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import com.example.bspos.presentation.common.BSPOSButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.navigation.Screen

private data class HelpGuide(
    val category: String,
    val level: String,
    val title: String,
    val description: String,
    val steps: List<String>,
    val destination: Screen? = null
)

private val guides = listOf(
    HelpGuide("Empieza aquí", "Básico", "Primeros pasos", "Tu primer producto, tu primera venta y tu tienda, en ese orden.", listOf("Completa los datos de tu tienda.", "Crea un producto con costo, precio y existencia.", "Abre Terminal, cobra una venta y revisa el inventario."), Screen.Catalog),
    HelpGuide("Operación", "Básico", "Terminal", "Busca el producto, agrégalo al carrito y elige cómo te pagan.", listOf("Busca o filtra el producto.", "Revisa el carrito y selecciona contado o crédito.", "Confirma el cobro y revisa la factura."), Screen.POS),
    HelpGuide("Operación", "Básico", "Ventas", "Consulta el historial y el margen real de cada venta.", listOf("Abre Ventas desde el menú Operación.", "Filtra por período o método de pago.", "Abre una venta para revisar sus productos y factura."), Screen.SalesHistory),
    HelpGuide("Operación", "Básico", "Cotizaciones", "Prepara un presupuesto y conviértelo en venta cuando el cliente confirme.", listOf("Busca productos y agrégalos al carrito.", "Define cliente, vigencia y notas.", "Guarda, comparte o convierte la cotización."), Screen.Quotes),
    HelpGuide("Operación", "Básico", "Pedidos", "Confirma los pedidos recibidos desde tu catálogo público.", listOf("Revisa los productos y el cliente.", "Confirma el método de pago.", "Convierte el pedido en venta para descontar inventario."), Screen.Orders),
    HelpGuide("Catálogo", "Básico", "Productos", "Nombre, costo y precio: con eso ya sabes cuánto ganas.", listOf("Crea el producto y agrega una imagen.", "Registra la existencia como un lote con su costo.", "Revisa el precio y publícalo en la tienda."), Screen.Catalog),
    HelpGuide("Catálogo", "Intermedio", "Inventario y lotes", "Cada entrada conserva su costo y las ventas salen por FIFO.", listOf("Registra cada recepción como un lote.", "Usa movimientos para aumentos, pérdidas o conteos.", "Consulta el detalle para ver cantidades y costos."), Screen.Inventory),
    HelpGuide("Catálogo", "Intermedio", "Decants", "Vende perfume por mililitros sin perder la cuenta del costo de la botella.", listOf("Selecciona la botella fuente.", "Define presentación, ml, costo y precio.", "Vende el decant y revisa el ml restante y la recuperación del costo."), Screen.Decants),
    HelpGuide("Catálogo", "Básico", "Importar", "Carga tu inventario desde Excel o CSV y revísalo antes de guardar.", listOf("Elige archivo, catálogo existente o creación rápida.", "Mapea columnas y valida números e imágenes.", "Confirma una sola importación para evitar duplicados."), Screen.Import),
    HelpGuide("Cobros", "Básico", "Clientes y crédito", "Registra clientes, ventas a crédito y sus abonos.", listOf("Crea o selecciona el cliente.", "Confirma una venta a crédito desde Ventas.", "Registra abonos y revisa el saldo pendiente."), Screen.Customers),
    HelpGuide("Finanzas", "Básico", "Ganancias", "Lee ventas, costo FIFO, gastos y utilidad del período.", listOf("Selecciona un período.", "Compara ventas netas con costo y gastos.", "Abre el detalle por producto para decidir qué comprar."), Screen.Finance),
    HelpGuide("Finanzas", "Básico", "Gastos y caja", "Los gastos y movimientos de caja completan la visión financiera.", listOf("Abre una caja para el día.", "Registra entradas, retiros y gastos.", "Cierra el día comparando efectivo esperado y contado."), Screen.Cash),
    HelpGuide("Análisis", "Básico", "Reportes", "Consulta ventas, ganancia, cobros y stock bajo por período.", listOf("Elige la fecha inicial y final.", "Revisa los indicadores y la tabla.", "Exporta CSV o Excel para compartirlo."), Screen.Reports),
    HelpGuide("Configuración", "Básico", "Equipo y permisos", "Define quién entra a tu negocio y qué puede hacer.", listOf("Crea el vendedor desde Equipo.", "Asigna permisos por menú.", "Prueba el acceso con el rol del vendedor."), Screen.Team),
    HelpGuide("Configuración", "Básico", "Configuración de tienda", "Moneda, impuestos, comprobantes, crédito y catálogo público.", listOf("Completa la identidad de la tienda.", "Revisa métodos de pago y reglas.", "Guarda y valida la vista pública."), Screen.ShopSettings),
    HelpGuide("Tienda online", "Básico", "Mi tienda", "Tu catálogo público con carrito y pedidos por WhatsApp.", listOf("Configura portada, logo y contacto.", "Publica productos con precio e imagen.", "Abre el enlace público y prueba un pedido."), Screen.Storefront)
)

@Composable
fun HelpScreen(
    onOpen: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Todas") }
    var tutorialVisible by remember { mutableStateOf(false) }
    var selectedGuide by remember { mutableStateOf<HelpGuide?>(null) }
    val categories = listOf("Todas", "Empieza aquí", "Operación", "Catálogo", "Cobros", "Finanzas", "Análisis", "Tienda online", "Configuración")
    val filtered = remember(query, category) {
        val normalized = query.trim()
        guides.filter { guide ->
            (category == "Todas" || guide.category == category) &&
                (normalized.isBlank() || listOf(guide.title, guide.description, guide.category).any { it.contains(normalized, ignoreCase = true) })
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Guías cortas para aprender haciendo. Busca una función o empieza por lo más usado.", color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("¿Qué quieres hacer?") },
                placeholder = { Text("Ej.: vender, lote, decant") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Tutorial guiado", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                        Text("Te mostramos el recorrido básico sin crear ventas ni tocar tu inventario.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { tutorialVisible = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Iniciar")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { value ->
                    FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value, maxLines = 1) })
                }
            }
        }
        items(filtered, key = { "${it.category}-${it.title}" }) { guide ->
            Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(guide.category, color = BSPOSTheme.colors.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("· ${guide.level}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(guide.title, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(guide.description, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { selectedGuide = guide }) { Text("Ver pasos") }
                        guide.destination?.let { destination ->
                            Button(onClick = { onOpen(destination) }) {
                                Text("Abrir")
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text("No encontramos una guía con esos filtros.", color = BSPOSTheme.colors.textSecondary, modifier = Modifier.padding(12.dp))
            }
        }
    }

    if (tutorialVisible) {
        AlertDialog(
            onDismissRequest = { tutorialVisible = false },
            title = { Text("Tutorial guiado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Crea tu primer producto desde Productos.")
                    Text("2. Abre Terminal y agrégalo al carrito.")
                    Text("3. Revisa el total sin confirmar ninguna venta.")
                }
            },
            confirmButton = { TextButton(onClick = { tutorialVisible = false }) { Text("Entendido") } }
        )
    }

    selectedGuide?.let { guide ->
        AlertDialog(
            onDismissRequest = { selectedGuide = null },
            title = { Text(guide.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    guide.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                }
            },
            confirmButton = { TextButton(onClick = { selectedGuide = null }) { Text("Cerrar") } }
        )
    }
}
