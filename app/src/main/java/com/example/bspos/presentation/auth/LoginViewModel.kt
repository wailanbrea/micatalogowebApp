package com.example.bspos.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.MiCatalogoConnectionState
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val authenticated: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val connectionRepository: MiCatalogoConnectionRepository
) : ViewModel() {
    val connection = connectionRepository.observeConnection()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiCatalogoConnectionState("", false))
    val uiState = MutableStateFlow(LoginUiState())
    fun login(email: String, password: String, remember: Boolean) = viewModelScope.launch {
        uiState.value = LoginUiState(isSubmitting = true)
        when (val result = connectionRepository.login(email, password, remember)) {
            is MiCatalogoResult.Success -> uiState.value = LoginUiState(authenticated = true)
            is MiCatalogoResult.Failure -> uiState.value = LoginUiState(errorMessage = result.message)
        }
    }

    fun consumeAuthenticated() {
        uiState.value = uiState.value.copy(authenticated = false)
    }
}
