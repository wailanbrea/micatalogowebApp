package com.example.bspos.data.micatalogo.api

import com.example.bspos.data.micatalogo.dto.LoginRequestDto
import com.example.bspos.data.micatalogo.dto.SellerSummaryDto
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
import com.example.bspos.data.micatalogo.dto.QuoteCreateRequestDto
import com.example.bspos.data.micatalogo.dto.QuoteResponseDto
import com.example.bspos.data.micatalogo.dto.OrderConfirmRequestDto
import com.example.bspos.data.micatalogo.dto.OrderConfirmResponseDto
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
import com.example.bspos.data.micatalogo.dto.DailyCloseDto
import com.example.bspos.data.micatalogo.dto.DailyCloseRequestDto
import com.example.bspos.data.micatalogo.dto.ExpenseActionResponseDto
import com.example.bspos.data.micatalogo.dto.ExpenseCategoryDto
import com.example.bspos.data.micatalogo.dto.ExpenseCreateRequestDto
import com.example.bspos.data.micatalogo.dto.ExpensePaginatedResponseDto
import com.example.bspos.data.micatalogo.dto.FeatureResponseDto
import com.example.bspos.data.micatalogo.dto.AuthorizationDecisionResponseDto
import com.example.bspos.data.micatalogo.dto.PurchaseActionResponseDto
import com.example.bspos.data.micatalogo.dto.PurchaseCreateRequestDto
import com.example.bspos.data.micatalogo.dto.PurchaseWorkspaceDto
import com.example.bspos.data.micatalogo.dto.PurchaseInvoicePreviewDto
import com.example.bspos.data.micatalogo.dto.SupplierActionResponseDto
import com.example.bspos.data.micatalogo.dto.SupplierCreateRequestDto
import com.example.bspos.data.micatalogo.dto.PricingRuleRequestDto
import com.example.bspos.data.micatalogo.dto.PricingApprovalRequestDto
import com.example.bspos.data.micatalogo.dto.PricingMutationResponseDto
import com.example.bspos.data.micatalogo.dto.PartnerCreateRequestDto
import com.example.bspos.data.micatalogo.dto.AttributeUpdateRequestDto
import com.example.bspos.data.micatalogo.dto.PartnerTransactionRequestDto
import com.example.bspos.data.micatalogo.dto.AccountantAccessRequestDto
import com.example.bspos.data.micatalogo.dto.ShopSettingsDto
import com.example.bspos.data.micatalogo.dto.ShopSettingsUpdateDto
import com.example.bspos.data.micatalogo.dto.ShopLogoUploadResponseDto
import com.example.bspos.data.micatalogo.dto.FinanceCashFlowDto
import com.example.bspos.data.micatalogo.dto.FinanceIncomeStatementDto
import com.example.bspos.data.micatalogo.dto.FinanceSummaryDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
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
import retrofit2.http.Streaming

interface MiCatalogoApi {
    @GET("api/v1/shops/{shop}/seller-summary")
    suspend fun sellerSummary(@Path("shop") shopId: String, @Query("period") period: String): Response<SellerSummaryDto>

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

    @GET("api/v1/shops/{shopId}/features/{feature}")
    suspend fun feature(
        @Path("shopId") shopId: String,
        @Path("feature") feature: String,
        @Query("period") period: String? = null,
        @Query("q") query: String? = null,
        @Query("status") status: String? = null
    ): Response<FeatureResponseDto>

    @POST("api/v1/shops/{shopId}/authorization-requests/{requestId}/approve")
    suspend fun approveAuthorization(
        @Path("shopId") shopId: String,
        @Path("requestId") requestId: String
    ): Response<AuthorizationDecisionResponseDto>

    @POST("api/v1/shops/{shopId}/authorization-requests/{requestId}/reject")
    suspend fun rejectAuthorization(
        @Path("shopId") shopId: String,
        @Path("requestId") requestId: String
    ): Response<AuthorizationDecisionResponseDto>

    @PUT("api/v1/shops/{shopId}/attributes/{attributeId}")
    suspend fun updateAttribute(
        @Path("shopId") shopId: String,
        @Path("attributeId") attributeId: String,
        @Body request: AttributeUpdateRequestDto
    ): Response<PricingMutationResponseDto>

    @POST("api/v1/shops/{shopId}/products/{productId}/pricing-rule")
    suspend fun savePricingRule(
        @Path("shopId") shopId: String,
        @Path("productId") productId: String,
        @Body request: PricingRuleRequestDto
    ): Response<PricingMutationResponseDto>

