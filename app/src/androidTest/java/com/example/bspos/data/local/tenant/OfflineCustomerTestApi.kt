package com.example.bspos.data.local.tenant

import com.example.bspos.data.micatalogo.api.*
import com.example.bspos.data.micatalogo.dto.*
import retrofit2.Response
import java.io.IOException

/** Exercises the real offline path without contacting any business server. */
object OfflineCustomerTestApi : MiCatalogoCustomerApi {
        override suspend fun payments(shopId: String, beforeId: String?, paymentIds: List<String>?): Response<com.example.bspos.data.micatalogo.api.CustomerPaymentHistoryDto> = Response.success(com.example.bspos.data.micatalogo.api.CustomerPaymentHistoryDto())
        override suspend fun reversePayment(shopId: String, customerId: String, paymentId: String, body: com.example.bspos.data.micatalogo.api.ReverseCustomerPaymentDto): Response<com.example.bspos.data.micatalogo.api.ReverseCustomerPaymentResponseDto> = error("Unused reversal")

    override suspend fun customers(shopId: String) = Response.success(CustomerListDto(emptyList()))
    override suspend fun createCustomer(shopId: String, request: CustomerUploadRequestDto): Response<RemoteCustomerDto> =
        throw IOException("Isolated offline fixture")
    override suspend fun payment(shopId: String, customerId: String, body: CustomerPaymentDto): Response<CustomerPaymentResponseDto> =
        error("Payments must not use the offline customer fixture")
}
