package com.example.bspos.data.micatalogo.api

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface MiCatalogoOperationApi {
    @POST("api/v1/shops/{shopId}/mobile-operations")
    suspend fun submit(@Path("shopId") shopId: String, @Body payload: JsonObject): Response<JsonObject>
}
