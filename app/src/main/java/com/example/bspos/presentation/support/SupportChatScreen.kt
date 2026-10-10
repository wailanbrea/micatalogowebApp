package com.example.bspos.presentation.support

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Send
import com.example.bspos.presentation.common.BSPOSButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.bspos.presentation.common.BSPOSActionTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.SupportConversationDto
import com.example.bspos.data.micatalogo.dto.SupportMessageDto
import com.example.bspos.presentation.common.CheckoutStyleBottomSheet
import com.example.bspos.presentation.common.CheckoutStylePrimaryButton
import com.example.bspos.presentation.common.CheckoutStyleSectionLabel
import kotlinx.coroutines.delay

@Composable
fun SupportChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SupportChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showNewChat by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.isInbox) {
        while (true) {
            delay(15_000)
            viewModel.load(showLoading = false)
        }
    }

    Box(modifier.fillMaxSize().background(BSPOSTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            SupportChatHeader(
                conversationOpen = state.activeConversation != null,
                onBack = if (state.activeConversation != null) viewModel::clearActiveConversation else onNavigateBack,
                onRefresh = { viewModel.load() }
            )
            if (state.loading && state.conversations.isEmpty() && state.activeConversation == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BSPOSTheme.colors.primary)
                }
            } else if (state.activeConversation != null) {
                SupportConversationContent(
                    conversation = state.activeConversation!!,
                    messages = state.messages,
                    isInbox = state.isInbox,
                    sending = state.sending,
                    closing = state.closing,
                    onSend = viewModel::sendMessage,
                    onClose = viewModel::closeConversation,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SupportInboxContent(
                    conversations = state.conversations,
                    isInbox = state.isInbox,
                    onOpen = viewModel::openConversation,
                    onNewChat = { showNewChat = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        state.error?.let { error ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                color = BSPOSTheme.colors.errorLight,
                border = BorderStroke(1.dp, BSPOSTheme.colors.error.copy(alpha = .35f))
            ) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearError) { Text("Cerrar") }
                }
            }
        }
    }

    if (showNewChat) {
        NewSupportChatDialog(
            sending = state.sending,
            onDismiss = { if (!state.sending) showNewChat = false },
            onStart = { subject, message ->
                showNewChat = false
                viewModel.startConversation(subject, message)
            }
        )
    }
}

@Composable
private fun SupportChatHeader(conversationOpen: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BSPOSTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = BSPOSTheme.colors.textPrimary)
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ajustes", color = BSPOSTheme.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(" / ", color = BSPOSTheme.colors.textSecondary, fontSize = 11.sp)
            Text("Soporte", color = BSPOSTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            if (conversationOpen) {
                Text(" / ", color = BSPOSTheme.colors.textSecondary, fontSize = 11.sp)
                Text("Conversación", color = BSPOSTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = BSPOSTheme.colors.primary) }
    }
}

