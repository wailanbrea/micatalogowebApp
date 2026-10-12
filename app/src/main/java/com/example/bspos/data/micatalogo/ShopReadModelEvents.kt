package com.example.bspos.data.micatalogo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Only an acknowledged mutation with successful read-back invalidates dependent modules. */
@Singleton
class ShopReadModelEvents @Inject constructor() {
    private val updates = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val changes = updates.asSharedFlow()
    suspend fun confirmed(shop: String) { updates.emit(shop) }
}
