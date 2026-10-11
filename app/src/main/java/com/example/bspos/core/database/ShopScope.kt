package com.example.bspos.core.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private class ShopMutationContext(val lock: ShopSwitchLock, val shop: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ShopMutationContext>
}

@Singleton
class ShopSwitchLock @Inject constructor() { internal val mutex = Mutex() }

@Singleton
class ShopScope @Inject constructor(private val preferences: DataStore<Preferences>, private val lock: ShopSwitchLock) {
    val changes: Flow<String?> = preferences.data.map { it[ACTIVE_SHOP_ID]?.takeIf(String::isNotBlank) }.distinctUntilChanged()
    suspend fun current(): String = currentCoroutineContext()[ShopMutationContext]?.takeIf { it.lock===lock }?.shop
        ?: changes.first() ?: error("Selecciona una tienda activa antes de operar.")

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(query: (String?) -> Flow<List<T>>): Flow<List<T>> = changes.flatMapLatest { shop ->
        if (shop == null) flowOf(emptyList()) else query(shop).onStart { emit(emptyList()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observeValue(query: (String?) -> Flow<T?>): Flow<T?> = changes.flatMapLatest { shop ->
        if (shop == null) flowOf(null) else query(shop).onStart { emit(null) }
    }

    suspend fun <T> mutate(block: suspend (String) -> T): T {
        // A use case transaction and its repositories share the same captured tenant.
        // This also keeps the lock order consistent: shop lock, then Room transaction.
        currentCoroutineContext()[ShopMutationContext]?.takeIf { it.lock===lock }?.let { return block(it.shop) }
        return lock.mutex.withLock {
            val shop = current()
            withContext(ShopMutationContext(lock, shop)) { block(shop) }
                .also { check(changes.first() == shop) { "La tienda cambió; vuelve a intentar la operación." } }
        }
    }

    companion object { val ACTIVE_SHOP_ID = stringPreferencesKey("micatalogo_active_shop_id") }
}
