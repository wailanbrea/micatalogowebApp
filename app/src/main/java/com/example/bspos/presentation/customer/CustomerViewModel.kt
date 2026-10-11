package com.example.bspos.presentation.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.usecase.CustomerInput
import com.example.bspos.domain.usecase.CustomerUseCases
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerAccountApi
import com.example.bspos.data.micatalogo.dto.CustomerAccountDetailDto
import com.example.bspos.data.micatalogo.apiErrorMessage
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerDetailState(
    val customer:Customer,val loading:Boolean=true,
    val account:CustomerAccountDetailDto?=null,val error:String?=null
)

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val useCases:CustomerUseCases,
    private val accounts:Lazy<MiCatalogoCustomerAccountApi>,
    private val shops:ShopScope
):ViewModel() {
    val customers=useCases.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val _message=MutableStateFlow<String?>(null)
    val message=_message.asStateFlow()
    private val _detail=MutableStateFlow<CustomerDetailState?>(null)
    val detail=_detail.asStateFlow()
    private var detailJob:Job?=null

    init {
        viewModelScope.launch {
            shops.changes.collect { active ->
                if(_detail.value?.customer?.remoteShopId?.let { it!=active }==true) closeDetail()
            }
        }
    }

    fun openDetail(customer:Customer) {
        detailJob?.cancel()
        _detail.value=CustomerDetailState(customer)
        detailJob=viewModelScope.launch {
            try {
                val shop=shops.current()
                if(customer.remoteShopId!=shop) {
                    _detail.value=null
                    _message.value="El cliente no pertenece a la tienda activa."
                    return@launch
                }
                val remote=customer.remoteCustomerId
                if(remote==null) {
                    _detail.value=CustomerDetailState(customer,loading=false,error="Cliente pendiente de sincronizar. Sus movimientos estarán disponibles cuando se guarde en MiCatalogo.")
                    return@launch
                }
                val response=accounts.get().detail(shop,remote)
                check(response.isSuccessful) { response.apiErrorMessage("No se pudieron consultar los movimientos del cliente.") }
                val account=checkNotNull(response.body())
                require(account.customer.id==remote) { "La respuesta no corresponde al cliente seleccionado." }
                if(shops.current()==shop && _detail.value?.customer?.id==customer.id)
                    _detail.value=CustomerDetailState(customer,loading=false,account=account)
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) {
                if(_detail.value?.customer?.id==customer.id)
                    _detail.value=CustomerDetailState(customer,loading=false,error=error.message ?: "No se pudieron cargar los movimientos.")
            }
        }
    }

    fun retryDetail() { _detail.value?.customer?.let(::openDetail) }
    fun closeDetail() { detailJob?.cancel(); detailJob=null; _detail.value=null }
    fun add(input:CustomerInput)=runOperation("No se pudo guardar el cliente") { useCases.create(input) }
    fun update(customer:Customer,input:CustomerInput)=runOperation("No se pudo actualizar el cliente") { useCases.update(customer,input) }
    fun delete(customer:Customer)=runOperation("No se puede eliminar un cliente con saldo pendiente") { useCases.delete(customer) }
    fun consumeMessage() { _message.value=null }
    private fun runOperation(fallback:String,operation:suspend()->Unit) {
        viewModelScope.launch {
            try { operation() }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { _message.value=error.message ?: fallback }
        }
    }
}
