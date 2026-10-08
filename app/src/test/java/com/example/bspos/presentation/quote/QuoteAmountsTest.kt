package com.example.bspos.presentation.quote

import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteAmountsTest {
    @Test fun parsesDecimalPricesExactlyInCents() {
        assertEquals(95000L, parseMoney("950.00"))
        assertEquals(29L, parseMoney("0.29"))
        assertEquals(1770000L, parseMoney("RD$ 17,700.00"))
    }
}
