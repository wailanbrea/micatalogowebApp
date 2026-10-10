package com.example.bspos.data.micatalogo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * Some legacy feature endpoints returned row ids as JSON numbers while newer
 * responses return them as strings. The UI only needs a stable textual key,
 * so accept both representations at the API boundary.
 */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeString()
        val element: JsonElement = jsonDecoder.decodeJsonElement()
        return (element as? JsonPrimitive)?.content ?: element.toString()
    }
}

/**
 * Some older shop records were serialized by the API as business_hours: []
 * when no schedule had been configured. The current contract is an object
 * keyed by day, so treat the legacy empty array as an empty schedule instead
 * of failing the whole settings screen during deserialization.
 */
object ShopHoursMapSerializer : JsonTransformingSerializer<Map<String, ShopHoursDto>>(
    MapSerializer(String.serializer(), ShopHoursDto.serializer())
) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray && element.isEmpty()) JsonObject(emptyMap()) else element
}

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
data class FeatureResponseDto(
    @SerialName("feature_key") val featureKey: String = "",
    val feature: FeatureDefinitionDto = FeatureDefinitionDto(),
    val module: FeatureModuleDto = FeatureModuleDto()
)

@Serializable
data class SupportChatUserDto(
    val id: String = "",
    val name: String = "",
    val email: String = ""
)

@Serializable
data class SupportChatShopDto(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class SupportConversationDto(
    val id: String = "",
    val subject: String = "Ayuda con MiCatalogo",
    val status: String = "open",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    val shop: SupportChatShopDto? = null,
    val requester: SupportChatUserDto? = null,
    @SerialName("assigned_to") val assignedTo: SupportChatUserDto? = null,
    @SerialName("closed_by") val closedBy: SupportChatUserDto? = null
)

@Serializable
data class SupportMessageDto(
    val id: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    val sender: SupportChatUserDto? = null
)

@Serializable
data class SupportInboxResponseDto(
    @SerialName("owner_email") val ownerEmail: String = "",
    @SerialName("is_inbox") val isInbox: Boolean = false,
    val conversations: List<SupportConversationDto> = emptyList()
)

@Serializable
data class SupportConversationDetailDto(
    val conversation: SupportConversationDto = SupportConversationDto(),
    val messages: List<SupportMessageDto> = emptyList()
)

@Serializable
data class SupportChatCreateRequestDto(
    val subject: String? = null,
    val message: String
)

@Serializable
data class SupportChatMessageRequestDto(
    val message: String
)

@Serializable
data class SupportChatMessageResponseDto(
    val message: String = "",
    @SerialName("chat_message") val chatMessage: SupportMessageDto? = null
)

@Serializable
data class FeatureDefinitionDto(
    val group: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = ""
)

@Serializable
data class FeatureModuleDto(
    val kind: String = "prepared",
    val kpis: List<FeatureKpiDto> = emptyList(),
    val rows: List<FeatureRowDto> = emptyList(),
    @SerialName("page_total") val pageTotal: String? = null,
    val actions: List<FeatureActionDto> = emptyList(),
    val sections: List<FeatureSectionDto> = emptyList(),
    @SerialName("bottleSources") val bottleSources: List<FeatureBottleSourceDto> = emptyList(),
    @SerialName("quoteProducts") val quoteProducts: List<FeatureProductDto> = emptyList(),
    @SerialName("pendingRequests") val pendingRequests: List<AuthorizationRequestDto> = emptyList(),
    val note: String? = null
)

@Serializable
data class AuthorizationRequestDto(
    val id: String = "",
    val action: String = "",
    val context: Map<String, JsonElement> = emptyMap(),
    @SerialName("requester") val requester: String = "Vendedor",
    @SerialName("requester_email") val requesterEmail: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("approve_url") val approveUrl: String? = null,
    @SerialName("reject_url") val rejectUrl: String? = null,
    val status: String = "pending"
)

@Serializable
data class AuthorizationDecisionResponseDto(
    val message: String = "",
    val request: AuthorizationRequestDto? = null
)

@Serializable
data class FeatureBottleSourceDto(
    val name: String = "",
    @SerialName("volume_ml") val volumeMl: Int = 0,
    @SerialName("available_ml") val availableMl: Int? = null,
    @SerialName("decants_count") val decantsCount: Int = 0,
    val cost: Double? = null,
    val revenue: Double = 0.0,
    val difference: Double? = null,
    val percent: Double? = null,
    val covered: Boolean = false,
    val message: String = ""
)

@Serializable
data class FeatureSectionDto(
    val key: String = "",
    val label: String = "",
    val kpis: List<FeatureKpiDto> = emptyList(),
    val rows: List<FeatureRowDto> = emptyList(),
    val note: String? = null
)

@Serializable
data class FeatureProductDto(
    val id: String = "",
    val name: String = "",
    val price: String = "0.00",
    val category: String = "",
    val brand: String = "",
    val code: String = "",
    val stock: Int? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("sale_unit_label") val saleUnitLabel: String = "Unidad"
)

@Serializable
data class QuoteItemRequestDto(
    @SerialName("product_id") val productId: String,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: String? = null
)

@Serializable
data class QuoteCreateRequestDto(
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    val notes: String? = null,
    val items: List<QuoteItemRequestDto>
)

@Serializable
data class QuoteResponseDto(
    val message: String = "",
    val quote: QuoteCreatedDto? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("invoice_url") val invoiceUrl: String? = null
)

@Serializable
data class OrderConfirmRequestDto(
    @SerialName("payment_kind") val paymentKind: String = "paid",
    @SerialName("payment_method") val paymentMethod: String = "cash",
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("credit_amount") val creditAmount: String? = null,
    val reference: String? = null
)

@Serializable
data class OrderConfirmResponseDto(
    val message: String = "",
    @SerialName("order_number") val orderNumber: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null
)

@Serializable
data class QuoteCreatedDto(
    val id: String = "",
    @SerialName("quote_number") val quoteNumber: String = "",
    val status: String = "",
    val total: String = "0.00",
    @SerialName("items_count") val itemsCount: Int = 0
)

@Serializable
data class FeatureKpiDto(
    val label: String = "",
    val value: String = "",
    val tone: String = "blue"
)

@Serializable
data class FeatureRowDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String? = null,
    val primary: String = "",
    val secondary: String = "",
    val value: String = "",
    val status: String = "",
    val filterable: Boolean = false,
    @SerialName("can_convert") val canConvert: Boolean = false,
    @SerialName("can_confirm") val canConfirm: Boolean = false,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("margin_percent") val marginPercent: String? = null,
    @SerialName("round_step") val roundStep: String? = null,
    @SerialName("auto_increase") val autoIncrease: Boolean = false,
    @SerialName("pending_price") val pendingPrice: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("delivery_type") val deliveryType: String? = null,
    val origin: String? = null,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val items: List<FeatureOrderItemDto> = emptyList()
)

