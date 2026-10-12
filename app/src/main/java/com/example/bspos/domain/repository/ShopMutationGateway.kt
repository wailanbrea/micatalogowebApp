package com.example.bspos.domain.repository

import kotlinx.serialization.json.JsonObject

interface ShopMutationGateway {
    suspend fun submit(shop: String, type: String, request: JsonObject, resource: String? = null): JsonObject
}
