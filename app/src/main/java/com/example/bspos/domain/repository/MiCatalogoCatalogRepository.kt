package com.example.bspos.domain.repository

import com.example.bspos.domain.model.MiCatalogoCatalogSyncResult
import com.example.bspos.domain.model.MiCatalogoResult

interface MiCatalogoCatalogRepository {
    /** Downloads a read-only remote snapshot and applies it only to the local BSPOS database. */
    suspend fun syncCatalog(shopId: String): MiCatalogoResult<MiCatalogoCatalogSyncResult>

    /** Hides remote products belonging to shops outside the current account scope. */
    suspend fun archiveRemoteProductsExcept(shopIds: Set<String>)
}
