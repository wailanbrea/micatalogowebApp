package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

enum class InventoryDocumentStatus { COMPLETED, CANCELLED }
enum class InventoryCountStatus { DRAFT, IN_PROGRESS, COMPLETED, CANCELLED }

data class StockEntry(
    val id: UUID, val documentNumber: String, val supplierId: UUID? = null, val date: Instant,
    val notes: String? = null, val status: InventoryDocumentStatus = InventoryDocumentStatus.COMPLETED,
    val createdAt: Instant
)

data class StockEntryItem(
    val id: UUID, val entryId: UUID, val productId: UUID, val quantity: Long,
    val unitCost: Long, val subtotal: Long
)

data class Purchase(
    val id: UUID, val supplierId: UUID, val documentNumber: String, val invoiceNumber: String? = null,
    val date: Instant, val subtotal: Long, val discount: Long = 0, val tax: Long = 0, val total: Long,
    val notes: String? = null, val status: InventoryDocumentStatus = InventoryDocumentStatus.COMPLETED,
    val createdAt: Instant, val updatedAt: Instant
)

data class PurchaseItem(
    val id: UUID, val purchaseId: UUID, val productId: UUID, val quantity: Long, val unitCost: Long,
    val discount: Long = 0, val tax: Long = 0, val subtotal: Long
)

data class InventoryCount(
    val id: UUID, val startedAt: Instant, val completedAt: Instant? = null, val notes: String? = null,
    val status: InventoryCountStatus = InventoryCountStatus.DRAFT
)

data class InventoryCountItem(
    val id: UUID, val countId: UUID, val productId: UUID, val systemQuantity: Long,
    val physicalQuantity: Long, val difference: Long
)
