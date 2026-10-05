package com.example.bspos.core.database

import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryLocationType
import com.example.bspos.domain.model.InventoryMovementType
import com.example.bspos.domain.model.InventoryReferenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class InventoryConvertersTest {
    private val converters = InventoryConverters()

    @Test
    fun enumNamesRoundTripWithoutOrdinalStorage() {
        InventoryLocationType.entries.forEach { assertEquals(it, converters.stringToLocation(converters.locationToString(it))) }
        InventoryMovementType.entries.forEach { assertEquals(it, converters.stringToMovement(converters.movementToString(it))) }
        AdjustmentDirection.entries.forEach { assertEquals(it, converters.stringToDirection(converters.directionToString(it))) }
        InventoryReferenceType.entries.forEach { assertEquals(it, converters.stringToReference(converters.referenceToString(it))) }
        assertEquals("PURCHASE_RETURN", converters.movementToString(InventoryMovementType.PURCHASE_RETURN))
        assertNull(converters.referenceToString(null))
        assertNull(converters.stringToReference(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownMovementTypeIsNotSilentlyReinterpreted() {
        converters.stringToMovement("UNKNOWN")
    }

    @Test
    fun warehouseAndRouteHaveCanonicalKeys() {
        val id = UUID.randomUUID()
        assertEquals("MAIN", InventoryLocation.MAIN.id)
        assertEquals(InventoryLocationType.ROUTE, InventoryLocation.route(id).type)
        assertEquals(id.toString(), InventoryLocation.route(id).id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun warehouseRejectsAlternateIds() { InventoryLocation(InventoryLocationType.MAIN_WAREHOUSE, "main") }

    @Test(expected = IllegalArgumentException::class)
    fun routeRejectsMainWarehouseKey() { InventoryLocation(InventoryLocationType.ROUTE, "MAIN") }

    @Test(expected = IllegalArgumentException::class)
    fun routeRejectsAbbreviatedUuid() { InventoryLocation(InventoryLocationType.ROUTE, "1-1-1-1-1") }
}
