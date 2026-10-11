package com.example.bspos.presentation.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.core.ui.theme.BSPOSFonts
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.CustomerAccountEntryDto
import com.example.bspos.data.micatalogo.dto.CustomerDebtOriginDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CustomerAccountScreen(state:CustomerDetailState,onBack:()->Unit,onRetry:()->Unit) {
    val account=state.account
    val balance=account?.customer?.balance?.let(MoneyUtils::parseDecimalToCents) ?: state.customer.balance
    val limit=account?.customer?.creditLimit?.let(MoneyUtils::parseDecimalToCents) ?: state.customer.creditLimit
    val active=account?.customer?.isActive ?: state.customer.isActive
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Volver a clientes") }
            Column(Modifier.weight(1f)) {
                Text(state.customer.fullName,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                Text("Movimientos y deuda",color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.labelMedium)
            }
            IconButton(onRetry,enabled=!state.loading) { Icon(Icons.Default.Refresh,"Actualizar movimientos") }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Card(shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,BSPOSTheme.colors.outline),
                    colors=CardDefaults.cardColors(containerColor=BSPOSTheme.colors.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(if(account==null) "Saldo guardado" else "Saldo pendiente",color=BSPOSTheme.colors.textSecondary)
                        Text(accountMoney(balance),fontFamily=BSPOSFonts.Monospace,style=MaterialTheme.typography.headlineMedium,
                            color=if(balance>0) BSPOSTheme.colors.warning else BSPOSTheme.colors.textPrimary)
                        AccountValueRow("Límite de crédito",accountMoney(limit))
                        AccountValueRow("Disponible",accountMoney((limit-balance).coerceAtLeast(0)))
                        if(!active) Text("Inactivo",color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.labelMedium)
                        state.customer.phone?.takeIf { it.isNotBlank() }?.let { Text(it,color=BSPOSTheme.colors.textSecondary) }
                    }
                }
            }
            if(state.loading) item {
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Consultando movimientos…",color=BSPOSTheme.colors.textSecondary)
                }
            }
            state.error?.let { error -> item {
                Card(colors=CardDefaults.cardColors(containerColor=BSPOSTheme.colors.warningLight)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(error,color=BSPOSTheme.colors.textPrimary)
                        TextButton(onRetry) { Text("Reintentar") }
                    }
                }
            } }
            if(account!=null) {
                item { Text("Origen de la deuda",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium) }
                if(balance<=0) item { Text("No tiene deuda pendiente.",color=BSPOSTheme.colors.textSecondary) }
                else {
                    items(account.debtOrigins,key={it.invoiceNumber}) { origin -> DebtOriginCard(origin) }
                    val unallocated=account.unallocatedBalance?.let(MoneyUtils::parseDecimalToCents) ?: 0L
                    if(unallocated>0) item {
                        Card(border=BorderStroke(1.dp,BSPOSTheme.colors.outline)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("Saldo no asociado a una factura",fontWeight=FontWeight.SemiBold)
                                Text(accountMoney(unallocated),fontFamily=BSPOSFonts.Monospace)
                                Text("Puede proceder de un saldo inicial o ajustes. Revisa los movimientos del cliente.",color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if(account.debtOrigins.isEmpty() && unallocated==0L) item {
                        Text("Revisa los cargos y ajustes del historial para identificar la procedencia del saldo.",color=BSPOSTheme.colors.textSecondary)
                    }
                }
                item {
                    Column {
                        Text("Movimientos",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                        if(account.entriesHasMore) Text("Mostrando los últimos 100 movimientos.",color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.bodySmall)
                    }
                }
                if(account.entries.isEmpty()) item { Text("Este cliente todavía no tiene movimientos registrados.",color=BSPOSTheme.colors.textSecondary) }
                items(account.entries,key={it.id}) { entry -> AccountMovementCard(entry) }
            }
        }
    }
}

@Composable
private fun DebtOriginCard(origin:CustomerDebtOriginDto) {
    Card(shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,BSPOSTheme.colors.outline),
        colors=CardDefaults.cardColors(containerColor=BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(origin.invoiceNumber,fontWeight=FontWeight.SemiBold)
            Text(listOfNotNull(origin.orderNumber?.let { "Pedido $it" },origin.channel?.let { channelLabel(it) }).joinToString(" · "),
                color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.bodySmall)
            origin.issuedAt?.let { Text("Emitida ${accountDate(it)}",color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.bodySmall) }
            origin.dueDate?.let { Text("Vence $it",color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.bodySmall) }
            AccountValueRow("Total",decimalMoney(origin.total))
            AccountValueRow("Pagado",decimalMoney(origin.paidAmount))
            AccountValueRow("Pendiente",decimalMoney(origin.outstandingAmount))
        }
    }
}

@Composable
private fun AccountMovementCard(entry:CustomerAccountEntryDto) {
    val value=accountEntryCents(entry.amount) ?: 0L
    val signed=if(entry.type=="payment" && value>0) -value else value
    Card(shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,BSPOSTheme.colors.outline),
        colors=CardDefaults.cardColors(containerColor=BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(entry.type=="charge" && entry.invoiceNumber==null) "Cargo de saldo" else movementLabel(entry.type),fontWeight=FontWeight.SemiBold)
                Text((if(signed>0) "+" else "")+accountMoney(signed),fontFamily=BSPOSFonts.Monospace,
                    color=if(signed<0) BSPOSTheme.colors.success else BSPOSTheme.colors.warning)
            }
            Text(accountDate(entry.createdAt),color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.labelSmall)
            entry.invoiceNumber?.let { Text("Factura $it",color=BSPOSTheme.colors.primary,style=MaterialTheme.typography.bodySmall) }
            entry.allocations.forEach { allocation ->
                Text("Aplicado a ${allocation.invoiceNumber ?: "factura"}: ${decimalMoney(allocation.amount)}",color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.bodySmall)
            }
            entry.notes?.takeIf { it.isNotBlank() }?.let { Text(it,color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.bodySmall) }
            AccountValueRow("Saldo después",decimalMoney(entry.balanceAfter))
        }
    }
}

@Composable private fun AccountValueRow(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.bodySmall)
        Text(value,fontFamily=BSPOSFonts.Monospace,style=MaterialTheme.typography.bodySmall)
    }
}
internal fun movementLabel(type:String)=when(type) {
    "charge" -> "Venta a crédito"; "payment" -> "Abono recibido"; "adjustment" -> "Ajuste de saldo";
    "return" -> "Devolución"; else -> "Movimiento ($type)"
}
internal fun accountEntryCents(value:String):Long? {
    val text=value.trim()
    val magnitude=MoneyUtils.parseDecimalToCents(text.removePrefix("-").removePrefix("+")) ?: return null
    return if(text.startsWith("-")) -magnitude else magnitude
}
private fun channelLabel(channel:String)=when(channel) { "pos" -> "Terminal"; "whatsapp" -> "Pedido WhatsApp"; "web" -> "Venta web"; else -> channel }
private fun accountDate(value:String)=runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale("es")))
}.getOrDefault(value)
@Composable private fun accountMoney(value:Long)=MoneyUtils.formatCents(value,LocalCurrency.current)
@Composable private fun decimalMoney(value:String)=accountMoney(MoneyUtils.parseDecimalToCents(value) ?: 0L)
