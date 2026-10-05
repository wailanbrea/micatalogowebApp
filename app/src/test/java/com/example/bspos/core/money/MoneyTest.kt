package com.example.bspos.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun decimalInputsPreserveCentsAndRejectInvalidOrOverflowingValues() {
        assertEquals(1055L, MoneyUtils.parseDecimalToCents("10.55"))
        assertEquals(1055L, MoneyUtils.parseDecimalToCents("10,55"))
        assertEquals(0L, MoneyUtils.parseDecimalToCents("0"))
        for (input in listOf("", "-1", "1.234", "1.2.3", "1,000.00", "texto", "92233720368547758.08")) {
            org.junit.Assert.assertNull(MoneyUtils.parseDecimalToCents(input))
        }
    }

    @Test
    fun testFormatCents_regularAmount() {
        val cents = 3500L
        val formatted = MoneyUtils.formatCents(cents)
        assertEquals("RD$ 35.00", formatted)
    }

    @Test
    fun testFormatCents_largeAmount() {
        val cents = 2475000L
        val formatted = MoneyUtils.formatCents(cents)
        assertEquals("RD$ 24,750.00", formatted)
    }

    @Test
    fun testFormatCents_withFractionalCents() {
        val cents = 1840050L
        val formatted = MoneyUtils.formatCents(cents)
        assertEquals("RD$ 18,400.50", formatted)
    }

    @Test
    fun testPesosToCents() {
        val pesos = 35.50
        val cents = MoneyUtils.pesosToCents(pesos)
        assertEquals(3550L, cents)
    }

    @Test
    fun testParsePesosStringToCents() {
        val input = "RD$ 24,750.00"
        val cents = MoneyUtils.parsePesosStringToCents(input)
        assertEquals(2475000L, cents)
    }
}
