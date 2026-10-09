package com.example.bspos.domain.model

data class MiCatalogoShop(
    val id: String,
    val name: String,
    val slug: String?,
    val businessType: String? = null,
    val businessTypeLabel: String? = null,
    val capabilities: Map<String, String> = emptyMap(),
    val productFields: List<String> = emptyList(),
    val presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    val quota: MiCatalogoShopQuota? = null,
    val menuPermissions: List<String> = emptyList(),
    val enabledMenuKeys: List<String>? = null,
    val canManageMenuVisibility: Boolean = false,
    val menuOptions: List<ShopMenuOption> = emptyList(),
    val canManageSellers: Boolean = false,
    val sellers: List<MiCatalogoSeller> = emptyList()
)

data class MiCatalogoBusinessPresentation(
    val archetype: String = "general_retail",
    val terminology: Map<String, String> = emptyMap(),
    val dashboardWidgets: List<String> = emptyList(),
    val dashboardQuickActions: List<String> = emptyList(),
    val dashboardTitle: String = "Resumen de tu negocio",
    val posSearchPlaceholder: String = "Buscar producto o código",
    val posShowWholesale: Boolean = false,
    val posShowCredit: Boolean = false,
    val posShowInventory: Boolean = true,
    val catalogSearchPlaceholder: String = "Buscar productos",
    val catalogEmptyMessage: String = "Crea tu primer producto para empezar",
    val catalogShowStock: Boolean = true,
    val inventoryTitle: String = "Existencias y movimientos",
    val inventoryEnabled: Boolean = true,
    val customersShowCredit: Boolean = false
) {
    fun term(key: String, fallback: String): String = terminology[key].orEmpty().ifBlank { fallback }
}

data class MiCatalogoManagedShop(
    val id: String,
    val name: String,
    val slug: String,
    val ownerName: String,
    val ownerEmail: String,
    val whatsappCountryCode: String,
    val whatsappNumber: String,
    val status: String,
    val productCount: Int
)

data class MiCatalogoSeller(
    val id: String,
    val userId: String,
    val name: String,
    val email: String,
    val isActive: Boolean,
    val menuPermissions: List<String>
)

data class SellerMenuOption(val key: String, val label: String)

data class ShopMenuOption(
    val key: String,
    val label: String,
    val group: String,
    val protected: Boolean
)

val SellerMenuOptions = listOf(
    SellerMenuOption("sales", "Ventas"),
    SellerMenuOption("products", "Productos"),
    SellerMenuOption("printers", "Impresoras"),
    SellerMenuOption("quotes", "Cotizaciones"),
    SellerMenuOption("orders", "Pedidos"),
    SellerMenuOption("encargos", "Encargos"),
    SellerMenuOption("shipments", "Envíos"),
    SellerMenuOption("day_close", "Cierre de día"),
    SellerMenuOption("containers", "Contenedores"),
    SellerMenuOption("loads", "Cargas"),
    SellerMenuOption("suppliers", "Suplidores"),
    SellerMenuOption("purchase_invoices", "Facturas"),
    SellerMenuOption("photos", "Fotos"),
    SellerMenuOption("storefront", "Mi tienda"),
    SellerMenuOption("services", "Servicios"),
    SellerMenuOption("price_health", "Salud de precios"),
    SellerMenuOption("pricing", "Precios automáticos"),
    SellerMenuOption("decants", "Decants"),
    SellerMenuOption("attributes", "Marcas y atributos"),
    SellerMenuOption("import", "Importar"),
    SellerMenuOption("customers", "Clientes"),
    SellerMenuOption("credit", "Crédito"),
    SellerMenuOption("inventory", "Inventario"),
    SellerMenuOption("collections", "Cobros"),
    SellerMenuOption("cash", "Caja"),
    SellerMenuOption("finance", "Ganancias"),
    SellerMenuOption("inventory_adjustments", "Ajustes de inventario"),
    SellerMenuOption("partners", "Socios"),
    SellerMenuOption("expenses", "Gastos"),
    SellerMenuOption("reports", "Reportes"),
    SellerMenuOption("commissions", "Comisiones"),
    SellerMenuOption("authorizations", "Autorizaciones"),
    SellerMenuOption("returns", "Devoluciones"),
    SellerMenuOption("routes", "Rutas"),
    // Legacy permission key kept for existing seller profiles; the duplicate
    // shortcut is no longer rendered because these destinations live in the drawer.
    SellerMenuOption("more", "Más herramientas"),
    SellerMenuOption("accountant", "Contador"),
    SellerMenuOption("account", "Mi cuenta"),
    SellerMenuOption("updates", "Novedades"),
    SellerMenuOption("help", "Ayuda"),
    SellerMenuOption("practice", "Practicar sin miedo"),
    SellerMenuOption("support", "Soporte"),
    SellerMenuOption("metrics", "Métricas y QR"),
    SellerMenuOption("public_catalog", "Compartir catálogo")
)

