package com.example.bspos.presentation.pos

import com.example.bspos.domain.model.Product
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosProductPresentationTest {
    private val decant = Product(
        id = UUID.randomUUID(),
        name = "Baccarat Rouge 5 ml",
        internalCode = "BR-5",
        categoryId = UUID.randomUUID(),
        unitId = UUID.randomUUID(),
        salePrice = 120000,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        remoteSaleUnit = "decant",
        remoteVolumeMl = 5,
        remoteSourceProductId = "baccarat-bottle",
    )

    @Test
    fun decantsExposeTheirPresentationAndAvailableUnits() {
        assertEquals("DECANT · 5 ml", decant.decantPresentationLabel())
        assertEquals("En stock (8)", decant.posStockLabel(8, allowNegativeStock = false))
        assertEquals("Agotado", decant.posStockLabel(0, allowNegativeStock = false))
        assertEquals("Fuente Baccarat Rouge 100 ml", decant.sourceProductName(mapOf("baccarat-bottle" to "Fuente Baccarat Rouge 100 ml")))
    }

    @Test
    fun nonDecantsDoNotReceiveDecantLabels() {
        val unit = decant.copy(remoteSaleUnit = "unit", remoteVolumeMl = null)

        assertNull(unit.decantPresentationLabel())
        assertEquals("En stock (3)", unit.posStockLabel(3, allowNegativeStock = false))
    }
}
