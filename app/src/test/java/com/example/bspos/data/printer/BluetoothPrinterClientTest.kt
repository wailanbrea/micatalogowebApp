package com.example.bspos.data.printer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothPrinterClientTest {
    @Test
    fun payloadUsesEscPosResetPrinterEncodingAndFinalFeed() {
        val payload = buildEscPosPayload("PRUEBA\nLínea dos\r\nLínea tres")

        assertArrayEquals(byteArrayOf(0x1B, 0x40, 0x1B, 0x61, 0x00), payload.take(5).toByteArray())

        val printableContent = payload.drop(5).toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue(printableContent.startsWith("PRUEBA\r\nLínea dos\r\nLínea tres"))
        assertTrue(printableContent.endsWith("\r\n\r\n\r\n\r\n\r\n"))
        assertFalse(payload.asList().windowed(2).any { it == listOf(0x1D.toByte(), 0x56.toByte()) })
    }

    @Test
    fun payloadNormalizesTrailingLineBreaksBeforeFeedingPaper() {
        val payload = buildEscPosPayload("Ticket\n\n")
        val printableContent = payload.drop(5).toByteArray().toString(Charsets.ISO_8859_1)

        assertEquals("Ticket\r\n\r\n\r\n\r\n\r\n", printableContent)
    }
}
