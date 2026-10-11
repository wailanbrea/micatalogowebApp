package com.example.bspos.presentation.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.activity.compose.BackHandler
import com.example.bspos.core.ui.theme.BSPOSFonts
import androidx.compose.material3.*
import com.example.bspos.presentation.common.BSPOSButton as Button
import com.example.bspos.presentation.common.BSPOSOutlinedButton as OutlinedButton
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.core.money.MoneyUtils
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import com.example.bspos.domain.usecase.CustomerInput
import com.example.bspos.presentation.common.BSPOSAlertDialog as AlertDialog
import com.example.bspos.presentation.common.DialogScrollableColumn
import java.util.Locale

@Composable
fun CustomerScreen(
    presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    viewModel: CustomerViewModel = hiltViewModel()
) {
    val customers by viewModel.customers.collectAsState()
    val message by viewModel.message.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val listState=rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Customer?>(null) }
    var deleting by remember { mutableStateOf<Customer?>(null) }
    BackHandler(enabled=detail!=null) { viewModel.closeDetail() }
    if(detail!=null) {
        CustomerAccountScreen(checkNotNull(detail),viewModel::closeDetail,viewModel::retryDetail)
        return
    }
    val filtered = customers.filter { it.fullName.contains(query, true) || it.documentNumber.orEmpty().contains(query, true) || it.phone.orEmpty().contains(query) }
    val totalBalance = customers.sumOf { it.balance }
    Column(Modifier.fillMaxSize().background(BSPOSTheme.colors.background).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BSPOSTheme.colors.warningLight), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.People, null, tint = BSPOSTheme.colors.warning)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cartera pendiente", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelMedium)
                Text(money(totalBalance), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            Button({ creating = true }, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Nuevo")
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = BSPOSTheme.colors.surface, border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)) { Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Search, null, tint = BSPOSTheme.colors.primary); OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Buscar negocio, contacto o telefono") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent)) } }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (query.isBlank()) "Aun no hay clientes" else "Sin coincidencias", color = BSPOSTheme.colors.textSecondary) } else LazyColumn(Modifier.weight(1f), state=listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { customer ->
                CustomerCard(
                    customer,
                    presentation.customersShowCredit,
                    { viewModel.openDetail(customer) },
                    { editing = customer },
                    { deleting = customer }
                )
            }
        }
    }
    if (creating) CustomerForm(null, presentation.customersShowCredit, { viewModel.add(it); creating = false }, { creating = false })
    editing?.let { current -> CustomerForm(current, presentation.customersShowCredit, { viewModel.update(current, it); editing = null }, { editing = null }) }
    deleting?.let { customer ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar cliente") },
            text = {
                Text(
                    if (customer.balance > 0L) {
                        "${customer.fullName} tiene un saldo pendiente. No se puede eliminar hasta registrar el cobro."
                    } else {
                        "¿Quieres retirar a ${customer.fullName} del directorio? Sus ventas históricas se conservarán."
                    }
                )
            },
            confirmButton = {
                if (customer.balance <= 0L) {
                    TextButton(onClick = { viewModel.delete(customer); deleting = null }) { Text("Eliminar") }
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
    message?.let { text -> AlertDialog(onDismissRequest = viewModel::consumeMessage, title = { Text("No se pudo completar") }, text = { Text(text) }, confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("Cerrar") } }) }
}

@Composable
internal fun CustomerCard(customer: Customer, showCredit: Boolean, onOpen:()->Unit,onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember(customer.id) { mutableStateOf(false) }
    Card(onClick=onOpen,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),
        border=BorderStroke(1.dp,BSPOSTheme.colors.outline),colors=CardDefaults.cardColors(containerColor=BSPOSTheme.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(BSPOSTheme.colors.surfaceVariant),contentAlignment=Alignment.Center) {
                Text(customer.fullName.take(1).uppercase(),color=BSPOSTheme.colors.primary,fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(customer.fullName,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(listOfNotNull(customer.phone?.takeIf { it.isNotBlank() } ?: customer.whatsapp?.takeIf { it.isNotBlank() },
                    "Inactivo".takeUnless { customer.isActive }).joinToString(" · ").ifBlank { "Sin teléfono" },
                    color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(showCredit) Column(horizontalAlignment=Alignment.End) {
                Text(money(customer.balance),fontFamily=BSPOSFonts.Monospace,fontWeight=FontWeight.SemiBold,
                    color=if(customer.balance>0) BSPOSTheme.colors.warning else BSPOSTheme.colors.textPrimary,
                    style=MaterialTheme.typography.labelLarge)
                Text(if(customer.balance>0) "Pendiente" else "Sin deuda",color=BSPOSTheme.colors.textSecondary,style=MaterialTheme.typography.labelSmall)
            }
            Box {
                IconButton(onClick={menuOpen=true}) { Icon(Icons.Default.MoreVert,"Acciones de ${customer.fullName}") }
                DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}) {
                    DropdownMenuItem(text={Text("Ver movimientos")},onClick={menuOpen=false;onOpen()})
                    DropdownMenuItem(text={Text("Editar cliente")},onClick={menuOpen=false;onEdit()})
                    DropdownMenuItem(text={Text("Eliminar cliente")},onClick={menuOpen=false;onDelete()})
                }
            }
        }
    }
}