@Serializable
data class FeatureOrderItemDto(
    val name: String = "",
    val quantity: Int = 0,
    @SerialName("unit_price") val unitPrice: String = "",
    @SerialName("line_total") val lineTotal: String = ""
)

@Serializable
data class PricingRuleRequestDto(
    @SerialName("margin_percent") val marginPercent: String,
    @SerialName("round_step") val roundStep: String,
    @SerialName("auto_increase") val autoIncrease: Boolean
)

@Serializable
data class PricingApprovalRequestDto(
    @SerialName("expected_price") val expectedPrice: String
)

@Serializable
data class AttributeUpdateRequestDto(
    val name: String,
    val filterable: Boolean,
    val required: Boolean,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class PricingMutationResponseDto(
    val message: String = ""
)

@Serializable
data class PartnerCreateRequestDto(
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("ownership_percent") val ownershipPercent: String = "0"
)

@Serializable
data class PartnerTransactionRequestDto(
    val type: String,
    val amount: String,
    val notes: String? = null
)

@Serializable
data class AccountantAccessRequestDto(
    val email: String
)

@Serializable
data class ShopHoursDto(
    val open: String? = "09:00",
    val close: String? = "18:00",
    @SerialName("all_day") val allDay: Boolean = false,
    val closed: Boolean = false
)

@Serializable
data class ShopSettingsDto(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    @SerialName("business_type") val businessType: String = "general_retail",
    @SerialName("business_type_label") val businessTypeLabel: String = "Negocio independiente",
    val description: String = "",
    @SerialName("logo_url") val logoUrl: String? = null,
    val address: String = "",
    @SerialName("maps_url") val mapsUrl: String = "",
    val instagram: String = "",
    @SerialName("whatsapp_country_code") val whatsappCountryCode: String = "",
    @SerialName("whatsapp_number") val whatsappNumber: String = "",
    @SerialName("offers_shipping") val offersShipping: Boolean = false,
    @SerialName("primary_color") val primaryColor: String = "#1d4ed8",
    @SerialName("secondary_color") val secondaryColor: String = "#0f172a",
    @SerialName("business_hours")
    @kotlinx.serialization.Serializable(with = ShopHoursMapSerializer::class)
    val businessHours: Map<String, ShopHoursDto> = emptyMap(),
    val google: GoogleSettingsDto = GoogleSettingsDto(),
    @SerialName("business_types") val businessTypes: List<ShopTypeOptionDto> = emptyList(),
    @SerialName("operational_settings") val operationalSettings: OperationalSettingsDto = OperationalSettingsDto(),
    @SerialName("available_payment_methods") val availablePaymentMethods: List<PaymentMethodOptionDto> = emptyList(),
    @SerialName("payment_accounts") val paymentAccounts: List<PaymentAccountDto> = emptyList()
)

@Serializable
data class GoogleSettingsDto(
    val score: Int = 0,
    @SerialName("target_score") val targetScore: Int = 90,
    @SerialName("pending_count") val pendingCount: Int = 0,
    val checks: List<GoogleChecklistItemDto> = emptyList()
)

@Serializable
data class GoogleChecklistItemDto(
    val key: String = "",
    val label: String = "",
    val description: String = "",
    val done: Boolean = false,
    val count: Int? = null,
    val section: String = "Google"
)

@Serializable
data class ShopTypeOptionDto(
    val key: String = "",
    val label: String = ""
)

@Serializable
data class ReceiptSettingsDto(
    @SerialName("show_logo") val showLogo: Boolean = true,
    @SerialName("show_customer") val showCustomer: Boolean = true,
    @SerialName("show_seller") val showSeller: Boolean = true,
    @SerialName("show_notes") val showNotes: Boolean = true
)

@Serializable
data class FiscalSettingsDto(
    val enabled: Boolean = false,
    @SerialName("invoice_type") val invoiceType: String = "consumer"
)

@Serializable
data class CreditSettingsDto(
    val enabled: Boolean = true,
    @SerialName("default_days") val defaultDays: Int = 30,
    @SerialName("allow_partial_payments") val allowPartialPayments: Boolean = true
)

@Serializable
data class ToggleSettingDto(
    val enabled: Boolean = false
)

@Serializable
data class WholesaleSettingsDto(
    val enabled: Boolean = false,
    @SerialName("minimum_quantity") val minimumQuantity: Int = 6
)

@Serializable
data class PurchaseSettingsDto(
    @SerialName("allow_partial_receive") val allowPartialReceive: Boolean = true,
    @SerialName("require_supplier") val requireSupplier: Boolean = false
)

@Serializable
data class ShippingSettingsDto(
    val enabled: Boolean = false,
    val types: List<String> = listOf("pickup", "delivery")
)

@Serializable
data class DecantSettingsDto(
    val enabled: Boolean = false,
    @SerialName("default_ml") val defaultMl: List<Int> = listOf(5, 10, 30),
    @SerialName("as_cover") val asCover: Boolean = false,
    @SerialName("section_text") val sectionText: String? = null
)

@Serializable
data class CatalogSettingsDto(
    val sort: String = "name_asc",
    @SerialName("offers_first") val offersFirst: Boolean = true,
    @SerialName("hide_out_of_stock") val hideOutOfStock: Boolean = false,
    @SerialName("show_stock") val showStock: Boolean = false,
    @SerialName("allow_backorder") val allowBackorder: Boolean = false
)

@Serializable
data class MarketingSettingsDto(
    @SerialName("meta_pixel") val metaPixel: String? = null,
    @SerialName("tiktok_pixel") val tiktokPixel: String? = null,
    val ga4: String? = null,
    @SerialName("google_site_verification") val googleSiteVerification: String? = null
)

@Serializable
data class ImageSettingsDto(
    @SerialName("max_per_product") val maxPerProduct: Int = 3,
    @SerialName("auto_optimize") val autoOptimize: Boolean = true
)

@Serializable
data class OperationalSettingsDto(
    val currency: String = "DOP",
    @SerialName("currency_symbol") val currencySymbol: String = "RD$",
    val timezone: String = "America/Santo_Domingo",
    @SerialName("tax_rate") val taxRate: Double? = null,
    @SerialName("business_rnc") val businessRnc: String? = null,
    @SerialName("employee_count") val employeeCount: Int? = null,
    @SerialName("payment_methods") val paymentMethods: List<String> = listOf("cash", "bank_transfer", "card"),
    val receipt: ReceiptSettingsDto = ReceiptSettingsDto(),
    val fiscal: FiscalSettingsDto = FiscalSettingsDto(),
    val credit: CreditSettingsDto = CreditSettingsDto(),
    val orders: ToggleSettingDto = ToggleSettingDto(true),
    @SerialName("quick_service") val quickService: ToggleSettingDto = ToggleSettingDto(true),
    val recipes: ToggleSettingDto = ToggleSettingDto(),
    val wholesale: WholesaleSettingsDto = WholesaleSettingsDto(),
    val purchases: PurchaseSettingsDto = PurchaseSettingsDto(),
    val shipping: ShippingSettingsDto = ShippingSettingsDto(),
    val decants: DecantSettingsDto = DecantSettingsDto(),
    val images: ImageSettingsDto = ImageSettingsDto(),
    val catalog: CatalogSettingsDto = CatalogSettingsDto(),
    val marketing: MarketingSettingsDto = MarketingSettingsDto()
)

@Serializable
data class PaymentMethodOptionDto(
    val key: String = "",
    val label: String = ""
)

@Serializable
data class PaymentAccountDto(
    val id: String = "",
    val name: String = "",
    @SerialName("bank_name") val bankName: String? = null,
    @SerialName("account_number") val accountNumber: String? = null,
    @SerialName("account_holder") val accountHolder: String? = null,
    val instructions: String? = null
)

@Serializable
data class ShopSettingsUpdateDto(
    val name: String,
    @SerialName("business_type") val businessType: String,
    val description: String,
    val address: String,
    @SerialName("maps_url") val mapsUrl: String? = null,
    val instagram: String? = null,
    @SerialName("whatsapp_country_code") val whatsappCountryCode: String,
    @SerialName("whatsapp_number") val whatsappNumber: String,
    @SerialName("offers_shipping") val offersShipping: Boolean,
    @SerialName("primary_color") val primaryColor: String,
    @SerialName("secondary_color") val secondaryColor: String,
    @SerialName("business_hours") val businessHours: Map<String, ShopHoursDto>,
    @SerialName("operational_settings") val operationalSettings: OperationalSettingsDto = OperationalSettingsDto()
)

@Serializable
data class ShopLogoUploadResponseDto(
    val message: String = "",
    @SerialName("logo_url") val logoUrl: String? = null
)

@Serializable
data class FeatureActionDto(
    val label: String = "",
    val url: String = "",
    val tone: String = "secondary"
)

@Serializable
data class PurchaseWorkspaceDto(
    val documents: List<PurchaseDocumentDto> = emptyList(),
    val suppliers: List<PurchaseSupplierDto> = emptyList(),
    val products: List<PurchaseProductDto> = emptyList()
)

@Serializable
data class PurchaseInvoicePreviewDto(
    @SerialName("upload_token") val uploadToken: String = "",
    val file: PurchasePreviewFileDto = PurchasePreviewFileDto(),
    val rows: List<PurchaseInvoicePreviewRowDto> = emptyList(),
    val warnings: List<String> = emptyList(),
    val counts: PurchasePreviewCountsDto = PurchasePreviewCountsDto()
)

@Serializable
data class PurchasePreviewFileDto(
    val name: String = "",
    val type: String = ""
)

@Serializable
data class PurchasePreviewCountsDto(
    val total: Int = 0,
    val valid: Int = 0,
    @SerialName("needs_review") val needsReview: Int = 0,
    val matched: Int = 0
)

@Serializable
data class PurchaseInvoicePreviewRowDto(
    val line: Int = 0,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String = "",
    val quantity: Int = 0,
    @SerialName("unit_cost") val unitCost: String? = null,
    @SerialName("match_label") val matchLabel: String = "",
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val valid: Boolean = false
)

@Serializable
data class PurchaseSupplierDto(
    val id: String = "",
    val name: String = "",
    @SerialName("invoice_currency") val invoiceCurrency: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null
)

@Serializable
data class SupplierCreateRequestDto(
    val name: String,
    @SerialName("invoice_currency") val invoiceCurrency: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null
)

@Serializable
data class SupplierActionResponseDto(
    val message: String = "",
    val supplier: PurchaseSupplierDto? = null
)

@Serializable
data class PurchaseProductDto(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val cost: String = "0.00"
)

@Serializable
data class PurchaseDocumentDto(
    val id: String = "",
    @SerialName("document_number") val documentNumber: String = "",
    @SerialName("invoice_date") val invoiceDate: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    val type: String = "container",
    val status: String = "draft",
    val currency: String = "DOP",
    @SerialName("exchange_rate") val exchangeRate: String? = null,
    val carrier: String? = null,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("expected_at") val expectedAt: String? = null,
    @SerialName("shipping_pounds") val shippingPounds: String? = null,
    @SerialName("freight_amount") val freightAmount: String? = null,
    @SerialName("customs_amount") val customsAmount: String? = null,
    @SerialName("payment_status") val paymentStatus: String = "pending",
    @SerialName("parent_document_id") val parentDocumentId: String? = null,
    val subtotal: String = "0.00",
    val total: String = "0.00",
    val notes: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    val supplier: PurchaseSupplierDto? = null,
    val items: List<PurchaseItemDto> = emptyList()
)

@Serializable
data class PurchaseItemDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("product_name") val productName: String = "",
    val quantity: Int = 0,
    @SerialName("unit_cost") val unitCost: String = "0.00",
    @SerialName("line_total") val lineTotal: String = "0.00",
    val received: Boolean = false
)

