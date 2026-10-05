package com.example.bspos.data.mapper

import com.example.bspos.data.local.entity.InventoryCountEntity
import com.example.bspos.data.local.entity.InventoryCountItemEntity
import com.example.bspos.data.local.entity.PurchaseEntity
import com.example.bspos.data.local.entity.PurchaseItemEntity
import com.example.bspos.data.local.entity.StockEntryEntity
import com.example.bspos.data.local.entity.StockEntryItemEntity
import com.example.bspos.domain.model.InventoryCount
import com.example.bspos.domain.model.InventoryCountItem
import com.example.bspos.domain.model.Purchase
import com.example.bspos.domain.model.PurchaseItem
import com.example.bspos.domain.model.StockEntry
import com.example.bspos.domain.model.StockEntryItem

fun StockEntryEntity.toDomain() = StockEntry(id, documentNumber, supplierId, date, notes, status, createdAt)
fun StockEntry.toEntity() = StockEntryEntity(id, documentNumber, supplierId, date, notes, status, createdAt)
fun StockEntryItemEntity.toDomain() = StockEntryItem(id, entryId, productId, quantity, unitCost, subtotal)
fun StockEntryItem.toEntity() = StockEntryItemEntity(id, entryId, productId, quantity, unitCost, subtotal)
fun PurchaseEntity.toDomain() = Purchase(id, supplierId, documentNumber, invoiceNumber, date, subtotal, discount, tax, total, notes, status, createdAt, updatedAt)
fun Purchase.toEntity() = PurchaseEntity(id, supplierId, documentNumber, invoiceNumber, date, subtotal, discount, tax, total, notes, status, createdAt, updatedAt)
fun PurchaseItemEntity.toDomain() = PurchaseItem(id, purchaseId, productId, quantity, unitCost, discount, tax, subtotal)
fun PurchaseItem.toEntity() = PurchaseItemEntity(id, purchaseId, productId, quantity, unitCost, discount, tax, subtotal)
fun InventoryCountEntity.toDomain() = InventoryCount(id, startedAt, completedAt, notes, status)
fun InventoryCount.toEntity() = InventoryCountEntity(id, startedAt, completedAt, notes, status)
fun InventoryCountItemEntity.toDomain() = InventoryCountItem(id, countId, productId, systemQuantity, physicalQuantity, difference)
fun InventoryCountItem.toEntity() = InventoryCountItemEntity(id, countId, productId, systemQuantity, physicalQuantity, difference)