    @POST("api/v1/shops/{shopId}/pricing/recalculate")
    suspend fun recalculatePricing(
        @Path("shopId") shopId: String
    ): Response<PricingMutationResponseDto>

    @POST("api/v1/shops/{shopId}/products/{productId}/pricing-approval")
    suspend fun approvePricingRule(
        @Path("shopId") shopId: String,
        @Path("productId") productId: String,
        @Body request: PricingApprovalRequestDto
    ): Response<PricingMutationResponseDto>

    @POST("api/v1/shops/{shopId}/partners")
    suspend fun createPartner(
        @Path("shopId") shopId: String,
        @Body request: PartnerCreateRequestDto
    ): Response<PricingMutationResponseDto>

    @POST("api/v1/shops/{shopId}/partners/{partnerId}/transactions")
    suspend fun recordPartnerTransaction(
        @Path("shopId") shopId: String,
        @Path("partnerId") partnerId: String,
        @Body request: PartnerTransactionRequestDto
    ): Response<PricingMutationResponseDto>

    @POST("api/v1/shops/{shopId}/accountant-access")
    suspend fun grantAccountantAccess(
        @Path("shopId") shopId: String,
        @Body request: AccountantAccessRequestDto
    ): Response<PricingMutationResponseDto>

    @GET("api/v1/shops/{shopId}/settings")
    suspend fun shopSettings(@Path("shopId") shopId: String): Response<ShopSettingsDto>

    @PUT("api/v1/shops/{shopId}/settings")
    suspend fun updateShopSettings(
        @Path("shopId") shopId: String,
        @Body request: ShopSettingsUpdateDto
    ): Response<ShopSettingsDto>

    @Multipart
    @POST("api/v1/shops/{shopId}/media/logo")
    suspend fun uploadShopLogo(
        @Path("shopId") shopId: String,
        @Part logo: MultipartBody.Part
    ): Response<ShopLogoUploadResponseDto>

    @Streaming
    @GET("api/v1/shops/{shopId}/reports/export")
    suspend fun exportReport(
        @Path("shopId") shopId: String,
        @Query("format") format: String
    ): Response<ResponseBody>

    @GET("api/v1/shops/{shopId}/purchases")
    suspend fun purchases(@Path("shopId") shopId: String): Response<PurchaseWorkspaceDto>

    @GET("api/v1/shops/{shopId}/suppliers")
    suspend fun suppliers(@Path("shopId") shopId: String): Response<PurchaseWorkspaceDto>

    @POST("api/v1/shops/{shopId}/suppliers")
    suspend fun createSupplier(
        @Path("shopId") shopId: String,
        @Body request: SupplierCreateRequestDto
    ): Response<SupplierActionResponseDto>

    @Multipart
    @POST("api/v1/shops/{shopId}/purchases/preview")
    suspend fun previewPurchaseInvoice(
        @Path("shopId") shopId: String,
        @Part file: MultipartBody.Part
    ): Response<PurchaseInvoicePreviewDto>

    @POST("api/v1/shops/{shopId}/purchases")
    suspend fun createPurchase(
        @Path("shopId") shopId: String,
        @Body request: PurchaseCreateRequestDto
    ): Response<PurchaseActionResponseDto>

    @POST("api/v1/shops/{shopId}/purchases/{documentId}/receive")
    suspend fun receivePurchase(
        @Path("shopId") shopId: String,
        @Path("documentId") documentId: String
    ): Response<PurchaseActionResponseDto>

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

    @POST("api/v1/shops/{shopId}/quotes")
    suspend fun createQuote(
        @Path("shopId") shopId: String,
        @Body request: QuoteCreateRequestDto
    ): Response<QuoteResponseDto>

    @POST("api/v1/shops/{shopId}/quotes/{quoteId}/convert")
    suspend fun convertQuote(
        @Path("shopId") shopId: String,
        @Path("quoteId") quoteId: String
    ): Response<QuoteResponseDto>

    @POST("api/v1/shops/{shopId}/orders/{orderId}/confirm")
    suspend fun confirmOrder(
        @Path("shopId") shopId: String,
        @Path("orderId") orderId: String,
        @Body request: OrderConfirmRequestDto
    ): Response<OrderConfirmResponseDto>

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

    @GET("api/v1/shops/{shopId}/finance/day-close")
    suspend fun dailyClose(
        @Path("shopId") shopId: String,
        @Query("date") date: String
    ): Response<DailyCloseDto>

    @POST("api/v1/shops/{shopId}/finance/day-close")
    suspend fun closeDay(
        @Path("shopId") shopId: String,
        @Body request: DailyCloseRequestDto
    ): Response<DailyCloseDto>

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