@Serializable
data class PurchaseItemRequestDto(
    @SerialName("product_id") val productId: String,
    val quantity: Int,
    @SerialName("unit_cost") val unitCost: String
)

@Serializable
data class PurchaseCreateRequestDto(
    val type: String,
    @SerialName("document_number") val documentNumber: String,
    @SerialName("invoice_date") val invoiceDate: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    val amount: String? = null,
    @SerialName("supplier_id") val supplierId: String? = null,
    val currency: String = "DOP",
    @SerialName("exchange_rate") val exchangeRate: String? = null,
    val carrier: String? = null,
    @SerialName("tracking_number") val trackingNumber: String? = null,
    @SerialName("expected_at") val expectedAt: String? = null,
    @SerialName("shipping_pounds") val shippingPounds: String? = null,
    @SerialName("freight_amount") val freightAmount: String? = null,
    @SerialName("customs_amount") val customsAmount: String? = null,
    @SerialName("payment_status") val paymentStatus: String = "pending",
    @SerialName("parent_document_id") val parentDocumentId: String? = null,
    val mode: String = "draft",
    val notes: String? = null,
    val items: List<PurchaseItemRequestDto> = emptyList()
)

@Serializable
data class PurchaseActionResponseDto(
    val message: String = "",
    val document: PurchaseDocumentDto? = null
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
    @SerialName("business_type") val businessType: String? = null,
    @SerialName("business_type_label") val businessTypeLabel: String? = null,
    val capabilities: Map<String, String> = emptyMap(),
    @SerialName("product_fields") val productFields: List<String> = emptyList(),
    val presentation: BusinessPresentationDto = BusinessPresentationDto(),
    val quota: ShopQuotaDto? = null,
    @SerialName("menu_permissions") val menuPermissions: List<String> = emptyList(),
    @SerialName("enabled_menu_keys") val enabledMenuKeys: List<String>? = null,
    @SerialName("can_manage_menu_visibility") val canManageMenuVisibility: Boolean = false,
    @SerialName("menu_options") val menuOptions: List<ShopMenuOptionDto> = emptyList(),
    @SerialName("can_manage_sellers") val canManageSellers: Boolean = false,
    val sellers: List<ShopSellerDto> = emptyList()
)

