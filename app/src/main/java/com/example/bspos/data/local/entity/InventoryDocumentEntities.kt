package com.example.bspos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bspos.domain.model.InventoryCountStatus
import com.example.bspos.domain.model.InventoryDocumentStatus
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "stock_entries",
    foreignKeys = [ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplier_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["supplier_id"], name = "idx_stock_entries_supplier_id"), Index(value = ["date"], name = "idx_stock_entries_date")]
)
data class StockEntryEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "document_number") val documentNumber: String,
    @ColumnInfo(name = "supplier_id") val supplierId: UUID? = null,
    val date: Instant,
    val notes: String? = null,
    @ColumnInfo(defaultValue = "'COMPLETED'") val status: InventoryDocumentStatus = InventoryDocumentStatus.COMPLETED,
    @ColumnInfo(name = "created_at") val createdAt: Instant
)

@Entity(
    tableName = "stock_entry_items",
    foreignKeys = [
        ForeignKey(entity = StockEntryEntity::class, parentColumns = ["id"], childColumns = ["entry_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["entry_id"], name = "idx_stock_entry_items_entry_id"), Index(value = ["product_id"], name = "idx_stock_entry_items_product_id")]
)
data class StockEntryItemEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "entry_id") val entryId: UUID,
    @ColumnInfo(name = "product_id") val productId: UUID,
    val quantity: Long,
    @ColumnInfo(name = "unit_cost") val unitCost: Long,
    val subtotal: Long
)

@Entity(
    tableName = "purchases",
    foreignKeys = [ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplier_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["supplier_id"], name = "idx_purchases_supplier_id"), Index(value = ["date"], name = "idx_purchases_date")]
)
data class PurchaseEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "supplier_id") val supplierId: UUID,
    @ColumnInfo(name = "document_number") val documentNumber: String,
    @ColumnInfo(name = "invoice_number") val invoiceNumber: String? = null,
    val date: Instant,
    val subtotal: Long,
    @ColumnInfo(defaultValue = "0") val discount: Long = 0,
    @ColumnInfo(defaultValue = "0") val tax: Long = 0,
    val total: Long,
    val notes: String? = null,
    @ColumnInfo(defaultValue = "'COMPLETED'") val status: InventoryDocumentStatus = InventoryDocumentStatus.COMPLETED,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(entity = PurchaseEntity::class, parentColumns = ["id"], childColumns = ["purchase_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["purchase_id"], name = "idx_purchase_items_purchase_id"), Index(value = ["product_id"], name = "idx_purchase_items_product_id")]
)
data class PurchaseItemEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "purchase_id") val purchaseId: UUID,
    @ColumnInfo(name = "product_id") val productId: UUID,
    val quantity: Long,
    @ColumnInfo(name = "unit_cost") val unitCost: Long,
    @ColumnInfo(defaultValue = "0") val discount: Long = 0,
    @ColumnInfo(defaultValue = "0") val tax: Long = 0,
    val subtotal: Long
)

@Entity(tableName = "inventory_counts")
data class InventoryCountEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    val notes: String? = null,
    @ColumnInfo(defaultValue = "'DRAFT'") val status: InventoryCountStatus = InventoryCountStatus.DRAFT
)

@Entity(
    tableName = "inventory_count_items",
    foreignKeys = [
        ForeignKey(entity = InventoryCountEntity::class, parentColumns = ["id"], childColumns = ["count_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"], onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["count_id"], name = "idx_count_items_count_id"), Index(value = ["product_id"], name = "idx_count_items_product_id"), Index(value = ["count_id", "product_id"], name = "idx_count_items_unique", unique = true)]
)
data class InventoryCountItemEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "count_id") val countId: UUID,
    @ColumnInfo(name = "product_id") val productId: UUID,
    @ColumnInfo(name = "system_quantity") val systemQuantity: Long,
    @ColumnInfo(name = "physical_quantity") val physicalQuantity: Long,
    val difference: Long
)
