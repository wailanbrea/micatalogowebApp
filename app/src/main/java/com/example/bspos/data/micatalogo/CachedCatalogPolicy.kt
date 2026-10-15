package com.example.bspos.data.micatalogo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bspos.data.micatalogo.dto.ShopDto
import com.example.bspos.domain.model.canAccessMiCatalogoMenu
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal val SHOP_SNAPSHOT_CACHE = stringPreferencesKey("micatalogo_shop_cache")

/** Reads the same persisted shop snapshot, never performs HTTP inside a Room transaction. */
class CachedCatalogPolicy @Inject constructor(private val store: DataStore<Preferences>, private val json: Json) {
    suspend fun canManagePhotos(shopId: String): Boolean {
        val snapshot = store.data.first()[SHOP_SNAPSHOT_CACHE] ?: return false
        val shop = runCatching { json.decodeFromString<List<ShopDto>>(snapshot).firstOrNull { it.id == shopId } }.getOrNull() ?: return false
        return canAccessMiCatalogoMenu(false, false, shop.menuPermissions, "photos", shop.capabilities,
            shop.enabledMenuKeys, shop.canManageMenuVisibility) && (shop.catalogPermissions["manage_photos"] ?: shop.canManageSellers)
    }

    suspend fun canReadCosts(shopId: String): Boolean {
        val snapshot = store.data.first()[SHOP_SNAPSHOT_CACHE] ?: return false
        val shop = runCatching { json.decodeFromString<List<ShopDto>>(snapshot).firstOrNull { it.id == shopId } }.getOrNull() ?: return false
        return shop.catalogPermissions["read_costs"] ?: (shop.canManageSellers || canAccessMiCatalogoMenu(
            false, false, shop.menuPermissions, "finance", shop.capabilities, shop.enabledMenuKeys, shop.canManageMenuVisibility))
    }
}

internal fun includeInitialCost(saleUnit: String?, cost: Long, mayReadCosts: Boolean): Boolean =
    saleUnit != "service" || cost != 0L || mayReadCosts
