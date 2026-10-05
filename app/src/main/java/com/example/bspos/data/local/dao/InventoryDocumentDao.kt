package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.bspos.data.local.entity.InventoryCountEntity
import com.example.bspos.data.local.entity.InventoryCountItemEntity
import com.example.bspos.data.local.entity.PurchaseEntity
import com.example.bspos.data.local.entity.PurchaseItemEntity
import com.example.bspos.data.local.entity.StockEntryEntity
import com.example.bspos.data.local.entity.StockEntryItemEntity
import com.example.bspos.domain.model.InventoryCountStatus
import com.example.bspos.domain.model.InventoryDocumentStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
abstract class InventoryDocumentDao {
    @Query("SELECT * FROM purchases WHERE supplier_id = :supplierId ORDER BY date DESC, id DESC")
    abstract fun observePurchasesForSupplier(supplierId: UUID): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    abstract suspend fun findPurchase(id: UUID): PurchaseEntity?

    @Query("SELECT * FROM purchase_items WHERE purchase_id = :purchaseId ORDER BY id")
    abstract fun observePurchaseItems(purchaseId: UUID): Flow<List<PurchaseItemEntity>>

    @Insert protected abstract suspend fun insertPurchase(purchase: PurchaseEntity)
    @Insert protected abstract suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)
    @Update protected abstract suspend fun updatePurchase(purchase: PurchaseEntity): Int

    @Transaction
    open suspend fun insertPurchaseWithItems(purchase: PurchaseEntity, items: List<PurchaseItemEntity>) {
        validatePurchase(purchase, items)
        insertPurchase(purchase)
        insertPurchaseItems(items)
    }

    @Query("SELECT * FROM stock_entries WHERE id = :id")
    abstract suspend fun findStockEntry(id: UUID): StockEntryEntity?

    @Query("SELECT * FROM stock_entries WHERE supplier_id = :supplierId ORDER BY date DESC, id DESC")
    abstract fun observeStockEntriesForSupplier(supplierId: UUID): Flow<List<StockEntryEntity>>

    @Query("SELECT * FROM stock_entry_items WHERE entry_id = :entryId ORDER BY id")
    abstract fun observeStockEntryItems(entryId: UUID): Flow<List<StockEntryItemEntity>>

    @Insert protected abstract suspend fun insertStockEntry(entry: StockEntryEntity)
    @Insert protected abstract suspend fun insertStockEntryItems(items: List<StockEntryItemEntity>)

    @Transaction
    open suspend fun insertStockEntryWithItems(entry: StockEntryEntity, items: List<StockEntryItemEntity>) {
        require(entry.documentNumber.isNotBlank()) { "Document number is required" }
        validateEntryItems(entry.id, items)
        insertStockEntry(entry)
        insertStockEntryItems(items)
    }

    @Query("SELECT * FROM inventory_counts ORDER BY started_at DESC, id DESC")
    abstract fun observeCounts(): Flow<List<InventoryCountEntity>>

    @Query("SELECT * FROM inventory_counts WHERE id = :id")
    abstract suspend fun findCount(id: UUID): InventoryCountEntity?

    @Query("SELECT * FROM inventory_count_items WHERE count_id = :countId ORDER BY product_id")
    abstract fun observeCountItems(countId: UUID): Flow<List<InventoryCountItemEntity>>

    @Insert protected abstract suspend fun insertCount(count: InventoryCountEntity)
    @Insert protected abstract suspend fun insertCountItems(items: List<InventoryCountItemEntity>)
    @Update protected abstract suspend fun updateCount(count: InventoryCountEntity): Int

    @Transaction
    open suspend fun insertCountWithItems(count: InventoryCountEntity, items: List<InventoryCountItemEntity>) {
        require(count.status == InventoryCountStatus.DRAFT) { "A new count must start as DRAFT" }
        require(count.completedAt == null) { "A draft count cannot be completed" }
        require(items.isNotEmpty()) { "A count must include at least one product" }
        require(items.map { it.productId }.distinct().size == items.size) { "A product can only be counted once" }
        items.forEach {
            require(it.countId == count.id) { "Count item belongs to another document" }
            require(Math.subtractExact(it.physicalQuantity, it.systemQuantity) == it.difference) { "Incorrect count difference" }
        }
        insertCount(count)
        insertCountItems(items)
    }

    @Transaction
    open suspend fun updateCountStatus(id: UUID, status: InventoryCountStatus, completedAt: Instant?) {
        val current = checkNotNull(findCount(id)) { "Inventory count not found" }
        require(isValidCountTransition(current.status, status)) { "Invalid count transition" }
        require((status == InventoryCountStatus.COMPLETED) == (completedAt != null)) { "Completion timestamp does not match status" }
        check(updateCount(current.copy(status = status, completedAt = completedAt)) == 1)
    }

    private fun validatePurchase(purchase: PurchaseEntity, items: List<PurchaseItemEntity>) {
        require(purchase.documentNumber.isNotBlank()) { "Document number is required" }
        require(purchase.status == InventoryDocumentStatus.COMPLETED) { "Only completed purchases can be created" }
        require(purchase.discount >= 0 && purchase.tax >= 0) { "Discount and tax cannot be negative" }
        require(items.isNotEmpty()) { "A purchase must include at least one product" }
        require(items.map { it.productId }.distinct().size == items.size) { "A product can only appear once" }
        require(items.all { it.purchaseId == purchase.id }) { "Purchase item belongs to another document" }
        val computedSubtotal = items.sumOf { validatePurchaseItem(it); it.subtotal }
        require(computedSubtotal == purchase.subtotal) { "Purchase subtotal does not equal line subtotals" }
        require(Math.addExact(Math.subtractExact(purchase.subtotal, purchase.discount), purchase.tax) == purchase.total) { "Incorrect purchase total" }
    }

    private fun validateEntryItems(entryId: UUID, items: List<StockEntryItemEntity>) {
        require(items.isNotEmpty()) { "A stock entry must include at least one product" }
        require(items.map { it.productId }.distinct().size == items.size) { "A product can only appear once" }
        items.forEach {
            require(it.entryId == entryId) { "Stock entry item belongs to another document" }
            require(it.quantity > 0 && it.unitCost >= 0) { "Invalid stock entry quantity or cost" }
            require(Math.multiplyExact(it.quantity, it.unitCost) == it.subtotal) { "Incorrect stock entry subtotal" }
        }
    }

    private fun validatePurchaseItem(item: PurchaseItemEntity) {
        require(item.quantity > 0 && item.unitCost >= 0 && item.discount >= 0 && item.tax >= 0) { "Invalid purchase line values" }
        require(Math.addExact(Math.subtractExact(Math.multiplyExact(item.quantity, item.unitCost), item.discount), item.tax) == item.subtotal) { "Incorrect purchase line subtotal" }
    }

    private fun isValidCountTransition(from: InventoryCountStatus, to: InventoryCountStatus): Boolean = when (from) {
        InventoryCountStatus.DRAFT -> to == InventoryCountStatus.IN_PROGRESS || to == InventoryCountStatus.CANCELLED
        InventoryCountStatus.IN_PROGRESS -> to == InventoryCountStatus.COMPLETED || to == InventoryCountStatus.CANCELLED
        InventoryCountStatus.COMPLETED, InventoryCountStatus.CANCELLED -> false
    }
}
