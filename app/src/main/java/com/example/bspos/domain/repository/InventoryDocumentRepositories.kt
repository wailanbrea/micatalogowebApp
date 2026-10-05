package com.example.bspos.domain.repository

import com.example.bspos.domain.model.InventoryCount
import com.example.bspos.domain.model.InventoryCountItem
import com.example.bspos.domain.model.Purchase
import com.example.bspos.domain.model.PurchaseItem
import com.example.bspos.domain.model.StockEntry
import com.example.bspos.domain.model.StockEntryItem
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

interface InventoryDocumentRepository {
    suspend fun insertPurchase(purchase: Purchase, items: List<PurchaseItem>)
    fun observePurchaseItems(purchaseId: UUID): Flow<List<PurchaseItem>>
    fun observePurchasesForSupplier(supplierId: UUID): Flow<List<Purchase>>
    suspend fun insertStockEntry(entry: StockEntry, items: List<StockEntryItem>)
    fun observeStockEntryItems(entryId: UUID): Flow<List<StockEntryItem>>
    suspend fun insertCount(count: InventoryCount, items: List<InventoryCountItem>)
    fun observeCountItems(countId: UUID): Flow<List<InventoryCountItem>>
    fun observeCounts(): Flow<List<InventoryCount>>
    suspend fun updateCountStatus(id: UUID, status: com.example.bspos.domain.model.InventoryCountStatus, completedAt: Instant?)
}
