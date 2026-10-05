package com.example.bspos.data.mapper

import com.example.bspos.data.local.entity.InventoryAdjustmentReasonEntity
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.InventoryStockEntity
import com.example.bspos.domain.model.InventoryAdjustmentReason
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.InventoryMovement
import com.example.bspos.domain.model.InventoryStock

fun InventoryAdjustmentReasonEntity.toDomain() = InventoryAdjustmentReason(
    id = id, name = name, direction = direction, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun InventoryAdjustmentReason.toEntity() = InventoryAdjustmentReasonEntity(
    id = id, name = name, direction = direction, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun InventoryStockEntity.toDomain() = InventoryStock(
    id = id, productId = productId, location = InventoryLocation(locationType, locationId),
    quantity = quantity, reservedQuantity = reservedQuantity, updatedAt = updatedAt
)

fun InventoryStock.toEntity() = InventoryStockEntity(
    id = id, productId = productId, locationType = location.type, locationId = location.id,
    quantity = quantity, reservedQuantity = reservedQuantity, updatedAt = updatedAt
)

fun InventoryMovementEntity.toDomain() = InventoryMovement(
    id = id, productId = productId, location = InventoryLocation(locationType, locationId),
    type = movementType, quantity = quantity, previousQuantity = previousQuantity, newQuantity = newQuantity,
    unitCost = unitCost, totalCost = totalCost, referenceType = referenceType, referenceId = referenceId,
    reasonId = reasonId, notes = notes, createdAt = createdAt, createdBy = createdBy
)

fun InventoryMovement.toEntity() = InventoryMovementEntity(
    id = id, productId = productId, locationType = location.type, locationId = location.id,
    movementType = type, quantity = quantity, previousQuantity = previousQuantity, newQuantity = newQuantity,
    unitCost = unitCost, totalCost = totalCost, referenceType = referenceType, referenceId = referenceId,
    reasonId = reasonId, notes = notes, createdAt = createdAt, createdBy = createdBy
)
