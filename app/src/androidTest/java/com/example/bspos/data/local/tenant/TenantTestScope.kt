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
