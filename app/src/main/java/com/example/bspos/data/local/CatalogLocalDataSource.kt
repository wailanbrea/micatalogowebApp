package com.example.bspos.data.local

import com.example.bspos.data.local.dao.CategoryDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.dao.SupplierDao
import com.example.bspos.data.local.dao.UnitOfMeasureDao
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.SupplierEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CatalogLocalDataSource @Inject constructor(
    private val categories: CategoryDao,
    private val units: UnitOfMeasureDao,
    private val products: ProductDao,
    private val suppliers: SupplierDao
) {
    fun observeCategories() = categories.observeAll()
    suspend fun findCategory(id: UUID) = categories.findById(id)
    suspend fun insert(category: CategoryEntity) = categories.insert(category)
    suspend fun update(category: CategoryEntity) = categories.update(category) == 1
    suspend fun deleteCategory(id: UUID, at: Instant) = categories.softDelete(id, at) == 1
    suspend fun reorderCategories(ids: List<UUID>, at: Instant) = categories.reorder(ids, at)

    fun observeUnits() = units.observeAll()
    suspend fun findUnit(id: UUID) = units.findById(id)
    suspend fun insert(unit: UnitOfMeasureEntity) = units.insert(unit)
    suspend fun update(unit: UnitOfMeasureEntity) = units.update(unit) == 1
    suspend fun deleteUnit(id: UUID, at: Instant) = units.softDelete(id, at) == 1

    fun observeProducts() = products.observeAll()
    suspend fun findProduct(id: UUID) = products.findById(id)
    suspend fun insert(product: ProductEntity) = products.insert(product)
    suspend fun update(product: ProductEntity): Boolean {
        val existing = products.findById(product.id) ?: return false
        return products.update(product.copy(miCatalogoShopId = existing.miCatalogoShopId,
            miCatalogoProductId = existing.miCatalogoProductId,
            miCatalogoSourceProductId = existing.miCatalogoSourceProductId,
            miCatalogoVolumeMl = existing.miCatalogoVolumeMl,
            miCatalogoAvailableMl = existing.miCatalogoAvailableMl,
            miCatalogoSaleUnit = existing.miCatalogoSaleUnit)) == 1
    }
    suspend fun deleteProduct(id: UUID, at: Instant) = products.softDelete(id, at) == 1

    fun observeSuppliers() = suppliers.observeAll()
    suspend fun findSupplier(id: UUID) = suppliers.findById(id)
    suspend fun insert(supplier: SupplierEntity) = suppliers.insert(supplier)
    suspend fun update(supplier: SupplierEntity) = suppliers.update(supplier) == 1
    suspend fun deleteSupplier(id: UUID, at: Instant) = suppliers.softDelete(id, at) == 1
}
