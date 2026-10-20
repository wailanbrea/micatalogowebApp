package com.example.bspos.presentation.practice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.CurrencyUnit

@Composable
fun PracticeScreen() {
    var session by remember { mutableStateOf(PracticeSession()) }
    var message by remember { mutableStateOf("Elige una operación para ver cómo cambia el negocio de ejemplo.") }
    fun money(cents: Long) = MoneyUtils.formatCents(cents, CurrencyUnit.DOP)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Practicar sin miedo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) {
            Text("SIMULACIÓN · Datos de ejemplo. Estas acciones no crean ventas, cobros ni movimientos en tu tienda. El ejercicio se reinicia al salir de esta pantalla.", Modifier.padding(16.dp))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Producto de práctica", fontWeight = FontWeight.Bold)
                Text("Precio ${money(PracticeSession.PRICE_CENTS)} · Costo ${money(PracticeSession.COST_CENTS)}")
                Text("Existencia de práctica: ${session.stock}")
                Text("Ventas simuladas: ${money(session.salesCents)}")
                Text("Ganancia bruta simulada: ${money(session.profitCents)}")
                Text("Efectivo simulado: ${money(session.cashCents)}")
                Text("Saldo por cobrar simulado: ${money(session.debtCents)}")
            }
        }
        Text("1. Practica una venta", fontWeight = FontWeight.Bold)
        Text("Al contado aumenta el efectivo. A crédito queda un saldo por cobrar. En ambos casos baja la existencia.")
        Button(onClick = { session = session.sell(1, false); message = "Venta al contado simulada: salió 1 unidad y entraron ${money(PracticeSession.PRICE_CENTS)}." }, enabled = session.stock > 0, modifier = Modifier.fillMaxWidth()) { Text("Simular venta al contado") }
        OutlinedButton(onClick = { session = session.sell(1, true); message = "Venta a crédito simulada: salió 1 unidad y aumentó el saldo por cobrar." }, enabled = session.stock > 0, modifier = Modifier.fillMaxWidth()) { Text("Simular venta a crédito") }
        Text("2. Practica un cobro", fontWeight = FontWeight.Bold)
        Text("Cobrar la deuda aumenta el efectivo, pero no vuelve a registrar la venta ni la ganancia.")
        OutlinedButton(onClick = { session = session.collect(session.debtCents); message = "Saldo de práctica cobrado. El total de ventas y la ganancia no cambiaron." }, enabled = session.debtCents > 0, modifier = Modifier.fillMaxWidth()) { Text("Simular cobro del saldo") }
        Text("3. Practica una recepción", fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { session = session.receive(5); message = "Recibiste 5 unidades de ejemplo. No es una venta ni un cobro." }, modifier = Modifier.fillMaxWidth()) { Text("Recibir 5 unidades de práctica") }
        Text(message, color = BSPOSTheme.colors.primary)
        TextButton(onClick = { session = PracticeSession(); message = "Ejercicio reiniciado: vuelve a intentar cualquier operación." }) { Text("Reiniciar práctica") }
    }
}
