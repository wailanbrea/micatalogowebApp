package com.example.bspos.presentation.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.usecase.CustomerInput
import com.example.bspos.domain.usecase.CustomerUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class CustomerViewModel @Inject constructor(private val useCases:CustomerUseCases):ViewModel(){val customers=useCases.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList());private val _message=MutableStateFlow<String?>(null);val message=_message.asStateFlow();fun add(input:CustomerInput){viewModelScope.launch{runCatching{useCases.create(input)}.onFailure{_message.value=it.message?:"No se pudo guardar el cliente"}}};fun update(customer:Customer,input:CustomerInput){viewModelScope.launch{runCatching{useCases.update(customer,input)}.onFailure{_message.value=it.message?:"No se pudo actualizar el cliente"}}};fun delete(customer:Customer){viewModelScope.launch{runCatching{useCases.delete(customer)}.onFailure{_message.value="No se puede eliminar un cliente con saldo pendiente"}}};fun consumeMessage(){_message.value=null}}
