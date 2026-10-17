package com.example.bspos.presentation.decants

import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.Product
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecantSheetStateTest {
    @Test
    fun sinVolumenOnlyListsActiveUnitProductsInPerfumeCategories() {
        val perfumes = Category(UUID.randomUUID(), "Perfumes de hombre", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val electronics = Category(UUID.randomUUID(), "Electrónica", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val shop = "shop-qa"
        val candidates = listOf(
            product("212 Men NYC", perfumes.id, shop),
            product("Billetera Tommy", electronics.id, shop),
            product("Botella ya configurada", perfumes.id, shop, saleUnit = "bottle"),
            product("Perfume de otra tienda", perfumes.id, "other-shop"),
            product("Combo perfume", perfumes.id, shop, combo = true),
            product("Perfume archivado", perfumes.id, shop, active = false)
        )

        assertEquals(listOf("212 Men NYC"), unconfiguredBottleSources(candidates, listOf(perfumes, electronics), shop).map { it.name })
    }

    @Test
    fun preparingStartsWithoutBottleOrSizeSelected() {
        val initial = DecantPrepareSelectionState(groupId = "focus-perfume")

        assertEquals("focus-perfume", initial.groupId)
        assertNull(initial.openingId)
        assertEquals(false, initial.sealedSelected)
        assertNull(initial.sizeKey)
    }

    @Test
    fun selectingBottleDoesNotPreselectAnyDecantSize() {
        val selected = DecantPrepareSelectionState(groupId = "perfume")
            .selectSource(groupId = "perfume", openingId = "opening-1")

        assertEquals("opening-1", selected.openingId)
        assertNull(selected.sizeKey)
    }

    @Test
    fun lateDismissFromAdvancedOptionsDoesNotCloseTheEmptyVialsSheet() {
        assertEquals("vials", dismissDecantSheetIfCurrent(current = "vials", closing = "advanced"))
    }

    @Test
    fun dismissingTheCurrentlyVisibleSheetClosesIt() {
        assertNull(dismissDecantSheetIfCurrent(current = "vials", closing = "vials"))
    }

    @Test
    fun lateDismissDoesNotCloseAnyReplacementSheet() {
        assertEquals("orders", dismissDecantSheetIfCurrent(current = "orders", closing = "advanced"))
    }

    private fun product(
        name: String,
        categoryId: UUID,
        shopId: String,
        saleUnit: String? = "unit",
        combo: Boolean = false,
        active: Boolean = true
    ) = Product(
        id = UUID.randomUUID(), name = name, internalCode = name.uppercase(), categoryId = categoryId,
        unitId = UUID.randomUUID(), salePrice = 100, isActive = active, createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH, remoteShopId = shopId, remoteProductId = UUID.randomUUID().toString(),
        remoteSaleUnit = saleUnit, remoteIsCombo = combo
    )
}
