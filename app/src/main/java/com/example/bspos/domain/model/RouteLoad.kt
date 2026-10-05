package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

enum class RouteLoadStatus { OPEN, SETTLED, CANCELLED }

data class RouteLoad(
    val id: UUID, val routeId: UUID, val date: Instant, val status: RouteLoadStatus = RouteLoadStatus.OPEN,
    val notes: String? = null, val createdAt: Instant
)

/** Cost is captured when the route is loaded and remains independent of later CPP changes. */
data class RouteLoadItem(
    val id: UUID, val routeLoadId: UUID, val productId: UUID, val quantity: Long, val unitCostSnapshot: Long
)
