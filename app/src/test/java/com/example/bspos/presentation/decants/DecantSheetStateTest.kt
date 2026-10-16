package com.example.bspos.presentation.decants

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecantSheetStateTest {
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
}
