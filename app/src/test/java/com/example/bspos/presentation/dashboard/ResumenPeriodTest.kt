package com.example.bspos.presentation.dashboard

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumenPeriodTest {
    @Test fun lastSevenDaysIncludesTodayAndComparesWithPreviousSeven() {
        val range = resumenRange("Últimos 7", LocalDate.of(2026, 10, 7))
        assertEquals(ResumenRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7)), range)
        assertEquals(ResumenRange(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 30)), range.previous)
    }

    @Test fun customPeriodCrossingMonthComparesEqualLengthAndNoOverlap() {
        val range = ResumenRange(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 2))
        assertEquals(ResumenRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 28)), range.previous)
    }

    @Test fun comparisonDistinguishesNoHistoryFromRealDrop() {
        assertEquals("sin datos previos", resumenComparison(370000, 0))
        assertEquals("▼ 100% vs periodo anterior", resumenComparison(0, 370000))
        assertEquals("▲ 50% vs periodo anterior", resumenComparison(15000, 10000))
        assertEquals("▲ 0% vs periodo anterior", resumenComparison(10000, 10000))
    }
}