@Composable
private fun CustomerForm(current: Customer?, showCredit: Boolean, onSave: (CustomerInput) -> Unit, onDismiss: () -> Unit) {
    val legacyName = current?.businessName.orEmpty().trim().split(Regex("\\s+"))
    var firstName by remember(current?.id) { mutableStateOf(current?.firstName ?: legacyName.firstOrNull().orEmpty()) }
    var lastName by remember(current?.id) { mutableStateOf(current?.lastName ?: legacyName.drop(1).joinToString(" ")) }
    var documentType by remember(current?.id) { mutableStateOf(current?.documentType ?: "cedula") }
    var documentNumber by remember(current?.id) { mutableStateOf(current?.documentNumber.orEmpty()) }
    var phone by remember(current?.id) { mutableStateOf(current?.phone.orEmpty()) }
    var email by remember(current?.id) { mutableStateOf(current?.email.orEmpty()) }
    var whatsapp by remember(current?.id) { mutableStateOf(current?.whatsapp.orEmpty()) }
    var address by remember(current?.id) { mutableStateOf(current?.address.orEmpty()) }
    var reference by remember(current?.id) { mutableStateOf(current?.reference.orEmpty()) }
    var credit by remember(current?.id) { mutableStateOf(current?.creditLimit?.div(100)?.toString().orEmpty()) }
    var notes by remember(current?.id) { mutableStateOf(current?.notes.orEmpty()) }
    var formError by remember(current?.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (current == null) "Nuevo cliente" else "Editar cliente") },
        text = {
            DialogScrollableColumn {
                OutlinedTextField(firstName, { firstName = it; formError = false }, label = { Text("Nombre *") }, singleLine = true)
                OutlinedTextField(lastName, { lastName = it; formError = false }, label = { Text("Apellido *") }, singleLine = true)
                Text("Tipo de documento *", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = documentType == "cedula", onClick = { documentType = "cedula" }, label = { Text("Cédula") })
                    FilterChip(selected = documentType == "pasaporte", onClick = { documentType = "pasaporte" }, label = { Text("Pasaporte") })
                }
                OutlinedTextField(documentNumber, { documentNumber = it; formError = false }, label = { Text("Número de documento *") }, singleLine = true)
                OutlinedTextField(phone, { phone = it; formError = false }, label = { Text("Teléfono *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(address, { address = it; formError = false }, label = { Text("Dirección *") }, minLines = 2)
                if (showCredit) OutlinedTextField(credit, { credit = it.filter(Char::isDigit); formError = false }, label = { Text("Límite de crédito (${LocalCurrency.current.symbol}) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = formError, supportingText = { if (formError) Text("Completa los campos obligatorios y verifica el límite") })
                OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(whatsapp, { whatsapp = it }, label = { Text("WhatsApp") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(reference, { reference = it }, label = { Text("Referencia") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton({
                val value = if (showCredit) MoneyUtils.parseWholeUnitsToCents(credit) else 0L
                val valid = firstName.isNotBlank() && lastName.isNotBlank() && documentNumber.isNotBlank() && phone.isNotBlank() && address.isNotBlank() && value != null && value >= 0 && (current == null || value >= current.balance)
                if (!valid) formError = true else onSave(CustomerInput(firstName, lastName, documentType, documentNumber, phone, address, value!!, email, whatsapp, reference, notes))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun money(cents: Long): String = MoneyUtils.formatCents(cents, LocalCurrency.current)
