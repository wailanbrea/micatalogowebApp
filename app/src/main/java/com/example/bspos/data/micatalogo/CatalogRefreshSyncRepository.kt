package com.example.bspos.data.micatalogo

import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.MiCatalogoCatalogRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRefreshSyncRepository @Inject constructor(
    private val queue: OperationOutboxDao,
    private val catalog: MiCatalogoCatalogRepository
) {
    private val mutex = Mutex()
    /** True means a durable download remains pending; never changes sales payloads. */
    suspend fun sync(): Boolean = mutex.withLock {
        queue.pendingRefreshes().forEach { row ->
            when (val result = catalog.syncCatalog(row.shopId)) {
                is MiCatalogoResult.Success -> queue.clearRefresh(row.shopId, row.revision)
                is MiCatalogoResult.Failure -> queue.refreshError(row.shopId, row.revision, result.message)
            }
        }
        queue.pendingRefreshes().isNotEmpty()
    }
}
