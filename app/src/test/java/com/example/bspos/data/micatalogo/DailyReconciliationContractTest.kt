package com.example.bspos.data.micatalogo

import com.example.bspos.data.micatalogo.dto.DailyCloseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DailyReconciliationContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun legacyDailyCloseDoesNotInventAdjustmentAuthority() {
        val value = json.decodeFromString<DailyCloseDto>("""{"business_date":"2026-10-19","closure":{"id":"original","business_date":"2026-10-19","expected_cash":100}}""")
        assertFalse(value.canAdjust)
        assertTrue(value.adjustments.isEmpty())
        assertEquals("original", value.closure!!.id)
    }
    @Test fun auditedMoneyRemainsDecimalTextAndOriginalSnapshotRemainsSeparate() {
        val value = json.decodeFromString<DailyCloseDto>("""{"business_date":"2026-10-19","can_adjust":true,"expected_cash":120,
            "closure":{"id":"original","business_date":"2026-10-19","expected_cash":100},"adjustments":[
            {"id":"adjustment","closure_id":"original","user_id":"1","reason":"QA count","created_at":"2026-10-19T12:00:00Z",
            "expected_cash":"120.00","counted_cash":"115.00","difference":"-5.00","change_from_original":"20.00"}]}""")
        assertEquals(100.0, value.closure!!.expectedCash, 0.0)
        assertEquals("120.00", value.adjustments.single().expectedCash)
        assertEquals("-5.00", value.adjustments.single().difference)
        assertTrue(value.canAdjust)
    }
}
