package com.example.bspos.presentation.printer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.BluetoothPrinter
import com.example.bspos.domain.model.ReceiptPaperWidth
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.common.UiErrorBus

private sealed interface BluetoothConnectAction {
    data object LoadPairedDevices : BluetoothConnectAction
    data class TestPrinter(val printer: BluetoothPrinter) : BluetoothConnectAction
}

private fun missingBluetoothPermissions(
    context: Context,
    action: BluetoothConnectAction
): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()

    val requiredPermissions = buildList {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        // print() calls cancelDiscovery() before connecting the SPP socket.
        if (action is BluetoothConnectAction.TestPrinter) {
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }
    return requiredPermissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun BluetoothPrinterScreen(viewModel: BluetoothPrinterViewModel = hiltViewModel()) {
    val printers by viewModel.printers.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    var showPairedDevices by remember { mutableStateOf(false) }
    var pendingBluetoothAction by remember { mutableStateOf<BluetoothConnectAction?>(null) }

    fun performBluetoothAction(action: BluetoothConnectAction) {
        when (action) {
            BluetoothConnectAction.LoadPairedDevices -> {
                viewModel.loadPairedDevices()
                showPairedDevices = true
            }
            is BluetoothConnectAction.TestPrinter -> viewModel.testPrint(action.printer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val action = pendingBluetoothAction
        pendingBluetoothAction = null
        if (action != null && missingBluetoothPermissions(context, action).isEmpty()) {
            performBluetoothAction(action)
        } else {
            UiErrorBus.show("Permiso Bluetooth denegado. Actívalo para usar impresoras vinculadas.")
        }
    }

    fun requireBluetoothPermissions(action: BluetoothConnectAction) {
        val missingPermissions = missingBluetoothPermissions(context, action)
        if (missingPermissions.isNotEmpty()) {
            pendingBluetoothAction = action
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            performBluetoothAction(action)
        }
    }

    LaunchedEffect(error) {
        if (error != null) viewModel.clearError()
    }

    Column(
        Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Print, null, tint = BSPOSTheme.colors.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Impresoras Bluetooth persistentes", color = BSPOSTheme.colors.textSecondary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Agregar impresora Bluetooth", fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Vincula primero la impresora desde Ajustes de Android y luego agrégala aquí.",
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        requireBluetoothPermissions(BluetoothConnectAction.LoadPairedDevices)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Bluetooth, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar impresoras vinculadas")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Impresoras guardadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (printers.isEmpty()) {
            Text("Todavía no hay una impresora configurada.", color = BSPOSTheme.colors.textSecondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(printers, key = { it.address }) { printer ->
                    PrinterCard(
                        printer,
                        onDefault = { viewModel.setDefault(printer) },
                        onDelete = { viewModel.remove(printer) },
                        onTest = { requireBluetoothPermissions(BluetoothConnectAction.TestPrinter(printer)) },
                        onPaperWidth = { viewModel.setPaperWidth(printer, it) }
                    )
                }
            }
        }
    }

    if (showPairedDevices) {
        AlertDialog(
            onDismissRequest = { showPairedDevices = false },
            title = { Text("Selecciona una impresora") },
            text = {
                if (pairedDevices.isEmpty()) {
                    Text("No hay dispositivos Bluetooth vinculados. Vincula una impresora en Ajustes de Android.")
                } else {
                    LazyColumn {
                        items(pairedDevices, key = { it.address }) { device ->
                            TextButton(
                                onClick = {
                                    viewModel.add(device)
                                    showPairedDevices = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(device.name, fontWeight = FontWeight.Bold)
                                    Text(device.address, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showPairedDevices = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun PrinterCard(
    printer: BluetoothPrinter,
    onDefault: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onPaperWidth: (ReceiptPaperWidth) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, null, tint = BSPOSTheme.colors.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        printer.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        printer.address,
                        color = BSPOSTheme.colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (printer.isDefault) {
                        Text("Predeterminada", color = BSPOSTheme.colors.success, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Papel", style = MaterialTheme.typography.labelSmall, color = BSPOSTheme.colors.textSecondary)
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReceiptPaperWidth.entries.forEach { width ->
                        FilterChip(
                            selected = printer.paperWidth == width,
                            onClick = { onPaperWidth(width) },
                            label = { Text(width.label) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTest) { Icon(Icons.Default.PlayArrow, "Probar impresora") }
                IconButton(onClick = onDefault) {
                    Icon(
                        Icons.Default.CheckCircle,
                        "Usar como predeterminada",
                        tint = if (printer.isDefault) BSPOSTheme.colors.success else Color.Gray
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar", tint = BSPOSTheme.colors.error) }
            }
        }
    }
}
