package com.example.bspos.presentation.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

@Composable
fun AppUpdateDialog(
    state: AppUpdateState,
    onDownload: (AvailableAppUpdate) -> Unit,
    onRetryCheck: () -> Unit,
    onDismissCheckFailure: () -> Unit
) {
    if (state is AppUpdateState.CheckFailed) {
        AlertDialog(
            onDismissRequest = onDismissCheckFailure,
            title = { Text("No se pudo comprobar la actualización") },
            text = {
                Column {
                    Text("La app no pudo confirmar si hay una versión nueva.")
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = onRetryCheck) { Text("Reintentar") } },
            dismissButton = { TextButton(onClick = onDismissCheckFailure) { Text("Cerrar") } }
        )
        return
    }

    val update = when (state) {
        is AppUpdateState.Available -> state.update
        is AppUpdateState.Downloading -> state.update
        is AppUpdateState.Failed -> state.update
        else -> return
    }
    val isDownloading = state is AppUpdateState.Downloading
    val error = (state as? AppUpdateState.Failed)?.message

    AlertDialog(
        // Once the server has announced a newer APK the user must not be able
        // to dismiss the update by tapping outside, pressing Back, or swiping
        // the dialog away. If the download/installer is interrupted, the
        // ViewModel returns to this same state on resume and the dialog is
        // presented again.
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(if (update.isRequired) "Actualizacion requerida" else "Actualizacion disponible") },
        text = {
            Column {
                Text("MiCatalogo ${update.versionName} está disponible.")
                if (update.releaseNotes.isNotBlank()) Text(update.releaseNotes, style = MaterialTheme.typography.bodySmall)
                if (isDownloading) {
                    val progress = (state as AppUpdateState.Downloading).progress
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Descargando: $progress%", style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onDownload(update) }, enabled = !isDownloading) {
                Text(
                    when {
                        isDownloading -> "Descargando"
                        error != null -> "Reintentar"
                        else -> "Actualizar"
                    }
                )
            }
        }
    )
}
