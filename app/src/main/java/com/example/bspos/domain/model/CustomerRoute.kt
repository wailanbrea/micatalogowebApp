package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

data class Customer(
    val id: UUID, val businessName: String, val ownerName: String? = null, val phone: String? = null,
    val whatsapp: String? = null, val address: String? = null, val reference: String? = null,
    val taxId: String? = null, val visitDays: String? = null, val creditLimit: Long = 0,
    val balance: Long = 0, val latitude: Double? = null, val longitude: Double? = null,
    val notes: String? = null, val isActive: Boolean = true, val createdAt: Instant,
    val updatedAt: Instant, val deletedAt: Instant? = null, val firstName: String? = null,
    val lastName: String? = null, val documentType: String? = null, val documentNumber: String? = null,
    val email: String? = null
) {
    val fullName: String
        get() = listOfNotNull(firstName?.trim()?.takeIf { it.isNotBlank() }, lastName?.trim()?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { businessName }
}

data class CommercialRoute(
    val id: UUID, val name: String, val code: String, val description: String? = null,
    val isActive: Boolean = true, val createdAt: Instant, val updatedAt: Instant
)

/** A customer belongs to zero or one active commercial route. */
data class RouteCustomer(val routeId: UUID, val customerId: UUID, val visitOrder: Int = 0)
