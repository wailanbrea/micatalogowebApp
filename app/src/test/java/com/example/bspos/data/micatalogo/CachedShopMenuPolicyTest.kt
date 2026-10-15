package com.example.bspos.data.micatalogo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CachedShopMenuPolicyTest {
    @Test fun collectionsUsesItsOwnPermissionAndCustomerCapabilityNotNewCredit() = runTest {
        val values = MutableStateFlow(preferencesOf(SHOP_SNAPSHOT_CACHE to """[
            {"id":"a","menu_permissions":["collections"],"capabilities":{"customers":"enabled","credit":"disabled"}},
            {"id":"b","menu_permissions":["cash","credit","customers"]}]"""))
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Read-only")
        }
        val policy = CachedShopMenuPolicy(store, Json { ignoreUnknownKeys = true })
        assertTrue(policy.allows("a", "collections"))
        assertFalse(policy.allows("a", "credit"))
        assertFalse(policy.allows("b", "collections"))
        assertFalse(policy.allows("unknown", "collections"))
        values.value = emptyPreferences()
        assertFalse(policy.allows("a", "collections"))
        values.value = preferencesOf(SHOP_SNAPSHOT_CACHE to "malformed")
        assertFalse(policy.allows("a", "collections"))
    }

    @Test fun disabledEffectiveMenuDoesNotGainAuthorityFromAStoredDelegation() = runTest {
        val store = object : DataStore<Preferences> {
            override val data = kotlinx.coroutines.flow.flowOf(preferencesOf(SHOP_SNAPSHOT_CACHE to
                """[{"id":"a","menu_permissions":["collections"],"enabled_menu_keys":[]}]"""))
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Read-only")
        }
        assertFalse(CachedShopMenuPolicy(store, Json { ignoreUnknownKeys = true }).allows("a", "collections"))
    }
}
