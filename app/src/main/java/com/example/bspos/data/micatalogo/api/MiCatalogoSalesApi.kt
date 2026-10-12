package com.example.bspos.data.micatalogo.api

import com.example.bspos.data.micatalogo.dto.FeatureResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MiCatalogoSalesApi {
    @GET("api/v1/shops/{shop}/features/sales")
    suspend fun sales(@Path("shop") shop: String, @Query("period") period: String, @Query("status") status: String,
        @Query("q") query: String?, @Query("origin") origin: String, @Query("min_amount") minimum: String?, @Query("max_amount") maximum: String?): Response<FeatureResponseDto>
    @GET("api/v1/shops/{shop}/sales/export")
    suspend fun export(@Path("shop") shop: String, @Query("period") period: String, @Query("status") status: String,
        @Query("q") query: String?, @Query("origin") origin: String, @Query("min_amount") minimum: String?, @Query("max_amount") maximum: String?): Response<okhttp3.ResponseBody>
}
