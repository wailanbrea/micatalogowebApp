package com.example.bspos.presentation.purchase

import com.example.bspos.data.micatalogo.dto.PurchaseDocumentDto
import com.example.bspos.domain.model.MiCatalogoShop
import org.junit.Assert.*
import org.junit.Test

class PurchaseAccessTest {
    @Test fun invoiceAndSupplierPermissionsNeverGrantContainerOrRouteLoadOperations() {
        val invoice = PurchaseAccess.forShop(MiCatalogoShop("a", "A", null, menuPermissions = listOf("purchase_invoices")), "seller")
        assertTrue(invoice.creates("purchase_invoice"))
        assertTrue(invoice.creates("supplier_debt"))
        assertFalse(invoice.creates("container"))
        assertFalse(invoice.creates("load"))
        assertFalse(invoice.reads("route_loads"))
        assertFalse(invoice.receives(PurchaseDocumentDto(type = "container")))
        assertFalse(invoice.receives(PurchaseDocumentDto(type = "purchase_invoice", status = "received")))
        val supplier = PurchaseAccess.forShop(MiCatalogoShop("a", "A", null, menuPermissions = listOf("suppliers")), "seller")
        assertTrue(supplier.creates("supplier_debt"))
        assertFalse(supplier.previews)
        assertFalse(supplier.receives(PurchaseDocumentDto(type = "purchase_invoice")))
    }

    @Test fun accountantAndRevokedShopKeysDoNotAuthorizePurchaseOperations() {
        val shop = MiCatalogoShop("a", "A", null, menuPermissions = listOf("containers", "suppliers"))
        assertTrue(PurchaseAccess.forShop(shop, "seller").creates("container"))
        assertFalse(PurchaseAccess.forShop(shop, "accountant").creates("container"))
        assertFalse(PurchaseAccess.forShop(shop.copy(enabledMenuKeys = emptyList()), "seller").reads("containers"))
        assertFalse(PurchaseAccess.forShop(null, "admin").reads("suppliers"))
    }
}
