package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

data class UnitOfMeasure(
    val id: UUID,
    val name: String,
    val abbreviation: String,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
