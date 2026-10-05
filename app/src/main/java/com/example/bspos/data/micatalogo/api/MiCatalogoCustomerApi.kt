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
@Serializable data class CustomerPaymentResponseDto(
    val customer: RemoteCustomerDto,
    @SerialName("payment_id") val paymentId: Long,
    @SerialName("client_transaction_uuid") val clientTransactionUuid: String,
    val amount: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("customer_balance") val customerBalance: String,
    val allocations: List<CustomerPaymentAllocationDto>,
    @SerialName("cash_register_affected") val cashRegisterAffected: Boolean,
    @SerialName("server_timestamp") val serverTimestamp: String
)
@Serializable data class CustomerPaymentAllocationDto(
    @SerialName("invoice_id") val invoiceId: Long,
    @SerialName("invoice_number") val invoiceNumber: String,
    @SerialName("allocated_amount") val allocatedAmount: String,
    @SerialName("allocated_cents") val allocatedCents: Long,
    @SerialName("remaining_invoice_balance") val remainingInvoiceBalance: String,
    @SerialName("invoice_status") val invoiceStatus: String
)
@Serializable data class CustomerListDto(val customers: List<RemoteCustomerDto>)
