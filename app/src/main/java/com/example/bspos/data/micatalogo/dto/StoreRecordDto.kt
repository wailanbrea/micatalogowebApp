package com.example.bspos.data.micatalogo.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class StoreRecordDto(
    val kind: String,
    @SerialName("record_id") val recordId: String,
    val payload: JsonObject,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class RouteStockDto(
    @SerialName("route_id") val routeId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Long,
    @SerialName("unit_cost_cents") val unitCostCents: Long = 0
)
