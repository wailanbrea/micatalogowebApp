package com.example.bspos.data.micatalogo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class ProfileUpdateDto(
    val name: String,
    val email: String
)

@Serializable
data class LoginResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    val token: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val user: MeDto? = null
)

@Serializable
data class AndroidUpdateDto(
    @SerialName("version_code") val versionCode: Int = 0,
    @SerialName("version_name") val versionName: String = "",
    @SerialName("minimum_supported_version_code") val minimumSupportedVersionCode: Int = 0,
    @SerialName("apk_url") val apkUrl: String = "",
    @SerialName("apk_sha256") val apkSha256: String = "",
    @SerialName("release_notes") val releaseNotes: String = ""
)

@Serializable
data class MeDto(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null
)

@Serializable
data class ShopDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val quota: ShopQuotaDto? = null,
    @SerialName("menu_permissions") val menuPermissions: List<String> = emptyList(),
    @SerialName("can_manage_sellers") val canManageSellers: Boolean = false,
    val sellers: List<ShopSellerDto> = emptyList()
)

@Serializable
data class AdminShopDto(
    val id: String,
    val name: String,
    val slug: String,
    @SerialName("owner_name") val ownerName: String,
    @SerialName("owner_email") val ownerEmail: String,
    @SerialName("whatsapp_country_code") val whatsappCountryCode: String,
    @SerialName("whatsapp_number") val whatsappNumber: String,
    val status: String,
    @SerialName("product_count") val productCount: Int
)

@Serializable
data class AdminShopUpdateDto(
    val name: String,
    @SerialName("whatsapp_country_code") val whatsappCountryCode: String,
    @SerialName("whatsapp_number") val whatsappNumber: String,
    val status: String
)

@Serializable
data class ShopSellerDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val email: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("menu_permissions") val menuPermissions: List<String> = emptyList()
)

@Serializable
data class MenuPermissionsUpdateDto(
    @SerialName("menu_permissions") val menuPermissions: List<String>
)

@Serializable
data class SellerCreateRequestDto(
    val email: String,
    @SerialName("commission_type") val commissionType: String,
    @SerialName("commission_value") val commissionValue: String
)

@Serializable
data class SellerCreateResponseDto(
    val message: String = "",
    val seller: ShopSellerDto? = null
)

@Serializable
data class ShopQuotaDto(
    val plan: String,
    @SerialName("plan_label") val planLabel: String,
    @SerialName("product_count") val productCount: Int,
    @SerialName("product_limit") val productLimit: Int,
    @SerialName("products_remaining") val productsRemaining: Int,
    @SerialName("image_limit") val imageLimit: Int,
    @SerialName("can_add_products") val canAddProducts: Boolean,
    @SerialName("user_count") val userCount: Int = 0,
    @SerialName("user_limit") val userLimit: Int = 0,
    @SerialName("users_remaining") val usersRemaining: Int = 0,
    @SerialName("seller_count") val sellerCount: Int = 0,
    @SerialName("seller_limit") val sellerLimit: Int = 0,
    @SerialName("sellers_remaining") val sellersRemaining: Int = 0,
    @SerialName("can_add_users") val canAddUsers: Boolean = false,
    @SerialName("can_add_sellers") val canAddSellers: Boolean = false,
    @SerialName("additional_seat_price_usd") val additionalSeatPriceUsd: Double = 5.0,
    val features: List<String> = emptyList()
)

@Serializable
data class InventoryImportPreviewDto(
    val quota: ShopQuotaDto,
    val headers: List<String> = emptyList(),
    val mapping: Map<String, String> = emptyMap(),
    val fields: Map<String, String> = emptyMap(),
    val rows: List<InventoryImportRowDto> = emptyList(),
    @SerialName("valid_rows") val validRows: Int = 0,
    @SerialName("invalid_rows") val invalidRows: Int = 0
)

