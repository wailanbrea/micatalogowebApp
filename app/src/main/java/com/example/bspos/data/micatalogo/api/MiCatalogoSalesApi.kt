package com.example.bspos.data.micatalogo.api

import com.example.bspos.data.micatalogo.dto.FeatureResponseDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class SaleVoidRequestDto(
    @SerialName("invoice_id") val invoiceId: Long? = null,
    @SerialName("client_sale_uuid") val clientSaleUuid: String? = null,
    val reason: String,
    val restock: Boolean = true
)

@Serializable
data class SaleVoidResponseDto(
    val id: String = "",
    @SerialName("invoice_number") val invoiceNumber: String = "",
    val status: String = ""
)

interface MiCatalogoSalesApi {
    @GET("api/v1/shops/{shop}/features/sales")
    suspend fun sales(@Path("shop") shop: String, @Query("period") period: String, @Query("status") status: String,
        @Query("q") query: String?, @Query("origin") origin: String, @Query("min_amount") minimum: String?, @Query("max_amount") maximum: String?): Response<FeatureResponseDto>
    @GET("api/v1/shops/{shop}/sales/export")
    suspend fun export(@Path("shop") shop: String, @Query("period") period: String, @Query("status") status: String,
        @Query("q") query: String?, @Query("origin") origin: String, @Query("min_amount") minimum: String?, @Query("max_amount") maximum: String?): Response<okhttp3.ResponseBody>
    @POST("api/v1/shops/{shop}/sales/void")
    suspend fun voidSale(@Path("shop") shop: String, @Body body: SaleVoidRequestDto): Response<SaleVoidResponseDto>
}
