package com.example.bspos.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bspos.core.ui.theme.BSPOSTheme

@Composable
fun MoreScreen(
    onSuppliers: () -> Unit,
    onInventory: () -> Unit,
    onCollections: () -> Unit,
    onCredit: () -> Unit,
    onCash: () -> Unit,
    onReturns: () -> Unit,
    onRouteLoads: () -> Unit,
    onPrinters: () -> Unit,
    onSettings: () -> Unit,
    onFinance: () -> Unit = {},
    showSuppliers: Boolean,
    showInventory: Boolean,
    showCollections: Boolean,
    showCredit: Boolean,
    showCash: Boolean,
    showReturns: Boolean,
    showRouteLoads: Boolean,
    showPrinters: Boolean,
    showSettings: Boolean,
    showFinance: Boolean = true
) {
    val actions = listOf(
        if (showFinance) MoreAction("Finanzas", "P&L, rentabilidad y flujo neto", Icons.Default.BarChart, Color(0xFF1565C0), Color(0xFFE3F2FD), onFinance) else null,
        if (showRouteLoads) MoreAction("Cargas de ruta", "Transferir inventario a rutas", Icons.Default.LocalShipping, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight, onRouteLoads) else null,
        if (showCash) MoreAction("Caja", "Apertura, arqueo y cierre", Icons.Default.AccountBalanceWallet, BSPOSTheme.colors.success, BSPOSTheme.colors.successLight, onCash) else null,
        if (showCredit) MoreAction("Credito", "Vender a clientes con saldo pendiente", Icons.Default.AccountBalanceWallet, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight, onCredit) else null,
        if (showCollections) MoreAction("Cobros", "Recibos y saldos de clientes", Icons.Default.ReceiptLong, BSPOSTheme.colors.warning, BSPOSTheme.colors.warningLight, onCollections) else null,
        if (showReturns) MoreAction("Devoluciones", "Reembolsos de ventas de contado", Icons.Default.Replay, BSPOSTheme.colors.error, BSPOSTheme.colors.errorLight, onReturns) else null,
        if (showInventory) MoreAction("Inventario", "Existencias, ajustes y Kardex", Icons.Default.Inventory2, Color(0xFF7656E8), Color(0xFFF0ECFF), onInventory) else null,
        if (showSuppliers) MoreAction("Proveedores", "Contactos y abastecimiento", Icons.Default.People, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight, onSuppliers) else null,
        if (showPrinters) MoreAction("Impresoras", "Bluetooth y facturas", Icons.Default.Bluetooth, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight, onPrinters) else null,
        if (showSettings) MoreAction("Ajustes", "Preferencias operativas", Icons.Default.Settings, BSPOSTheme.colors.primary, BSPOSTheme.colors.primaryLight, onSettings) else null
    ).filterNotNull()
    LazyColumn(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Operaciones", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("Herramientas para administrar tu negocio", color = BSPOSTheme.colors.textSecondary)
            Spacer(Modifier.height(6.dp))
        }
        items(actions.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { action -> MoreActionCard(action, Modifier.weight(1f)) }; if (row.size == 1) Spacer(Modifier.weight(1f)) }
        }
    }
}

private data class MoreAction(val title: String, val subtitle: String, val icon: ImageVector, val accent: Color, val soft: Color, val onClick: () -> Unit)

@Composable
private fun MoreActionCard(action: MoreAction, modifier: Modifier) { Card(onClick = action.onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), elevation = CardDefaults.cardElevation(1.dp)) { Column(Modifier.padding(16.dp)) { Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(action.soft), contentAlignment = Alignment.Center) { Icon(action.icon, null, tint = action.accent) }; Spacer(Modifier.height(14.dp)); Text(action.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium); Text(action.subtitle, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall) } } }