@Serializable
data class ShopMenuOptionDto(
    val key: String = "",
    val label: String = "",
    val group: String = "Ajustes",
    @SerialName("protected") val protected: Boolean = false
)

@Serializable
data class BusinessPresentationDto(
    val archetype: String = "general_retail",
    val terminology: Map<String, String> = emptyMap(),
    val dashboard: BusinessDashboardPresentationDto = BusinessDashboardPresentationDto(),
    val pos: BusinessPosPresentationDto = BusinessPosPresentationDto(),
    val catalog: BusinessCatalogPresentationDto = BusinessCatalogPresentationDto(),
    val inventory: BusinessInventoryPresentationDto = BusinessInventoryPresentationDto(),
    val customers: BusinessCustomersPresentationDto = BusinessCustomersPresentationDto()
)

@Serializable
data class BusinessDashboardPresentationDto(
    val widgets: List<String> = emptyList(),
    @SerialName("quick_actions") val quickActions: List<String> = emptyList(),
    val title: String = "Resumen de tu negocio"
)

@Serializable
data class BusinessPosPresentationDto(
    @SerialName("search_placeholder") val searchPlaceholder: String = "Buscar producto o código",
    @SerialName("show_wholesale") val showWholesale: Boolean = false,
    @SerialName("show_credit") val showCredit: Boolean = false,
    @SerialName("show_inventory") val showInventory: Boolean = true
)

