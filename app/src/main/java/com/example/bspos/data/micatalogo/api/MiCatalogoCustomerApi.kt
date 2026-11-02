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
import retrofit2.http.Query

interface MiCatalogoCustomerApi {
    @GET("api/v1/shops/{shopId}/customer-payments")
    suspend fun payments(@Path("shopId") shopId: String, @Query("before_id") beforeId: String? = null,
        @Query("payment_ids[]") paymentIds: List<String>? = null): Response<CustomerPaymentHistoryDto>
    @POST("api/v1/shops/{shopId}/customers/{customerId}/payments/{paymentId}/reverse")
    suspend fun reversePayment(@Path("shopId") shopId: String, @Path("customerId") customerId: String,
        @Path("paymentId") paymentId: String, @Body body: ReverseCustomerPaymentDto): Response<ReverseCustomerPaymentResponseDto>
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
    @SerialName("payment_id") val paymentId: Long = 0,
    @SerialName("client_transaction_uuid") val clientTransactionUuid: String = "",
    val amount: String = "0.00",
    @SerialName("payment_method") val paymentMethod: String = "cash",
    @SerialName("customer_balance") val customerBalance: String = "0.00",
    val allocations: List<CustomerPaymentAllocationDto> = emptyList(),
    @SerialName("cash_register_affected") val cashRegisterAffected: Boolean = false,
    @SerialName("server_timestamp") val serverTimestamp: String = ""
)
@Serializable data class CustomerPaymentAllocationDto(
    @SerialName("invoice_id") val invoiceId: Long = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("allocated_amount") val allocatedAmount: String = "0.00",
    @SerialName("allocated_cents") val allocatedCents: Long = 0,
    @SerialName("remaining_invoice_balance") val remainingInvoiceBalance: String = "0.00",
    @SerialName("invoice_status") val invoiceStatus: String = ""
)
@Serializable data class CustomerListDto(val customers: List<RemoteCustomerDto>)

@Serializable data class CustomerPaymentHistoryDto(val payments: List<RemotePaymentHistoryDto> = emptyList(), @SerialName("next_cursor") val nextCursor: String? = null)
@Serializable data class RemotePaymentHistoryDto(
    val id: String, @SerialName("customer_id") val customerId: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("client_transaction_uuid") val clientTransactionUuid: String? = null,
    val receipt: String, val amount: String, @SerialName("payment_method") val paymentMethod: String,
    val notes: String? = null, @SerialName("created_at") val createdAt: String,
    @SerialName("can_reverse") val canReverse: Boolean = false, val reversal: CustomerPaymentReversalDto? = null
)
@Serializable data class CustomerPaymentReversalDto(val id: String, val reason: String,
    @SerialName("user_name") val userName: String? = null, @SerialName("created_at") val createdAt: String)
@Serializable data class ReverseCustomerPaymentDto(@SerialName("client_transaction_uuid") val uuid: String, val reason: String)
@Serializable data class ReverseCustomerPaymentResponseDto(val payment: RemotePaymentHistoryDto, val customer: RemoteCustomerDto)