private val ownerOnlyMenuKeys = setOf("settings", "shop_settings", "sellers")

fun canAccessMiCatalogoMenu(
    isPlatformOwner: Boolean,
    canManageShop: Boolean,
    menuPermissions: Collection<String>,
    menu: String,
    capabilities: Map<String, String> = emptyMap(),
    enabledMenuKeys: Collection<String>? = null
): Boolean {
    val requiredCapability = when (menu) {
        "products" -> "products"
        "inventory" -> "inventory"
        "sales" -> "sales"
        "customers" -> "customers"
        "collections", "credit" -> "credit"
        "cash" -> "cash"
        "expenses" -> "expenses"
        "finance" -> "finance"
        "decants" -> "decants"
        else -> null
    }
    if (requiredCapability != null && capabilities.isNotEmpty() && capabilities[requiredCapability] != "enabled") return false
    val protectedForOwner = menu in ownerOnlyMenuKeys && (isPlatformOwner || canManageShop)
    if (!protectedForOwner && enabledMenuKeys != null && menu !in enabledMenuKeys) return false
    return protectedForOwner || isPlatformOwner || canManageShop || (
    menu !in ownerOnlyMenuKeys && menu in menuPermissions
    )
}

data class MiCatalogoShopQuota(
    val plan: String,
    val planLabel: String,
    val productCount: Int,
    val productLimit: Int,
    val productsRemaining: Int,
    val imageLimit: Int,
    val canAddProducts: Boolean,
    val userCount: Int = 0,
    val userLimit: Int = 0,
    val usersRemaining: Int = 0,
    val sellerCount: Int = 0,
    val sellerLimit: Int = 0,
    val sellersRemaining: Int = 0,
    val canAddUsers: Boolean = false,
    val canAddSellers: Boolean = false,
    val additionalSeatPriceUsd: Double = 5.0,
    val features: List<String> = emptyList()
)

data class MiCatalogoInventoryImportPreview(
    val quota: MiCatalogoShopQuota,
    val headers: List<String> = emptyList(),
    val mapping: Map<String, String> = emptyMap(),
    val fields: Map<String, String> = emptyMap(),
    val rows: List<MiCatalogoInventoryImportRow>,
    val validRows: Int,
    val invalidRows: Int,
    val sessionId: String? = null,
    val headerRow: Int = 1,
    val sheetIndex: Int = 0,
    val sheetName: String = "",
    val sheets: List<ImportSheet> = emptyList(),
    val needsHeaderSelection: Boolean = false,
    val originalHeaders: List<String> = emptyList(),
    val mappingDetails: Map<String, ImportMappingDetail> = emptyMap(),
    val ignoredColumns: List<ImportMappingDetail> = emptyList(),
    val warnings: List<String> = emptyList(),
    val newRows: Int = 0,
    val existingRows: Int = 0,
    val duplicateRows: Int = 0
)

data class ImportSheet(val name: String, val index: Int, val headerRow: Int, val dataRows: Int)
data class ImportMappingDetail(val source: String, val header: String, val confidence: Double, val reason: String, val examples: List<String>)

data class MiCatalogoInventoryImportRow(
    val line: Int,
    val name: String,
    val productCode: String?,
    val barcode: String?,
    val brand: String?,
    val category: String?,
    val description: String?,
    val notes: String?,
    val price: String?,
    val costPrice: String?,
    val stock: Int?,
    val attributes: List<MiCatalogoInventoryImportAttribute>,
    val errors: List<String>,
    val valid: Boolean
)

data class MiCatalogoInventoryImportAttribute(val name: String, val value: String)

data class MiCatalogoInventoryImportResult(
    val message: String,
    val imported: Int,
    val quota: MiCatalogoShopQuota?
)

data class MiCatalogoCatalogSyncResult(
    val shopName: String,
    val categoriesApplied: Int,
    val unitsApplied: Int,
    val productsApplied: Int,
    val inventoryMovementsRecorded: Int,
    val imagesDownloaded: Int = 0,
    val imagesFailed: Int = 0
)

data class MiCatalogoPosSaleSyncResult(
    val sent: Int,
    val retried: Int,
    val blocked: Int,
    val recoveredBottles: List<String> = emptyList()
)

sealed interface MiCatalogoResult<out T> {
    data class Success<T>(val value: T) : MiCatalogoResult<T>
    data class Failure(val message: String) : MiCatalogoResult<Nothing>
}
