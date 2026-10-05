package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

data class Category(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val icon: String = "category_default",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
