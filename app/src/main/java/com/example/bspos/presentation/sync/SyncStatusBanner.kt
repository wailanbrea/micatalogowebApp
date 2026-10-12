package com.example.bspos.presentation.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bspos.core.ui.theme.BSPOSTheme

@Composable
fun SyncStatusBanner(status: SyncStatus, onRetry: () -> Unit) {
    val title = when (status.phase) {
        SyncPhase.CHECKING -> "Verificando sincronización…"
        SyncPhase.SYNCED -> "Sincronizado"
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
            if (status.phase in setOf(SyncPhase.RETRY, SyncPhase.BLOCKED, SyncPhase.PENDING)) {
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Reintentar", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
