package com.example.bspos.data.micatalogo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import dagger.Lazy
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ShopSnapshotObservationTest {
    @Test fun updatedAndEmptySnapshotsAreObservedWithoutNetworkOrConnectionRestart() = runTest {
        val values = MutableStateFlow<Preferences>(emptyPreferences())
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(values.value).also { values.value = it }
        }
        val api = Proxy.newProxyInstance(MiCatalogoApi::class.java.classLoader, arrayOf(MiCatalogoApi::class.java)) { _, method, _ ->
            error("Observing a snapshot must not call HTTP: ${method.name}")
        } as MiCatalogoApi
        val repository = MiCatalogoConnectionRepositoryImpl(store, Lazy { api }, "http://localhost/", Json { ignoreUnknownKeys = true })
        val connectionBefore = repository.observeConnection().first()
        assertNull(repository.observeShops().first())
        store.edit { it[SHOP_SNAPSHOT_CACHE] = """[{"id":"a","name":"QA_NAV_A","menu_permissions":["sales"]}]""" }
        assertEquals(listOf("sales"), repository.observeShops().first()!!.single().menuPermissions)
        store.edit { it[SHOP_SNAPSHOT_CACHE] = """[{"id":"a","name":"QA_NAV_A","menu_permissions":[]}]""" }
        assertTrue(repository.observeShops().first()!!.single().menuPermissions.isEmpty())
        store.edit { it[SHOP_SNAPSHOT_CACHE] = "[]" }
        assertTrue(repository.observeShops().first()!!.isEmpty())
        assertEquals(connectionBefore, repository.observeConnection().first())
    }
}
