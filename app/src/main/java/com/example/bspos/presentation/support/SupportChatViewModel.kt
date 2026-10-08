package com.example.bspos.presentation.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.SupportChatCreateRequestDto
import com.example.bspos.data.micatalogo.dto.SupportChatMessageRequestDto
import com.example.bspos.data.micatalogo.dto.SupportConversationDto
import com.example.bspos.data.micatalogo.dto.SupportMessageDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response
import javax.inject.Inject

data class SupportChatUiState(
    val loading: Boolean = true,
    val conversations: List<SupportConversationDto> = emptyList(),
    val activeConversation: SupportConversationDto? = null,
    val messages: List<SupportMessageDto> = emptyList(),
    val isInbox: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SupportChatViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SupportChatUiState())
    val state: StateFlow<SupportChatUiState> = _state.asStateFlow()

    fun load(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val response = api.get().supportConversations()
                check(response.isSuccessful) { responseError(response, "No se pudo cargar el chat.") }
                response.body() ?: error("La bandeja de soporte llegó vacía.")
            }.onSuccess { inbox ->
                val activeId = _state.value.activeConversation?.id
                _state.value = _state.value.copy(
                    loading = false,
                    conversations = inbox.conversations,
                    isInbox = inbox.isInbox,
                    error = null
                )
                if (!activeId.isNullOrBlank()) openConversation(activeId, showLoading = false)
            }.onFailure { error ->
                _state.value = _state.value.copy(loading = false, error = error.message ?: "No se pudo cargar el chat.")
            }
        }
    }

    fun openConversation(id: String, showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val response = api.get().supportConversation(id)
                check(response.isSuccessful) { responseError(response, "No se pudo abrir la conversación.") }
                response.body() ?: error("La conversación llegó vacía.")
            }.onSuccess { detail ->
                _state.value = _state.value.copy(
                    loading = false,
                    activeConversation = detail.conversation,
                    messages = detail.messages,
                    error = null
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(loading = false, error = error.message ?: "No se pudo abrir la conversación.")
            }
        }
    }

    fun startConversation(subject: String, message: String) {
        val text = message.trim()
        if (text.isBlank()) {
            _state.value = _state.value.copy(error = "Escribe un mensaje para iniciar el chat.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null)
            runCatching {
                val shopId = connection.activeShopId().orEmpty()
                check(shopId.isNotBlank()) { "Selecciona una tienda activa para iniciar el chat." }
                val response = api.get().createSupportConversation(
                    shopId,
                    SupportChatCreateRequestDto(subject.trim().ifBlank { null }, text)
                )
                check(response.isSuccessful) { responseError(response, "No se pudo iniciar el chat.") }
                response.body() ?: error("La conversación no fue creada.")
            }.onSuccess { detail ->
                _state.value = _state.value.copy(
                    loading = false,
                    sending = false,
                    conversations = listOf(detail.conversation) + _state.value.conversations.filter { it.id != detail.conversation.id },
                    activeConversation = detail.conversation,
                    messages = detail.messages,
                    error = null
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(sending = false, error = error.message ?: "No se pudo iniciar el chat.")
            }
        }
    }

    fun sendMessage(message: String) {
        val conversation = _state.value.activeConversation ?: return
        val text = message.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null)
            runCatching {
                val response = api.get().sendSupportMessage(conversation.id, SupportChatMessageRequestDto(text))
                check(response.isSuccessful) { responseError(response, "No se pudo enviar el mensaje.") }
                response.body()?.chatMessage ?: error("El servidor no devolvió el mensaje.")
            }.onSuccess { sent ->
                _state.value = _state.value.copy(
                    sending = false,
                    messages = _state.value.messages + sent,
                    conversations = _state.value.conversations.map {
                        if (it.id == conversation.id) it.copy(lastMessageAt = sent.createdAt, unreadCount = 0) else it
                    }
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(sending = false, error = error.message ?: "No se pudo enviar el mensaje.")
            }
        }
    }

    fun clearActiveConversation() {
        _state.value = _state.value.copy(activeConversation = null, messages = emptyList(), error = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun responseError(response: Response<*>, fallback: String): String {
        if (response.code() == 404) return "El chat todavía no está disponible en el servidor."
        if (response.code() == 503) return "El buzón de soporte todavía no está configurado."
        val raw = response.errorBody()?.string().orEmpty()
        val message = runCatching { JSONObject(raw).optString("message") }.getOrNull()
        return message?.takeIf { it.isNotBlank() } ?: fallback
    }
}
