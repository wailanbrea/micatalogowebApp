package com.example.bspos.data.micatalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCatalogoImportMapperTest {
    @Test
    fun deterministicIdsAreStableAndScopedToTheRemoteShop() {
        val first = MiCatalogoImportMapper.productId("shop-a", "01JREMOTEPRODUCT")

        assertEquals(first, MiCatalogoImportMapper.productId("shop-a", "01JREMOTEPRODUCT"))
        assertNotEquals(first, MiCatalogoImportMapper.productId("shop-b", "01JREMOTEPRODUCT"))
        assertTrue(MiCatalogoImportMapper.internalCode("shop-a", "01JREMOTEPRODUCT").startsWith("MC-"))
    }

    @Test
    fun centsRequireAnExactTwoDecimalRemoteValue() {
        assertEquals(12345L, MiCatalogoImportMapper.cents("123.45"))
        assertEquals(1200L, MiCatalogoImportMapper.cents("12"))
        assertEquals(0L, MiCatalogoImportMapper.cents(null))
        try {
            MiCatalogoImportMapper.cents("1.234")
            throw AssertionError("Expected exact cents conversion to reject fractions of a cent")
        } catch (_: ArithmeticException) {
        }
    }

    @Test
    fun draftAndSuspendedProductsAreInactive() {
        assertFalse(MiCatalogoImportMapper.isProductActive("available", "draft"))
        assertFalse(MiCatalogoImportMapper.isProductActive("suspended", "approved"))
        assertTrue(MiCatalogoImportMapper.isProductActive("available", "approved"))
    }
}
