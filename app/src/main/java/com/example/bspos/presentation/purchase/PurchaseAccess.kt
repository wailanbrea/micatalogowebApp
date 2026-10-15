package com.example.bspos.presentation.purchase

import com.example.bspos.data.micatalogo.dto.PurchaseDocumentDto
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.canAccessMiCatalogoMenu

/** A purchase area never grants access to another document type. */
data class PurchaseAccess(val keys: Set<String> = emptySet()) {
    fun reads(feature: String): Boolean = feature in FEATURES && feature in keys
    fun creates(type: String): Boolean = when (type) {
        "container" -> reads("containers")
        "load" -> reads("loads")
        "purchase_invoice" -> reads("purchase_invoices")
        "supplier_debt" -> reads("suppliers") || reads("purchase_invoices")
        else -> false
    }
    fun reads(document: PurchaseDocumentDto): Boolean = creates(if (document.status == "debt") "supplier_debt" else document.type)
    fun receives(document: PurchaseDocumentDto): Boolean = document.status == "draft" &&
        document.type in setOf("container", "purchase_invoice") && creates(document.type)
    val previews: Boolean get() = keys.any { it in setOf("containers", "loads", "purchase_invoices") }

    companion object {
        private val FEATURES = setOf("containers", "loads", "purchase_invoices", "suppliers")
        fun forShop(shop: MiCatalogoShop?, role: String): PurchaseAccess = if (shop == null || role == "accountant") PurchaseAccess() else PurchaseAccess(
            FEATURES.filterTo(mutableSetOf()) { key -> canAccessMiCatalogoMenu(false, false, shop.menuPermissions, key,
                shop.capabilities, shop.enabledMenuKeys, shop.canManageMenuVisibility) }
        )
    }
}
