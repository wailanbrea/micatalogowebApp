package com.example.bspos.data.micatalogo.api

import com.example.bspos.data.micatalogo.dto.LoginRequestDto
import com.example.bspos.data.micatalogo.dto.MenuPermissionsUpdateDto
import com.example.bspos.data.micatalogo.dto.LoginResponseDto
import com.example.bspos.data.micatalogo.dto.CatalogSnapshotDto
import com.example.bspos.data.micatalogo.dto.AndroidUpdateDto
import com.example.bspos.data.micatalogo.dto.AdminShopDto
import com.example.bspos.data.micatalogo.dto.AdminShopUpdateDto
import com.example.bspos.data.micatalogo.dto.MeDto
import com.example.bspos.data.micatalogo.dto.ShopDto
import com.example.bspos.data.micatalogo.dto.PosSaleUploadRequestDto
import com.example.bspos.data.micatalogo.dto.PosSaleUploadResponseDto
import com.example.bspos.data.micatalogo.dto.ProfileUpdateDto
import com.example.bspos.data.micatalogo.dto.SellerCreateRequestDto
import com.example.bspos.data.micatalogo.dto.SellerCreateResponseDto
import com.example.bspos.data.micatalogo.dto.InventoryImportPreviewDto
import com.example.bspos.data.micatalogo.dto.InventoryImportRequestDto
import com.example.bspos.data.micatalogo.dto.InventoryImportResponseDto
import com.example.bspos.data.micatalogo.dto.CashCurrentSessionResponseDto
import com.example.bspos.data.micatalogo.dto.CashMovementActionResponseDto
import com.example.bspos.data.micatalogo.dto.CashMovementRequestDto
import com.example.bspos.data.micatalogo.dto.CashSessionActionResponseDto
import com.example.bspos.data.micatalogo.dto.CashSessionCloseRequestDto
import com.example.bspos.data.micatalogo.dto.CashSessionOpenRequestDto
import com.example.bspos.data.micatalogo.dto.ExpenseActionResponseDto
import com.example.bspos.data.micatalogo.dto.ExpenseCategoryDto
import com.example.bspos.data.micatalogo.dto.ExpenseCreateRequestDto
import com.example.bspos.data.micatalogo.dto.ExpensePaginatedResponseDto
import com.example.bspos.data.micatalogo.dto.FinanceCashFlowDto
import com.example.bspos.data.micatalogo.dto.FinanceIncomeStatementDto
import com.example.bspos.data.micatalogo.dto.FinanceSummaryDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

interface MiCatalogoApi {
    @GET("api/v1/app-updates/android")
    suspend fun androidUpdate(): Response<AndroidUpdateDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>

    @GET("api/v1/me")
    suspend fun me(): Response<MeDto>

    @PUT("api/v1/me")
    suspend fun updateMe(@Body request: ProfileUpdateDto): Response<MeDto>

    @GET("api/v1/shops")
    suspend fun shops(): Response<List<ShopDto>>

    @GET("api/v1/admin/shops")
    suspend fun adminShops(): Response<List<AdminShopDto>>

    @PUT("api/v1/admin/shops/{shopId}")
    suspend fun updateAdminShop(
        @Path("shopId") shopId: String,
        @Body request: AdminShopUpdateDto
    ): Response<AdminShopDto>

    @GET("api/v1/shops/{shopId}/catalog")
    suspend fun catalog(@Path("shopId") shopId: String): Response<CatalogSnapshotDto>

    @Multipart
    @POST("api/v1/shops/{shopId}/inventory-import/preview")
    suspend fun previewInventoryImport(
        @Path("shopId") shopId: String,
        @Part file: MultipartBody.Part,
        @PartMap mapping: Map<String, @JvmSuppressWildcards RequestBody> = emptyMap()
    ): Response<InventoryImportPreviewDto>

    @POST("api/v1/shops/{shopId}/inventory-import")
    suspend fun importInventory(
        @Path("shopId") shopId: String,
        @Body request: InventoryImportRequestDto
    ): Response<InventoryImportResponseDto>

    @PUT("api/v1/shops/{shopId}/sellers/{sellerId}/menus")
    suspend fun updateSellerMenus(
        @Path("shopId") shopId: String,
        @Path("sellerId") sellerId: String,
        @Body request: MenuPermissionsUpdateDto
    ): Response<Map<String, List<String>>>

    @POST("api/v1/shops/{shopId}/sellers")
    suspend fun createSeller(
        @Path("shopId") shopId: String,
        @Body request: SellerCreateRequestDto
    ): Response<SellerCreateResponseDto>

    @POST("api/v1/shops/{shopId}/pos-sales")
    suspend fun uploadPosSale(
        @Path("shopId") shopId: String,
        @Body request: PosSaleUploadRequestDto
    ): Response<PosSaleUploadResponseDto>

    @GET("api/v1/shops/{shopId}/finance/summary")
    suspend fun financeSummary(
        @Path("shopId") shopId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("sort") sort: String? = null,
        @Query("direction") direction: String? = null
    ): Response<FinanceSummaryDto>

    @GET("api/v1/shops/{shopId}/reports/income-statement")
    suspend fun incomeStatement(
        @Path("shopId") shopId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<FinanceIncomeStatementDto>

    @GET("api/v1/shops/{shopId}/reports/cash-flow")
    suspend fun cashFlow(
        @Path("shopId") shopId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<FinanceCashFlowDto>

    @GET("api/v1/shops/{shopId}/cash-sessions/current")
    suspend fun currentCashSession(
        @Path("shopId") shopId: String
    ): Response<CashCurrentSessionResponseDto>

    @POST("api/v1/shops/{shopId}/cash-sessions/open")
    suspend fun openCashSession(
        @Path("shopId") shopId: String,
        @Body request: CashSessionOpenRequestDto
    ): Response<CashSessionActionResponseDto>

    @POST("api/v1/shops/{shopId}/cash-sessions/{sessionId}/close")
    suspend fun closeCashSession(
        @Path("shopId") shopId: String,
        @Path("sessionId") sessionId: String,
        @Body request: CashSessionCloseRequestDto
    ): Response<CashSessionActionResponseDto>

    @POST("api/v1/shops/{shopId}/cash-sessions/{sessionId}/movements")
    suspend fun recordCashMovement(
        @Path("shopId") shopId: String,
        @Path("sessionId") sessionId: String,
        @Body request: CashMovementRequestDto
    ): Response<CashMovementActionResponseDto>

    @GET("api/v1/shops/{shopId}/expenses")
    suspend fun expenses(
        @Path("shopId") shopId: String,
        @Query("page") page: Int = 1
    ): Response<ExpensePaginatedResponseDto>

    @GET("api/v1/shops/{shopId}/expense-categories")
    suspend fun expenseCategories(
        @Path("shopId") shopId: String
    ): Response<List<ExpenseCategoryDto>>

    @POST("api/v1/shops/{shopId}/expenses")
    suspend fun createExpense(
        @Path("shopId") shopId: String,
        @Body request: ExpenseCreateRequestDto
    ): Response<ExpenseActionResponseDto>
}
