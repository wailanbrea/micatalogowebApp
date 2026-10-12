package com.example.bspos.data.local.tenant

import com.example.bspos.data.micatalogo.api.*
import com.example.bspos.data.micatalogo.dto.*
import retrofit2.Response
import java.io.IOException

/** Exercises the real offline path without contacting any business server. */
object OfflineCustomerTestApi : MiCatalogoCustomerApi {
    override suspend fun customers(shopId: String) = Response.success(CustomerListDto(emptyList()))
    override suspend fun createCustomer(shopId: String, request: CustomerUploadRequestDto): Response<RemoteCustomerDto> =
        throw IOException("Isolated offline fixture")
    override suspend fun payment(shopId: String, customerId: String, body: CustomerPaymentDto): Response<CustomerPaymentResponseDto> =
        error("Payments must not use the offline customer fixture")
}
