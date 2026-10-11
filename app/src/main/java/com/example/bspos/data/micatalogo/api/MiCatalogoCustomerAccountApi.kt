package com.example.bspos.data.micatalogo.api

import com.example.bspos.data.micatalogo.dto.CustomerAccountDetailDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface MiCatalogoCustomerAccountApi {
    @GET("api/v1/shops/{shopId}/customers/{customerId}")
    suspend fun detail(@Path("shopId") shopId:String,@Path("customerId") customerId:String):Response<CustomerAccountDetailDto>
}
