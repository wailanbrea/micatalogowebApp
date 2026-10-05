package com.example.bspos.data.repository

import com.example.bspos.data.local.dao.InventoryDocumentDao
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.domain.model.InventoryCount
import com.example.bspos.domain.model.InventoryCountItem
import com.example.bspos.domain.model.InventoryCountStatus
import com.example.bspos.domain.model.Purchase
import com.example.bspos.domain.model.PurchaseItem
import com.example.bspos.domain.model.StockEntry
import com.example.bspos.domain.model.StockEntryItem
import com.example.bspos.domain.repository.InventoryDocumentRepository
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class InventoryDocumentRepositoryImpl @Inject constructor(
    private val local: InventoryDocumentDao
) : InventoryDocumentRepository {
    override suspend fun insertPurchase(purchase: Purchase, items: List<PurchaseItem>) =
        local.insertPurchaseWithItems(purchase.toEntity(), items.map { it.toEntity() })
    override fun observePurchaseItems(purchaseId: UUID) = local.observePurchaseItems(purchaseId).map { it.map { row -> row.toDomain() } }
    override fun observePurchasesForSupplier(supplierId: UUID) = local.observePurchasesForSupplier(supplierId).map { it.map { row -> row.toDomain() } }
    override suspend fun insertStockEntry(entry: StockEntry, items: List<StockEntryItem>) =
        local.insertStockEntryWithItems(entry.toEntity(), items.map { it.toEntity() })
    override fun observeStockEntryItems(entryId: UUID) = local.observeStockEntryItems(entryId).map { it.map { row -> row.toDomain() } }
    override suspend fun insertCount(count: InventoryCount, items: List<InventoryCountItem>) =
        local.insertCountWithItems(count.toEntity(), items.map { it.toEntity() })
    override fun observeCountItems(countId: UUID) = local.observeCountItems(countId).map { it.map { row -> row.toDomain() } }
    override fun observeCounts() = local.observeCounts().map { it.map { row -> row.toDomain() } }
    override suspend fun updateCountStatus(id: UUID, status: InventoryCountStatus, completedAt: Instant?) =
        local.updateCountStatus(id, status, completedAt)
}
