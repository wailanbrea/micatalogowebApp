package com.example.bspos.data.local.tenant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.bspos.core.database.ShopScope
import com.example.bspos.core.database.ShopSwitchLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Explicit tenant for isolated instrumented fixtures, never application preferences. */
fun tenantTestScope(shopId:String):ShopScope {
    val store=object:DataStore<Preferences> {
        private val value=MutableStateFlow<Preferences>(preferencesOf(ShopScope.ACTIVE_SHOP_ID to shopId))
        override val data:Flow<Preferences> = value
        override suspend fun updateData(transform:suspend(Preferences)->Preferences):Preferences = transform(value.value).also { value.value=it }
    }
    return ShopScope(store,ShopSwitchLock())
}

/** Explicit permissions for synthetic owner fixtures, never application preferences. */
fun tenantTestMenuPolicy(shopId: String, keys: List<String> = listOf("sales", "credit", "collections")): com.example.bspos.data.micatalogo.CachedShopMenuPolicy {
    val json = kotlinx.serialization.json.Json
    val payload = kotlinx.serialization.json.buildJsonArray {
        add(kotlinx.serialization.json.buildJsonObject {
            put("id", kotlinx.serialization.json.JsonPrimitive(shopId))
            put("menu_permissions", kotlinx.serialization.json.JsonArray(keys.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        })
    }.toString()
    val store = object : DataStore<Preferences> {
        override val data = kotlinx.coroutines.flow.flowOf(preferencesOf(com.example.bspos.data.micatalogo.SHOP_SNAPSHOT_CACHE to payload))
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Read-only synthetic fixture")
    }
    return com.example.bspos.data.micatalogo.CachedShopMenuPolicy(store, json)
}
