package com.example.bspos.presentation.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupPolicyTest {
    @Test fun unknownRemoteStatusNeverLooksLikeANewShop() {
        assertEquals(null, resolveFirstSaleStatus(null, localHasSale = false))
    }

    @Test fun confirmedEmptyRemoteHistoryShowsFirstSteps() {
        assertEquals(false, resolveFirstSaleStatus(false, localHasSale = false))
    }

    @Test fun localSaleCompletesTheMilestoneImmediately() {
        assertEquals(true, resolveFirstSaleStatus(false, localHasSale = true))
    }

    @Test fun confirmedRemoteSaleStaysCompletedWithoutLocalHistory() {
        assertEquals(true, resolveFirstSaleStatus(true, localHasSale = false))
    }
}
