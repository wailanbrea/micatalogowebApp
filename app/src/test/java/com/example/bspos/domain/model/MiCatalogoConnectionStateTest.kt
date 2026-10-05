package com.example.bspos.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCatalogoConnectionStateTest {
    @Test
    fun endpointAloneDoesNotDisableDemoData() {
        assertFalse(MiCatalogoConnectionState("http://10.0.2.2:8000/", false).isConfigured)
    }

    @Test
    fun accessTokenMarksTheConnectionConfigured() {
        assertTrue(MiCatalogoConnectionState("https://api.example.test/", true).isConfigured)
    }
}
