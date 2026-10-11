package com.example.bspos.data.repository

import com.example.bspos.data.local.dao.InventoryDocumentDao
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.entity.TenantMetadata
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
    private val local: InventoryDocumentDao,
    private val shops: ShopScope
) : InventoryDocumentRepository {
    override suspend fun insertPurchase(purchase: Purchase, items: List<PurchaseItem>) =
        shops.mutate { local.insertPurchaseWithItems(purchase.toEntity().copy(tenant=TenantMetadata.assigned(it)), items.map { it.toEntity() }) }
    override fun observePurchaseItems(purchaseId: UUID) = shops.observe { local.observePurchaseItems(purchaseId, it) }.map { it.map { row -> row.toDomain() } }
    override fun observePurchasesForSupplier(supplierId: UUID) = shops.observe { local.observePurchasesForSupplier(supplierId, it) }.map { it.map { row -> row.toDomain() } }
    override suspend fun insertStockEntry(entry: StockEntry, items: List<StockEntryItem>) =
        shops.mutate { local.insertStockEntryWithItems(entry.toEntity().copy(tenant=TenantMetadata.assigned(it)), items.map { it.toEntity() }) }
    override fun observeStockEntryItems(entryId: UUID) = shops.observe { local.observeStockEntryItems(entryId, it) }.map { it.map { row -> row.toDomain() } }
    override suspend fun insertCount(count: InventoryCount, items: List<InventoryCountItem>) =
        shops.mutate { local.insertCountWithItems(count.toEntity().copy(tenant=TenantMetadata.assigned(it)), items.map { it.toEntity() }) }
    override fun observeCountItems(countId: UUID) = shops.observe { local.observeCountItems(countId, it) }.map { it.map { row -> row.toDomain() } }
    override fun observeCounts() = shops.observe(local::observeCounts).map { it.map { row -> row.toDomain() } }
    override suspend fun updateCountStatus(id: UUID, status: InventoryCountStatus, completedAt: Instant?) =
        shops.mutate { local.updateCountStatus(id, status, completedAt, it) }
}
