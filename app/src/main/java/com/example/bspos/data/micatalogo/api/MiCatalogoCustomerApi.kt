package com.example.bspos.data.micatalogo.api

import com.example.bspos.data.micatalogo.dto.CustomerUploadRequestDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import retrofit2.http.Path

interface MiCatalogoCustomerApi {
    @POST("api/v1/shops/{shopId}/customers/{customerId}/payments")
    suspend fun payment(@Path("shopId") shopId: String, @Path("customerId") customerId: String,
        @Body body: CustomerPaymentDto): Response<CustomerPaymentResponseDto>
    @GET("api/v1/shops/{shopId}/customers")
    suspend fun customers(@Path("shopId") shopId: String): Response<CustomerListDto>
    @POST("api/v1/shops/{shopId}/customers")
    suspend fun createCustomer(
        @Path("shopId") shopId: String,
        @Body request: CustomerUploadRequestDto
    ): Response<RemoteCustomerDto>
}

@Serializable data class CustomerPaymentDto(
    @SerialName("client_transaction_uuid") val uuid: String,
    val amount: String,
    @SerialName("payment_method") val paymentMethod: String = "cash",
    val reference: String? = null,
    val notes: String? = null
)
@Serializable data class CustomerPaymentResponseDto(val customer: RemoteCustomerDto)
@Serializable data class CustomerListDto(val customers: List<RemoteCustomerDto>)
