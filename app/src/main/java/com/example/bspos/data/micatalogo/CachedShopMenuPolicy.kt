package com.example.bspos.data.micatalogo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.bspos.data.micatalogo.dto.ShopDto
import com.example.bspos.domain.model.canAccessMiCatalogoMenu
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Last effective shop permissions, read locally inside a mutation; never performs HTTP. */
class CachedShopMenuPolicy @Inject constructor(private val store: DataStore<Preferences>, private val json: Json) {
    suspend fun allows(shopId: String, key: String): Boolean {
        val cache = store.data.first()[SHOP_SNAPSHOT_CACHE] ?: return false
        val shop = runCatching { json.decodeFromString<List<ShopDto>>(cache).firstOrNull { it.id == shopId } }.getOrNull() ?: return false
        return canAccessMiCatalogoMenu(false, false, shop.menuPermissions, key, shop.capabilities,
            shop.enabledMenuKeys, shop.canManageMenuVisibility)
    }
}