@Serializable
data class InventoryImportRowDto(
    val line: Int = 0,
    val name: String = "",
    @SerialName("product_code") val productCode: String? = null,
    val barcode: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val price: Double? = null,
    @SerialName("cost_price") val costPrice: Double? = null,
    val stock: Int? = null,
    val attributes: List<InventoryImportAttributeDto> = emptyList(),
    val errors: List<String> = emptyList(),
    val valid: Boolean = false
)

@Serializable
data class InventoryImportAttributeDto(
    val name: String = "",
    val value: String = ""
)

@Serializable
data class InventoryImportRequestDto(
    val rows: List<InventoryImportRowDto>
)

@Serializable
data class InventoryImportResponseDto(
    val message: String = "",
    val imported: Int = 0,
    val quota: ShopQuotaDto? = null
)

@Serializable
data class ApiErrorDto(
    val message: String? = null,
    val errors: Map<String, List<String>> = emptyMap()
)

@Serializable
data class CatalogSnapshotDto(
    val shop: ShopDto,
    val categories: List<RemoteCategoryDto>,
    val products: List<RemoteProductDto>,
    @SerialName("generated_at") val generatedAt: String
)

@Serializable
data class RemoteCategoryDto(
    val id: String,
    val name: String,
    val slug: String,
    @SerialName("sort_order") val sortOrder: Int,
    val status: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class RemoteProductDto(
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("source_product_id") val sourceProductId: String? = null,
    val name: String,
    @SerialName("internal_code") val internalCode: String? = null,
    val brand: String? = null,
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val price: String? = null,
    @SerialName("wholesale_price") val wholesalePrice: String? = null,
    val currency: String,
    @SerialName("sale_unit") val saleUnit: String? = null,
    @SerialName("volume_ml") val volumeMl: Int? = null,
    @SerialName("availability_status") val availabilityStatus: String,
    @SerialName("moderation_status") val moderationStatus: String,
    val inventory: RemoteInventoryDto,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class RemoteInventoryDto(
    @SerialName("track_inventory") val trackInventory: Boolean,
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("available_ml") val availableMl: Int? = null,
    @SerialName("cost_price") val costPrice: String? = null,
    @SerialName("low_stock_threshold") val lowStockThreshold: Int? = null
)

@Serializable
data class PosSaleUploadRequestDto(
    @SerialName("client_sale_uuid") val clientSaleUuid: String,
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("sale_mode") val saleMode: String = "retail",
    val items: List<PosSaleUploadItemDto>,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("credit_amount") val creditAmount: String? = null,
    val discount: String = "0.00",
    val tax: String = "0.00",
    val payments: List<PosPaymentSplitDto>? = null,
    @SerialName("due_date") val dueDate: String? = null
)

@Serializable
data class PosPaymentSplitDto(
    val method: String,
    val amount: String,
    val reference: String? = null,
    val notes: String? = null
)

@Serializable
data class CustomerUploadRequestDto(
    @SerialName("client_customer_uuid") val clientCustomerUuid: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("document_type") val documentType: String,
    @SerialName("document_number") val documentNumber: String,
    val name: String? = null,
    val phone: String,
    val email: String? = null,
    val address: String,
    val whatsapp: String? = null,
    val reference: String? = null,
    @SerialName("credit_limit") val creditLimit: String,
    val notes: String? = null
)

@Serializable
data class RemoteCustomerDto(
    val id: String,
    @SerialName("client_customer_uuid") val clientCustomerUuid: String? = null,
    val name: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("document_type") val documentType: String? = null,
    @SerialName("document_number") val documentNumber: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val whatsapp: String? = null,
    val reference: String? = null,
    @SerialName("credit_limit") val creditLimit: String? = null,
    val balance: String? = null,
    @SerialName("available_credit") val availableCredit: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PosSaleUploadItemDto(
    @SerialName("product_id") val productId: String,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: String,
    val discount: String = "0.00",
    val tax: String = "0.00",
    @SerialName("expected_sale_unit") val expectedSaleUnit: String? = null,
    @SerialName("expected_volume_ml") val expectedVolumeMl: Int? = null,
    @SerialName("expected_source_product_id") val expectedSourceProductId: String? = null
)

@Serializable
data class PosSaleUploadResponseDto(
    @SerialName("client_sale_uuid") val clientSaleUuid: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    val status: String? = null,
    val total: String? = null
)

@Serializable
data class FinanceSummaryDto(
    val from: String,
    val to: String,
    val period: FinancePeriodDto,
    @SerialName("previous_period") val previousPeriod: FinancePeriodDto? = null,
    val comparison: FinanceComparisonDto? = null,
    @SerialName("current_state") val currentState: FinanceCurrentStateDto,
    @SerialName("income_statement") val incomeStatement: FinanceIncomeStatementDto? = null,
    @SerialName("cash_flow") val cashFlow: FinanceCashFlowDto? = null,
    val profitability: List<FinanceProductProfitabilityDto> = emptyList(),
    @SerialName("requires_attention") val requiresAttention: List<FinanceAlertDto> = emptyList()
)

@Serializable
data class FinancePeriodDto(
    @SerialName("gross_sales") val grossSales: Double,
    val discounts: Double,
    @SerialName("line_discounts") val lineDiscounts: Double,
    @SerialName("general_discounts") val generalDiscounts: Double,
    @SerialName("tax_collected") val taxCollected: Double,
    val returns: Double,
    @SerialName("net_sales") val netSales: Double,
    @SerialName("fifo_cogs") val fifoCogs: Double? = null,
    @SerialName("units_sold") val unitsSold: Int = 0,
    @SerialName("cost_coverage_percent") val costCoveragePercent: Double,
    @SerialName("revenue_cost_coverage") val revenueCostCoverage: Double,
    @SerialName("is_cost_coverage_partial") val isCostCoveragePartial: Boolean = false,
    @SerialName("gross_profit") val grossProfit: Double? = null,
    @SerialName("gross_margin_percent") val grossMarginPercent: Double? = null,
    @SerialName("operating_expenses") val operatingExpenses: Double,
    @SerialName("commissions_generated") val commissionsGenerated: Double,
    @SerialName("operating_profit") val operatingProfit: Double? = null,
    @SerialName("operating_margin_percent") val operatingMarginPercent: Double? = null,
    @SerialName("sales_count") val salesCount: Int = 0,
    @SerialName("average_ticket") val averageTicket: Double,
    @SerialName("collected_in_period") val collectedInPeriod: Double,
    @SerialName("credit_generated") val creditGenerated: Double
)

@Serializable
data class FinanceComparisonDto(
    @SerialName("net_sales_delta") val netSalesDelta: Double? = null,
    @SerialName("gross_profit_delta") val grossProfitDelta: Double? = null,
    @SerialName("average_ticket_delta") val averageTicketDelta: Double? = null,
    @SerialName("sales_count_delta") val salesCountDelta: Double? = null,
    @SerialName("margin_delta") val marginDelta: Double? = null
)

@Serializable
data class FinanceCurrentStateDto(
    @SerialName("receivable_total") val receivableTotal: Double,
    val aging: FinanceAgingDto,
    @SerialName("inventory_cost_value") val inventoryCostValue: Double,
    @SerialName("active_products_count") val activeProductsCount: Int = 0,
    @SerialName("low_stock_count") val lowStockCount: Int = 0,
    @SerialName("out_of_stock_count") val outOfStockCount: Int = 0,
    @SerialName("missing_cost_count") val missingCostCount: Int = 0,
    @SerialName("pending_orders_count") val pendingOrdersCount: Int = 0,
    @SerialName("pending_price_rules_count") val pendingPriceRulesCount: Int = 0,
    @SerialName("has_open_cash_session") val hasOpenCashSession: Boolean = false
)

@Serializable
data class FinanceAgingDto(
    @SerialName("days_0_30") val days0To30: Double,
    @SerialName("days_31_60") val days31To60: Double,
    @SerialName("days_61_90") val days61To90: Double,
    @SerialName("days_over_90") val daysOver90: Double,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    @SerialName("invoices_total") val invoicesTotal: Double,
    @SerialName("unallocated_receivables") val unallocatedReceivables: Double,
    @SerialName("total_receivable") val totalReceivable: Double,
    @SerialName("reconciliation_difference") val reconciliationDifference: Double,
    @SerialName("invoice_details") val invoiceDetails: List<FinanceInvoiceAgingDto> = emptyList()
)

@Serializable
data class FinanceInvoiceAgingDto(
    @SerialName("invoice_number") val invoiceNumber: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("reference_date") val referenceDate: String,
    @SerialName("overdue_days") val overdueDays: Int = 0,
    @SerialName("outstanding_amount") val outstandingAmount: Double,
    @SerialName("aging_bucket") val agingBucket: String
)

@Serializable
data class FinanceIncomeStatementDto(
    @SerialName("gross_sales") val grossSales: Double,
    val discounts: Double,
    val returns: Double,
    @SerialName("tax_collected") val taxCollected: Double,
    @SerialName("net_sales") val netSales: Double,
    @SerialName("fifo_cogs") val fifoCogs: Double? = null,
    @SerialName("gross_profit") val grossProfit: Double? = null,
    @SerialName("gross_margin_percent") val grossMarginPercent: Double? = null,
    @SerialName("operating_expenses_total") val operatingExpensesTotal: Double,
    val commissions: Double,
    @SerialName("operating_profit") val operatingProfit: Double? = null,
    @SerialName("operating_margin_percent") val operatingMarginPercent: Double? = null
)

@Serializable
data class FinanceCashFlowDto(
    val inflows: FinanceCashFlowInflowsDto,
    val outflows: FinanceCashFlowOutflowsDto,
    @SerialName("net_cash_flow") val netCashFlow: Double
)

@Serializable
data class FinanceCashFlowInflowsDto(
    @SerialName("sales_cash") val salesCash: Double,
    @SerialName("sales_card") val salesCard: Double,
    @SerialName("sales_transfer") val salesTransfer: Double,
    @SerialName("sales_other") val salesOther: Double,
    @SerialName("debt_collections") val debtCollections: Double,
    @SerialName("other_inflows") val otherInflows: Double,
    val total: Double
)

@Serializable
data class FinanceCashFlowOutflowsDto(
    @SerialName("expenses_paid") val expensesPaid: Double,
    @SerialName("cash_out") val cashOut: Double,
    val total: Double
)

@Serializable
data class FinanceProductProfitabilityDto(
    @SerialName("product_id") val productId: Long? = null,
    @SerialName("product_name") val productName: String,
    val units: Int = 0,
    val revenue: Double = 0.0,
    val cost: Double? = null,
    @SerialName("gross_profit") val grossProfit: Double? = null,
    @SerialName("margin_percent") val marginPercent: Double? = null,
    @SerialName("has_unknown_cost") val hasUnknownCost: Boolean = false
)

@Serializable
data class FinanceAlertDto(
    val type: String,
    val count: Int,
    val label: String,
    @SerialName("action_label") val actionLabel: String,
    val url: String? = null,
    val severity: String = "info"
)

@Serializable
data class CashCurrentSessionResponseDto(
    val session: CashSessionDetailDto? = null,
    @SerialName("has_open_session") val hasOpenSession: Boolean
)

@Serializable
data class CashSessionDetailDto(
    val id: String,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("opening_amount") val openingAmount: Double = 0.0,
    val status: String,
    val notes: String? = null,
    val summary: CashSessionSummaryDto? = null
)

@Serializable
data class CashSessionSummaryDto(
    @SerialName("opening_amount") val openingAmount: Double,
    @SerialName("sales_cash") val salesCash: Double,
    @SerialName("cash_in") val cashIn: Double,
    @SerialName("cash_out") val cashOut: Double,
    @SerialName("owner_contributions") val ownerContribution: Double,
    @SerialName("owner_withdrawals") val ownerWithdrawal: Double,
    @SerialName("debt_collections_cash") val debtCollectionsCash: Double,
    @SerialName("expenses_cash") val expensesCash: Double,
    @SerialName("supplier_payments") val supplierPayments: Double,
    val adjustments: Double,
    @SerialName("total_in") val totalIn: Double,
    @SerialName("total_out") val totalOut: Double,
    @SerialName("expected_closing_amount") val expectedClosingAmount: Double,
    @SerialName("counted_amount") val countedAmount: Double? = null,
    val difference: Double? = null,
    val status: String,
    @SerialName("movements_count") val movementsCount: Int
)

@Serializable
data class CashSessionActionResponseDto(
    val message: String = "",
    val session: CashSessionActionDetailDto? = null
)

@Serializable
data class CashSessionActionDetailDto(
    val id: String,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("opening_amount") val openingAmount: Double = 0.0,
    @SerialName("expected_closing_amount") val expectedClosingAmount: Double? = null,
    @SerialName("counted_closing_amount") val countedClosingAmount: Double? = null,
    val difference: Double? = null,
    val status: String? = null,
    val notes: String? = null
)

@Serializable
data class CashMovementActionResponseDto(
    val message: String = "",
    val movement: CashMovementDetailDto? = null
)

@Serializable
data class CashMovementDetailDto(
    val id: String,
    val type: String,
    val amount: Double = 0.0,
    val notes: String? = null,
    @SerialName("occurred_at") val occurredAt: String? = null
)

@Serializable
data class CashSessionOpenRequestDto(
    @SerialName("opening_amount") val openingAmount: String,
    val notes: String? = null,
    @SerialName("client_operation_uuid") val clientOperationUuid: String
)

@Serializable
data class CashSessionCloseRequestDto(
    @SerialName("counted_amount") val countedAmount: String,
    val notes: String? = null,
    @SerialName("client_operation_uuid") val clientOperationUuid: String
)

@Serializable
data class CashMovementRequestDto(
    val type: String,
    val amount: String,
    val notes: String,
    @SerialName("client_operation_uuid") val clientOperationUuid: String
)

@Serializable
data class ExpenseDto(
    val id: String,
    val description: String,
    val amount: Double,
    @SerialName("amount_paid") val amountPaid: Double,
    @SerialName("unpaid_amount") val unpaidAmount: Double,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("occurred_at") val occurredAt: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val reference: String? = null,
    val notes: String? = null,
    @SerialName("user_name") val userName: String? = null
)

@Serializable
data class ExpensePaginatedResponseDto(
    @SerialName("current_page") val currentPage: Int = 1,
    val data: List<ExpenseDto> = emptyList(),
    val total: Int = 0
)

@Serializable
data class ExpenseActionResponseDto(
    val message: String = "",
    val expense: ExpenseDto? = null
)

@Serializable
data class ExpenseCreateRequestDto(
    @SerialName("expense_category_id") val expenseCategoryId: Int? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val description: String,
    val amount: String,
    @SerialName("paid_amount") val paidAmount: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "cash",
    @SerialName("occurred_at") val occurredAt: String? = null,
    val reference: String? = null,
    val notes: String? = null,
    @SerialName("client_operation_uuid") val clientOperationUuid: String
)

@Serializable
data class ExpenseCategoryDto(
    val id: Int,
    val name: String,
    @SerialName("is_active") val isActive: Boolean = true,
    val description: String? = null
)

@Serializable
data class ExpensePaymentRequestDto(
    val amount: String,
    @SerialName("payment_method") val paymentMethod: String,
    val reference: String? = null,
    val notes: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("client_operation_uuid") val clientOperationUuid: String
)

@Serializable data class ExpenseBalanceDto(
    val id: String,
    val amount: Double,
    @SerialName("amount_paid") val amountPaid: Double,
    @SerialName("unpaid_amount") val unpaidAmount: Double,
    @SerialName("payment_status") val paymentStatus: String
)
@Serializable data class ExpensePaymentResponseDto(
    val expense: ExpenseBalanceDto,
    @SerialName("client_operation_uuid") val clientOperationUuid: String
)
