package com.example.bspos.presentation.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bspos.core.ui.theme.BSPOSTheme

@Composable
fun SyncStatusBanner(
    status: SyncStatus,
    onRetry: () -> Unit,
    onResolve: () -> Unit = {}
) {
    if (status.phase == SyncPhase.SYNCED && status.count == 0 && status.error == null) return

    var showResolution by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val title = when (status.phase) {
        SyncPhase.CHECKING -> "Verificando sincronización…"
        SyncPhase.SYNCED -> "Sin operaciones pendientes"
        SyncPhase.PENDING -> "Pendiente de sincronización"
        SyncPhase.RETRY -> "Reintentando"
        SyncPhase.BLOCKED -> "Requiere revisión"
    }
    val color = when (status.phase) {
        SyncPhase.BLOCKED -> BSPOSTheme.colors.error
        SyncPhase.SYNCED -> BSPOSTheme.colors.success
        else -> BSPOSTheme.colors.textSecondary
    }
    Surface(color = BSPOSTheme.colors.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (status.count == 0) title else "$title · ${status.count}", style = MaterialTheme.typography.labelSmall, color = color)
                status.error?.take(240)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = BSPOSTheme.colors.error) }
            }
            TextButton(onClick = { showDetails = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Detalles", style = MaterialTheme.typography.labelSmall)
            }
            if (status.phase in setOf(SyncPhase.RETRY, SyncPhase.BLOCKED, SyncPhase.PENDING)) {
                if (status.phase == SyncPhase.BLOCKED) {
                    TextButton(onClick = { showResolution = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Resolver", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Reintentar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Sincronización de esta tienda") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, color = color)
                    if (status.phase == SyncPhase.CHECKING) {
                        Text("Estamos comprobando las operaciones guardadas en este dispositivo.")
                    } else {
                        SyncCountRow("Ventas", status.saleCount)
                        SyncCountRow("Cobros", status.paymentCount)
                        SyncCountRow("Cambios de catálogo e inventario", status.operationCount)
                        SyncCountRow("Actualizaciones de catálogo", status.refreshCount)
                        HorizontalDivider()
                        SyncCountRow("Total pendiente", status.count)
                        if (status.blockedCount > 0) {
                            Text("${status.blockedCount} requieren revisión antes de enviarse.", color = BSPOSTheme.colors.error)
                        }
                        Text("Este detalle muestra lo pendiente de esta tienda en este dispositivo. Aunque no haya pendientes, puede faltar conexión o una actualización del catálogo.", style = MaterialTheme.typography.bodySmall)
                        status.error?.let { Text(it, color = BSPOSTheme.colors.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("Cerrar") } },
            dismissButton = {
                if (status.phase == SyncPhase.BLOCKED) {
                    TextButton(onClick = { showDetails = false; showResolution = true }) { Text("Revisar") }
                } else if (status.count > 0) {
                    TextButton(onClick = onRetry) { Text("Reintentar") }
                }
            }
        )
    }
    if (showResolution) {
        AlertDialog(
            onDismissRequest = { showResolution = false },
            title = { Text("Operación detenida") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("La operación no se enviará de nuevo automáticamente para evitar duplicados o cambios incorrectos.")
                    Text("Motivo", style = MaterialTheme.typography.labelMedium, color = BSPOSTheme.colors.textSecondary)
                    Text(status.error ?: "El servidor pidió revisar esta operación.", color = BSPOSTheme.colors.error)
                    status.resolutionLabel?.let {
                        Text("Puedes revisar los datos en $it y volver a intentarlo cuando estén corregidos.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showResolution = false; onRetry() }) { Text("Reintentar") }
            },
            confirmButton = {
                TextButton(onClick = { showResolution = false; onResolve() }) {
                    Text(if (status.resolutionLabel == null) "Abrir sincronización" else "Ir a ${status.resolutionLabel}")
                }
            }
        )
    }
}

@Composable
private fun SyncCountRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium)
    }
}
