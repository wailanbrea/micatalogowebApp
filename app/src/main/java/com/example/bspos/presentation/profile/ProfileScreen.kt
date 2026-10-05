package com.example.bspos.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.presentation.settings.SettingsViewModel

@Composable
fun ProfileScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val connection by viewModel.miCatalogoConnection.collectAsState()
    val state by viewModel.miCatalogoUi.collectAsState()
    var name by remember(connection.accountName) { mutableStateOf(connection.accountName) }
    var email by remember(connection.accountEmail) { mutableStateOf(connection.accountEmail) }

    LaunchedEffect(connection.accountEmail, connection.accountName) {
        if (connection.accountEmail.isNotBlank()) email = connection.accountEmail
        if (connection.accountName.isNotBlank()) name = connection.accountName
    }

    Column(
        Modifier.fillMaxSize().background(BSPOSTheme.colors.background).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Mi perfil", style = MaterialTheme.typography.headlineLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
        Text("Edita los datos de la cuenta conectada.", color = BSPOSTheme.colors.textSecondary)
        Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BSPOSTheme.colors.primary)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Correo electrónico") }, singleLine = true)
                Text("Rol: ${if (connection.isAdmin) "Administrador" else "Vendedor"}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { viewModel.updateProfile(name, email) },
                    enabled = !state.isUpdatingProfile && name.isNotBlank() && email.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isUpdatingProfile) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                    else Text("Guardar cambios")
                }
                Button(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar sesión")
                }
                state.successMessage?.let { Text(it, color = BSPOSTheme.colors.success) }
                state.errorMessage?.let { Text(it, color = BSPOSTheme.colors.error) }
            }
        }
    }
}
