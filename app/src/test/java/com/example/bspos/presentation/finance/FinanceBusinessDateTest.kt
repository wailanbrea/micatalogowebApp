package com.example.bspos.presentation.finance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FinanceBusinessDateTest {
    private val justAfterMidnightUtc = Instant.parse("2026-10-20T03:57:00Z")

    @Test fun todayUsesShopTimezoneInsteadOfDeviceTimezone() {
        assertEquals("2026-10-19", financeDateRange(FinancePeriodFilter.TODAY, "America/Santo_Domingo", justAfterMidnightUtc).first)
        assertEquals("2026-10-20", financeDateRange(FinancePeriodFilter.TODAY, "GMT", justAfterMidnightUtc).first)
    }

    @Test fun monthRangeUsesBusinessCalendarAndInvalidTimezoneFallsBackSafely() {
        assertEquals(
            "2026-10-01" to "2026-10-19",
            financeDateRange(FinancePeriodFilter.THIS_MONTH, "America/Santo_Domingo", justAfterMidnightUtc)
        )
        assertEquals("America/Santo_Domingo", normalizeBusinessTimezone("Invalid/Zone"))
    }
}
