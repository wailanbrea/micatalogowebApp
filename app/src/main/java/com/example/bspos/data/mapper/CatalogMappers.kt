package com.example.bspos.data.mapper

import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.SupplierEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.Supplier
import com.example.bspos.domain.model.UnitOfMeasure

fun CategoryEntity.toDomain() = Category(
    id = id, name = name, description = description, icon = icon, sortOrder = sortOrder,
    isActive = isActive, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun Category.toEntity() = CategoryEntity(
    id = id, name = name, description = description, icon = icon, sortOrder = sortOrder,
    isActive = isActive, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun UnitOfMeasureEntity.toDomain() = UnitOfMeasure(
    id = id, name = name, abbreviation = abbreviation, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun UnitOfMeasure.toEntity() = UnitOfMeasureEntity(
    id = id, name = name, abbreviation = abbreviation, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun ProductEntity.toDomain() = Product(
    id = id, name = name, internalCode = miCatalogoInternalCode ?: internalCode, barcode = barcode,
    categoryId = categoryId, unitId = unitId, description = description,
    salePrice = salePrice, wholesalePrice = wholesalePrice, averageCost = averageCost,
    lastPurchaseCost = lastPurchaseCost, minimumStock = minimumStock, imagePath = imagePath,
    thumbnailPath = thumbnailPath, isActive = isActive, tracksExpiration = tracksExpiration,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    remoteShopId = miCatalogoShopId, remoteProductId = miCatalogoProductId, remoteSaleUnit = miCatalogoSaleUnit
)

fun Product.toEntity() = ProductEntity(
    id = id, name = name, internalCode = if (remoteShopId != null && remoteProductId != null)
        com.example.bspos.data.micatalogo.MiCatalogoImportMapper.internalCode(remoteShopId, remoteProductId) else internalCode, barcode = barcode,
    categoryId = categoryId, unitId = unitId, description = description,
    salePrice = salePrice, wholesalePrice = wholesalePrice, averageCost = averageCost,
    lastPurchaseCost = lastPurchaseCost, minimumStock = minimumStock, imagePath = imagePath,
    thumbnailPath = thumbnailPath, isActive = isActive, tracksExpiration = tracksExpiration,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
    miCatalogoShopId = remoteShopId, miCatalogoProductId = remoteProductId,
    miCatalogoInternalCode = if (remoteProductId != null) internalCode else null
)

fun SupplierEntity.toDomain() = Supplier(
    id = id, name = name, contactName = contactName, phone = phone, email = email,
    address = address, taxId = taxId, notes = notes, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)

fun Supplier.toEntity() = SupplierEntity(
    id = id, name = name, contactName = contactName, phone = phone, email = email,
    address = address, taxId = taxId, notes = notes, isActive = isActive,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt
)