@Serializable
data class BusinessCatalogPresentationDto(
    @SerialName("search_placeholder") val searchPlaceholder: String = "Buscar productos",
    @SerialName("empty_message") val emptyMessage: String = "Crea tu primer producto para empezar",
    @SerialName("show_stock") val showStock: Boolean = true
)

@Serializable
data class BusinessInventoryPresentationDto(
    val title: String = "Existencias y movimientos",
    val enabled: Boolean = true
)

@Serializable
data class BusinessCustomersPresentationDto(
    @SerialName("show_credit") val showCredit: Boolean = false
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
data class MenuVisibilityUpdateDto(
    @SerialName("enabled_menu_keys") val enabledMenuKeys: List<String>
)

@Serializable
data class MenuVisibilityResponseDto(
    val message: String = "",
    @SerialName("enabled_menu_keys") val enabledMenuKeys: List<String> = emptyList(),
    @SerialName("menu_permissions") val menuPermissions: List<String> = emptyList()
)

@Serializable
data class SellerCreateRequestDto(
    val email: String,
    @SerialName("commission_type") val commissionType: String,
    @SerialName("commission_value") val commissionValue: String,
    @SerialName("menu_permissions") val menuPermissions: List<String> = listOf("sales", "products", "printers")
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
    @SerialName("invalid_rows") val invalidRows: Int = 0,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("header_row") val headerRow: Int = 1,
    val sheet: ImportSheetDto? = null,
    val sheets: List<ImportSheetDto> = emptyList(),
    @SerialName("needs_header_selection") val needsHeaderSelection: Boolean = false,
    @SerialName("original_headers") val originalHeaders: List<String> = emptyList(),
    @SerialName("mapping_confidence") val mappingConfidence: Map<String, ImportMappingDetailDto> = emptyMap(),
    @SerialName("ignored_columns") val ignoredColumns: List<ImportMappingDetailDto> = emptyList(),
    val warnings: List<String> = emptyList(),
    @SerialName("new_rows_count") val newRows: Int = 0,
    @SerialName("existing_rows_count") val existingRows: Int = 0,
    @SerialName("duplicate_rows_count") val duplicateRows: Int = 0
)

@Serializable
data class ImportSheetDto(
    val name: String = "",
    val index: Int = 0,
    @SerialName("header_row") val headerRow: Int = 1,
    @SerialName("data_rows") val dataRows: Int = 0
)

@Serializable
data class ImportMappingDetailDto(
    @SerialName("source_column") val source: String = "",
    @SerialName("original_header") val header: String = "",
    val confidence: Double = 0.0,
    val reason: String = "",
    val examples: List<String> = emptyList()
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
    val price: String? = null,
    @SerialName("cost_price") val costPrice: String? = null,
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
    val rows: List<InventoryImportRowDto> = emptyList(),
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("duplicate_strategy") val duplicateStrategy: String = "skip",
    @SerialName("create_missing_categories") val createMissingCategories: Boolean = false
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
    val slug: String? = null,
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
    @SerialName("is_combo") val isCombo: Boolean = false,
    @SerialName("combo_items") val comboItems: List<RemoteComboItemDto> = emptyList(),
    @SerialName("volume_ml") val volumeMl: Int? = null,
    @SerialName("availability_status") val availabilityStatus: String,
    @SerialName("moderation_status") val moderationStatus: String,
    val inventory: RemoteInventoryDto,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class RemoteComboItemDto(
    @SerialName("product_id") val productId: String,
    val name: String? = null,
    val quantity: Int = 1,
)

@Serializable
data class RemoteInventoryDto(
    @SerialName("track_inventory") val trackInventory: Boolean,
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("available_ml") val availableMl: Int? = null,
    @SerialName("reserved_decant_ml") val reservedDecantMl: Int? = null,
    @SerialName("opened_bottles") val openedBottles: Int? = null,
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
data class BottleRecoveryDto(
    @SerialName("source_product_id") val sourceProductId: String? = null,
    @SerialName("source_product_name") val sourceProductName: String? = null,
    val cost: Double = 0.0,
    val revenue: Double = 0.0,
    val difference: Double = 0.0,
    val percent: Double = 0.0,
    val covered: Boolean = false,
    @SerialName("just_covered") val justCovered: Boolean = false,
    val alert: String? = null
)

@Serializable
data class PosSaleUploadResponseDto(
    @SerialName("client_sale_uuid") val clientSaleUuid: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    val status: String? = null,
    val total: String? = null,
    @SerialName("bottle_recovery") val bottleRecovery: List<BottleRecoveryDto> = emptyList()
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
    @SerialName("gross_sales") val grossSales: Double = 0.0,
    val discounts: Double = 0.0,
    @SerialName("line_discounts") val lineDiscounts: Double = 0.0,
    @SerialName("general_discounts") val generalDiscounts: Double = 0.0,
    @SerialName("tax_collected") val taxCollected: Double = 0.0,
    val returns: Double = 0.0,
    @SerialName("net_sales") val netSales: Double = 0.0,
    @SerialName("fifo_cogs") val fifoCogs: Double? = null,
    @SerialName("units_sold") val unitsSold: Int = 0,
    @SerialName("cost_coverage_percent") val costCoveragePercent: Double = 100.0,
    @SerialName("revenue_cost_coverage") val revenueCostCoverage: Double = 100.0,
    @SerialName("is_cost_coverage_partial") val isCostCoveragePartial: Boolean = false,
    @SerialName("gross_profit") val grossProfit: Double? = null,
    @SerialName("gross_margin_percent") val grossMarginPercent: Double? = null,
    @SerialName("operating_expenses") val operatingExpenses: Double = 0.0,
    @SerialName("commissions_generated") val commissionsGenerated: Double = 0.0,
    @SerialName("operating_profit") val operatingProfit: Double? = null,
    @SerialName("operating_margin_percent") val operatingMarginPercent: Double? = null,
    @SerialName("sales_count") val salesCount: Int = 0,
    @SerialName("average_ticket") val averageTicket: Double = 0.0,
    @SerialName("collected_in_period") val collectedInPeriod: Double = 0.0,
    @SerialName("credit_generated") val creditGenerated: Double = 0.0
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
    @SerialName("receivable_total") val receivableTotal: Double = 0.0,
    val aging: FinanceAgingDto,
    @SerialName("inventory_cost_value") val inventoryCostValue: Double = 0.0,
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
    @SerialName("days_0_30") val days0To30: Double = 0.0,
    @SerialName("days_31_60") val days31To60: Double = 0.0,
    @SerialName("days_61_90") val days61To90: Double = 0.0,
    @SerialName("days_over_90") val daysOver90: Double = 0.0,
    @SerialName("overdue_count") val overdueCount: Int = 0,
    @SerialName("invoices_total") val invoicesTotal: Double = 0.0,
    @SerialName("unallocated_receivables") val unallocatedReceivables: Double = 0.0,
    @SerialName("total_receivable") val totalReceivable: Double = 0.0,
    @SerialName("reconciliation_difference") val reconciliationDifference: Double = 0.0,
    @SerialName("invoice_details") val invoiceDetails: List<FinanceInvoiceAgingDto> = emptyList()
)

@Serializable
data class FinanceInvoiceAgingDto(
    @SerialName("invoice_number") val invoiceNumber: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("reference_date") val referenceDate: String,
    @SerialName("overdue_days") val overdueDays: Int = 0,
    @SerialName("outstanding_amount") val outstandingAmount: Double = 0.0,
    @SerialName("aging_bucket") val agingBucket: String
)

@Serializable
data class FinanceIncomeStatementDto(
    @SerialName("net_sales") val netSales: Double = 0.0,
    @SerialName("fifo_cogs") val fifoCogs: Double? = null,
    @SerialName("gross_profit") val grossProfit: Double? = null,
    @SerialName("gross_margin_percent") val grossMarginPercent: Double? = null,
    @SerialName("operating_expenses_total") val operatingExpensesTotal: Double = 0.0,
    val commissions: Double = 0.0,
    @SerialName("operating_profit") val operatingProfit: Double? = null,
    @SerialName("operating_margin_percent") val operatingMarginPercent: Double? = null
)

@Serializable
data class FinanceCashFlowDto(
    val inflows: FinanceCashFlowInflowsDto,
    val outflows: FinanceCashFlowOutflowsDto,
    @SerialName("net_cash_flow") val netCashFlow: Double = 0.0
)

@Serializable
data class FinanceCashFlowInflowsDto(
    @SerialName("sales_cash") val salesCash: Double = 0.0,
    @SerialName("sales_card") val salesCard: Double = 0.0,
    @SerialName("sales_transfer") val salesTransfer: Double = 0.0,
    @SerialName("sales_other") val salesOther: Double = 0.0,
    @SerialName("debt_collections") val debtCollections: Double = 0.0,
    @SerialName("other_inflows") val otherInflows: Double = 0.0,
    val total: Double = 0.0
)

@Serializable
data class FinanceCashFlowOutflowsDto(
    @SerialName("expenses_paid") val expensesPaid: Double = 0.0,
    @SerialName("cash_out") val cashOut: Double = 0.0,
    val total: Double = 0.0
)

@Serializable
data class DailyCloseDto(
    @SerialName("business_date") val businessDate: String,
    @SerialName("sales_cash") val salesCash: Double = 0.0,
    @SerialName("debt_collections_cash") val debtCollectionsCash: Double = 0.0,
    @SerialName("other_inflows_cash") val otherInflowsCash: Double = 0.0,
    @SerialName("expenses_cash") val expensesCash: Double = 0.0,
    @SerialName("cash_out") val cashOut: Double = 0.0,
    @SerialName("expected_cash") val expectedCash: Double = 0.0,
    @SerialName("sales_card") val salesCard: Double = 0.0,
    @SerialName("sales_transfer") val salesTransfer: Double = 0.0,
    @SerialName("sales_other") val salesOther: Double = 0.0,
    @SerialName("debt_collections_total") val debtCollectionsTotal: Double = 0.0,
    @SerialName("expenses_paid_total") val expensesPaidTotal: Double = 0.0,
    val closure: DailyClosureRecordDto? = null
)

@Serializable
data class DailyClosureRecordDto(
    val id: String,
    @SerialName("business_date") val businessDate: String,
    @SerialName("expected_cash") val expectedCash: Double = 0.0,
    @SerialName("counted_cash") val countedCash: Double? = null,
    val difference: Double? = null,
    val status: String = "closed",
    @SerialName("closed_at") val closedAt: String? = null,
    val notes: String? = null
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
    @SerialName("has_open_session") val hasOpenSession: Boolean = false
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
    @SerialName("opening_amount") val openingAmount: Double = 0.0,
    @SerialName("sales_cash") val salesCash: Double = 0.0,
    @SerialName("cash_in") val cashIn: Double = 0.0,
    @SerialName("cash_out") val cashOut: Double = 0.0,
    @SerialName("owner_contributions") val ownerContribution: Double = 0.0,
    @SerialName("owner_withdrawals") val ownerWithdrawal: Double = 0.0,
    @SerialName("debt_collections_cash") val debtCollectionsCash: Double = 0.0,
    @SerialName("expenses_cash") val expensesCash: Double = 0.0,
    @SerialName("supplier_payments") val supplierPayments: Double = 0.0,
    val adjustments: Double = 0.0,
    @SerialName("total_in") val totalIn: Double = 0.0,
    @SerialName("total_out") val totalOut: Double = 0.0,
    @SerialName("expected_closing_amount") val expectedClosingAmount: Double,
    @SerialName("counted_amount") val countedAmount: Double? = null,
    val difference: Double? = null,
    val status: String = "",
    @SerialName("movements_count") val movementsCount: Int = 0
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
    @SerialName("counted_amount") val countedAmount: String? = null,
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
data class DailyCloseRequestDto(
    val date: String,
    @SerialName("counted_cash") val countedCash: String? = null,
    val notes: String? = null
)

@Serializable
data class ExpenseDto(
    val id: String,
    val description: String,
    val amount: Double = 0.0,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
    @SerialName("unpaid_amount") val unpaidAmount: Double = 0.0,
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
