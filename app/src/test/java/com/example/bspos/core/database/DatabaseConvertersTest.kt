package com.example.bspos.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

class DatabaseConvertersTest {
    private val converters = DatabaseConverters()

    @Test
    fun uuidHasCanonicalTextRepresentation() {
        val text = "abcde123-4567-4890-abcd-123456789012"
        val uuid = UUID.fromString(text)
        assertEquals(text, converters.uuidToString(uuid))
        assertEquals(uuid, converters.stringToUuid(text))
    }

    @Test
    fun instantsPreserveMillisecondsBeforeAndAfterEpoch() {
        listOf(-123456789L, 0L, 1790000000123L).forEach { millis ->
            val instant = Instant.ofEpochMilli(millis)
            assertEquals(millis, converters.instantToLong(instant))
            assertEquals(instant, converters.longToInstant(millis))
        }
    }

    @Test
    fun nullableAuditFieldsRemainNull() {
        assertNull(converters.uuidToString(null))
        assertNull(converters.stringToUuid(null))
        assertNull(converters.instantToLong(null))
        assertNull(converters.longToInstant(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptUuidFailsInsteadOfSilentlyChangingIdentity() {
        converters.stringToUuid("not-a-uuid")
    }
}