@Composable
private fun SupportInboxContent(
    conversations: List<SupportConversationDto>,
    isInbox: Boolean,
    onOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isInbox) "Bandeja de soporte" else "Chat con MiCatalogo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                    Text(
                        if (isInbox) "Solicitudes ordenadas por la última llegada. Revisa quién escribió y cuándo."
                        else "Escribe al equipo de soporte y conserva toda la conversación dentro de la app.",
                        color = BSPOSTheme.colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!isInbox) {
                        Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Nuevo chat")
                        }
                    }
                }
            }
        }
        if (conversations.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
                    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(40.dp))
                        Text(if (isInbox) "No hay solicitudes" else "Todavía no tienes conversaciones", fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                    }
                }
            }
        } else {
            items(conversations, key = { it.id }) { conversation ->
                SupportConversationCard(conversation, isInbox, onClick = { onOpen(conversation.id) })
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SupportConversationCard(conversation: SupportConversationDto, isInbox: Boolean, onClick: () -> Unit) {
    val requester = conversation.requester
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface),
        border = BorderStroke(1.dp, BSPOSTheme.colors.outline)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isInbox) requester?.name?.ifBlank { "Usuario" } ?: "Usuario" else conversation.subject, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                if (conversation.unreadCount > 0) {
                    Surface(shape = CircleShape, color = BSPOSTheme.colors.primary) { Text(conversation.unreadCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                }
            }
            if (isInbox) {
                Text(requester?.email.orEmpty(), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text(conversation.shop?.name?.let { "Tienda · $it" } ?: "Tienda no disponible", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                SupportStatusPill(conversation.status)
            }
            Text(
                if (isInbox) "Solicitado · ${supportDate(conversation.createdAt)}" else "Creado · ${supportDate(conversation.createdAt)}",
                color = BSPOSTheme.colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            if (isInbox && conversation.lastMessageAt != null && conversation.lastMessageAt != conversation.createdAt) {
                Text("Última actividad · ${supportDate(conversation.lastMessageAt)}", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SupportConversationContent(
    conversation: SupportConversationDto,
    messages: List<SupportMessageDto>,
    isInbox: Boolean,
    sending: Boolean,
    closing: Boolean,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCloseConfirmation by remember(conversation.id) { mutableStateOf(false) }
    var draft by remember(conversation.id) { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Column(modifier.fillMaxSize().imePadding()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface), border = BorderStroke(1.dp, BSPOSTheme.colors.outline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = BSPOSTheme.colors.primaryLight, modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = BSPOSTheme.colors.primary, modifier = Modifier.size(23.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(conversation.requester?.name ?: conversation.subject, fontWeight = FontWeight.Bold, color = BSPOSTheme.colors.textPrimary)
                        Text(conversation.requester?.email.orEmpty(), color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    SupportStatusPill(conversation.status)
                }
                Divider(color = BSPOSTheme.colors.outline.copy(alpha = .7f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Solicitud de soporte", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                        Text("Creada · ${supportDate(conversation.createdAt)}", color = BSPOSTheme.colors.textPrimary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (conversation.status != "closed") {
                        OutlinedButton(onClick = { showCloseConfirmation = true }, enabled = !closing, shape = RoundedCornerShape(12.dp)) {
                            Text(if (closing) "Concluyendo…" else "Concluir chat")
                        }
                    }
                }
                if (conversation.status == "closed") {
                    Text(
                        "Concluido${conversation.closedBy?.name?.let { " por $it" }.orEmpty()} · ${supportDate(conversation.closedAt)}",
                        color = BSPOSTheme.colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val ownEmail = if (isInbox) conversation.assignedTo?.email else conversation.requester?.email
                val mine = message.sender?.email.equals(ownEmail, ignoreCase = true)
                MessageBubble(message, mine = mine)
            }
        }
        if (conversation.status == "closed") {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                color = BSPOSTheme.colors.surfaceVariant,
                border = BorderStroke(1.dp, BSPOSTheme.colors.outline)
            ) {
                Text(
                    "Este chat está concluido. Para solicitar ayuda nuevamente, inicia un nuevo chat.",
                    modifier = Modifier.padding(14.dp),
                    color = BSPOSTheme.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(4000) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    shape = RoundedCornerShape(18.dp)
                )
                IconButton(onClick = { onSend(draft); draft = "" }, enabled = draft.isNotBlank() && !sending && !closing) {
                    if (sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = BSPOSTheme.colors.primary)
                    else Icon(Icons.Outlined.Send, contentDescription = "Enviar", tint = BSPOSTheme.colors.primary)
                }
            }
        }
    }
    if (showCloseConfirmation) {
        CheckoutStyleBottomSheet(
            title = "Concluir chat",
            badge = "SOPORTE",
            onDismiss = { showCloseConfirmation = false },
            dismissEnabled = !closing,
            footer = {
                CheckoutStylePrimaryButton(
                    text = "Concluir chat",
                    onClick = { showCloseConfirmation = false; onClose() },
                    enabled = !closing,
                    busy = closing
                )
            }
        ) {
            Text("Ambas personas verán la conversación como concluida y no se podrán enviar más mensajes en ella.", color = BSPOSTheme.colors.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SupportStatusPill(status: String) {
    val closed = status == "closed"
    Surface(shape = RoundedCornerShape(50), color = if (closed) BSPOSTheme.colors.surfaceVariant else BSPOSTheme.colors.successLight) {
        Text(
            if (closed) "Concluido" else "Abierto",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (closed) BSPOSTheme.colors.textSecondary else BSPOSTheme.colors.success,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MessageBubble(message: SupportMessageDto, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) BSPOSTheme.colors.primary else BSPOSTheme.colors.surface,
            border = if (mine) null else BorderStroke(1.dp, BSPOSTheme.colors.outline),
            modifier = Modifier.fillMaxWidth(.82f)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message.body, color = if (mine) Color.White else BSPOSTheme.colors.textPrimary)
                Text(supportDate(message.createdAt), color = if (mine) Color.White.copy(alpha = .75f) else BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun NewSupportChatDialog(
    sending: Boolean,
    onDismiss: () -> Unit,
    onStart: (String, String) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    CheckoutStyleBottomSheet(
        title = "Nuevo chat",
        badge = "SOPORTE",
        onDismiss = onDismiss,
        dismissEnabled = !sending,
        footer = {
            CheckoutStylePrimaryButton(
                text = "Enviar solicitud",
                onClick = { onStart(subject, message) },
                enabled = message.isNotBlank() && !sending,
                busy = sending
            )
        }
    ) {
        Text("Tu solicitud llegará a wailandkey@gmail.com y quedará registrada con la hora de llegada.", color = BSPOSTheme.colors.textSecondary, fontSize = 12.sp)
        CheckoutStyleSectionLabel("ASUNTO")
        OutlinedTextField(
            subject,
            { subject = it.take(160) },
            Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Opcional", fontSize = 13.sp) },
            shape = RoundedCornerShape(10.dp)
        )
        CheckoutStyleSectionLabel("MENSAJE")
        OutlinedTextField(
            message,
            { message = it.take(4000) },
            Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 6,
            placeholder = { Text("Describe cómo podemos ayudarte", fontSize = 13.sp) },
            shape = RoundedCornerShape(10.dp)
        )
    }
}

private fun supportDate(value: String?): String {
    if (value.isNullOrBlank()) return "Sin fecha"
    return value.replace('T', ' ').replace(Regex("\\+\\d{2}:?\\d{2}$"), "").removeSuffix("Z").take(16)
}
