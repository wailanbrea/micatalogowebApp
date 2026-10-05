package com.example.bspos.domain.repository

import com.example.bspos.domain.model.MiCatalogoPosSaleSyncResult
import com.example.bspos.domain.model.MiCatalogoResult

interface MiCatalogoPosSaleRepository {
    /** Uploads only locally completed, eligible sale snapshots. It never changes the remote catalog. */
    suspend fun syncDueSales(): MiCatalogoResult<MiCatalogoPosSaleSyncResult>
}
