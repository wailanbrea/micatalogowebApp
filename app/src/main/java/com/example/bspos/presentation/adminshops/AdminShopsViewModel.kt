package com.example.bspos.presentation.adminshops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.MiCatalogoManagedShop
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminShopsUiState(
    val isLoading: Boolean = false,
    val savingShopId: String? = null,
    val shops: List<MiCatalogoManagedShop> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class AdminShopsViewModel @Inject constructor(
    private val connectionRepository: MiCatalogoConnectionRepository
) : ViewModel() {
    val uiState = MutableStateFlow(AdminShopsUiState())

    fun load() = viewModelScope.launch {
        if (uiState.value.isLoading) return@launch
        uiState.value = uiState.value.copy(isLoading = true, error = null)
        when (val result = connectionRepository.managedShops()) {
            is MiCatalogoResult.Success -> uiState.value = uiState.value.copy(isLoading = false, shops = result.value)
            is MiCatalogoResult.Failure -> uiState.value = uiState.value.copy(isLoading = false, error = result.message)
        }
    }

    fun save(shop: MiCatalogoManagedShop, onSaved: () -> Unit) = viewModelScope.launch {
        if (uiState.value.savingShopId != null) return@launch
        uiState.value = uiState.value.copy(savingShopId = shop.id, error = null)
        when (val result = connectionRepository.updateManagedShop(shop)) {
            is MiCatalogoResult.Success -> {
                uiState.value = uiState.value.copy(
                    savingShopId = null,
                    shops = uiState.value.shops.map { if (it.id == result.value.id) result.value else it }
                )
                onSaved()
            }
            is MiCatalogoResult.Failure -> uiState.value = uiState.value.copy(savingShopId = null, error = result.message)
        }
    }
}
