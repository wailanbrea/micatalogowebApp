package com.example.bspos.presentation.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtReminderHelperTest {

    @Test
    fun testBuildReminderMessage_withShopName() {
        val msg = DebtReminderHelper.buildReminderMessage(
            customerName = "Juan Pérez",
            balanceCents = 150050L,
            shopName = "Super Colmado",
            currencySymbol = "RD$"
        )
        assertTrue(msg.contains("Hola Juan Pérez"))
        assertTrue(msg.contains("Super Colmado"))
        assertTrue(msg.contains("RD$ 1,500.50"))
        assertTrue(msg.contains("Agradecemos su gestión de pago"))
    }

    @Test
    fun testBuildReminderMessage_withoutShopName() {
        val msg = DebtReminderHelper.buildReminderMessage(
            customerName = "María Gómez",
            balanceCents = 20000L,
            shopName = null,
            currencySymbol = "RD$"
        )
        assertTrue(msg.contains("Hola María Gómez, le saludamos."))
        assertTrue(msg.contains("RD$ 200.00"))
    }

    @Test
    fun testCleanPhoneNumber_dominicanTenDigits() {
        assertEquals("18091234567", DebtReminderHelper.cleanPhoneNumber("809-123-4567"))
        assertEquals("18295556789", DebtReminderHelper.cleanPhoneNumber("(829) 555-6789"))
        assertEquals("18490001122", DebtReminderHelper.cleanPhoneNumber("8490001122"))
    }

    @Test
    fun testCleanPhoneNumber_withCountryCode() {
        assertEquals("18091234567", DebtReminderHelper.cleanPhoneNumber("+18091234567"))
        assertEquals("34612345678", DebtReminderHelper.cleanPhoneNumber("+34 612 34 56 78"))
    }

    @Test
    fun testCleanPhoneNumber_blankOrNull() {
        assertNull(DebtReminderHelper.cleanPhoneNumber(null))
        assertNull(DebtReminderHelper.cleanPhoneNumber(""))
        assertNull(DebtReminderHelper.cleanPhoneNumber("   "))
        assertNull(DebtReminderHelper.cleanPhoneNumber("---"))
    }
}
