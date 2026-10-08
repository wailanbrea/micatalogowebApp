package com.example.bspos.presentation.dayclose

import org.junit.Assert.*
import org.junit.Test

class CountedCashTest {
    @Test fun acceptsOptionalOrNonnegativeMoney() {
        listOf("", "0", "9500", "9500.00", "0.01").forEach { assertTrue(it, validCountedCash(it)) }
    }
    @Test fun rejectsInvalidAmountsBeforeSending() {
        listOf(".", "1..2", "-1", "abc", "1.234", "1.").forEach { assertFalse(it, validCountedCash(it)) }
    }
}
