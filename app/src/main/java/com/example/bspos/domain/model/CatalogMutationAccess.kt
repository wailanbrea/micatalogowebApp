package com.example.bspos.domain.model

/** Missing action metadata can retain a known owner context, never promote legacy seller defaults. */
fun MiCatalogoShop.canManageCatalogType(saleUnit: String?): Boolean {
    val key = when (saleUnit) { "service" -> "services"; "decant" -> "decants"; else -> "products" }
    if (!canAccessMiCatalogoMenu(false, false, menuPermissions, key, capabilities, enabledMenuKeys, canManageMenuVisibility)) return false
    val action = when (saleUnit) { "service" -> "manage_services"; "decant" -> "manage_decants"; else -> "manage_products" }
    return catalogPermissions[action] ?: canManageSellers
}

fun MiCatalogoShop.canReadCatalogCosts(): Boolean = catalogPermissions["read_costs"]
    ?: (canManageSellers || "finance" in menuPermissions)

fun MiCatalogoShop.canManageCatalogPhotos(): Boolean =
    canAccessMiCatalogoMenu(false, false, menuPermissions, "photos", capabilities, enabledMenuKeys, canManageMenuVisibility) &&
        (catalogPermissions["manage_photos"] ?: canManageSellers)
