package com.example.bspos.presentation.customer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.database.ShopScope
import com.example.bspos.core.database.ShopSwitchLock
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerAccountApi
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.usecase.CustomerUseCases
import dagger.Lazy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CustomerDetailViewModelTest {
    @Test fun detailsUseTheSelectedShopAndCloseOnShopChange() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val preferences=MemoryPreferences()
        val record=record()
        var requestedShop:String?=null
        val api=object:MiCatalogoCustomerAccountApi {
            override suspend fun detail(shopId:String,customerId:String):Response<CustomerAccountDetailDto> {
                requestedShop=shopId
                return Response.success(CustomerAccountDetailDto(RemoteCustomerDto(customerId,balance="200.00")))
            }
        }
        val vm=model(preferences,api)
        try {
            vm.openDetail(record)
            runCurrent()
            assertEquals("qa-detail-a",requestedShop)
            assertEquals("200.00",vm.detail.value!!.account!!.customer.balance)
            preferences.select("qa-detail-b")
            runCurrent()
            assertNull(vm.detail.value)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun anotherShopsCustomerNeverCallsTheEndpoint() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        var calls=0
        val api=object:MiCatalogoCustomerAccountApi {
            override suspend fun detail(shopId:String,customerId:String):Response<CustomerAccountDetailDto> { calls++; error("Unexpected call") }
        }
        val vm=model(MemoryPreferences(),api)
        try {
            vm.openDetail(record().copy(remoteShopId="qa-detail-b"))
            runCurrent()
            assertEquals(0,calls)
            assertNull(vm.detail.value?.account)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun closingPendingDetailsPreventsALateResponseFromReopeningThem() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val gate=CompletableDeferred<CustomerAccountDetailDto>()
        val api=object:MiCatalogoCustomerAccountApi {
            override suspend fun detail(shopId:String,customerId:String)=Response.success(gate.await())
        }
        val vm=model(MemoryPreferences(),api)
        try {
            vm.openDetail(record())
            vm.closeDetail()
            gate.complete(CustomerAccountDetailDto(RemoteCustomerDto("qa-customer")))
            runCurrent()
            assertNull(vm.detail.value)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private fun record()=Customer(UUID.randomUUID(),"QA cliente",createdAt=Instant.now(),updatedAt=Instant.now(),
        remoteCustomerId="qa-customer",remoteShopId="qa-detail-a")
    private fun model(preferences:MemoryPreferences,api:MiCatalogoCustomerAccountApi):CustomerViewModel {
        val repository=CustomerRepository::class.java.cast(Proxy.newProxyInstance(CustomerRepository::class.java.classLoader,
            arrayOf(CustomerRepository::class.java)) { _,method,_ ->
            if(method.name=="observeAll") flowOf(emptyList<Customer>()) else error("Unexpected method")
        })!!
        return CustomerViewModel(CustomerUseCases(repository),Lazy { api },ShopScope(preferences,ShopSwitchLock()))
    }
    private class MemoryPreferences:DataStore<Preferences> {
        private val values=MutableStateFlow<Preferences>(preferencesOf(ShopScope.ACTIVE_SHOP_ID to "qa-detail-a"))
        override val data:Flow<Preferences> = values
        override suspend fun updateData(transform:suspend(Preferences)->Preferences)=transform(values.value).also { values.value=it }
        suspend fun select(shop:String) { edit { it[ShopScope.ACTIVE_SHOP_ID]=shop } }
    }
}
