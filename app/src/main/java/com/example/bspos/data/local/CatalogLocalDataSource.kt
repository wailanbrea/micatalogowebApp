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
    fun observeCategories(shopId: String? = null) = categories.observeAll(shopId)
    suspend fun findCategory(id: UUID, shopId: String? = null) = categories.findById(id, shopId)
    suspend fun insert(category: CategoryEntity) = categories.insert(category)
    suspend fun update(category: CategoryEntity) = findCategory(category.id, category.tenant.shopId) != null && categories.update(category) == 1
    suspend fun deleteCategory(id: UUID, at: Instant, shopId: String? = null) = categories.softDelete(id, at, shopId) == 1
    suspend fun reorderCategories(ids: List<UUID>, at: Instant, shopId: String? = null) = categories.reorder(ids, at, shopId)

    fun observeUnits(shopId: String? = null) = units.observeAll(shopId)
    suspend fun findUnit(id: UUID, shopId: String? = null) = units.findById(id, shopId)
    suspend fun insert(unit: UnitOfMeasureEntity) = units.insert(unit)
    suspend fun update(unit: UnitOfMeasureEntity) = findUnit(unit.id, unit.tenant.shopId) != null && units.update(unit) == 1
    suspend fun deleteUnit(id: UUID, at: Instant, shopId: String? = null) = units.softDelete(id, at, shopId) == 1

    fun observeProducts(shopId: String? = null) = products.observeAll(shopId)
    fun observeProductsForShop(shopId: String) = products.observeForShop(shopId)
    suspend fun findProduct(id: UUID, shopId: String? = null) = products.findById(id, shopId)
    suspend fun insert(product: ProductEntity) = products.insert(product)
    suspend fun update(product: ProductEntity): Boolean {
        val existing = products.findById(product.id, product.tenant.shopId) ?: return false
        return products.update(product.copy(miCatalogoShopId = existing.miCatalogoShopId,
            miCatalogoProductId = existing.miCatalogoProductId,
            miCatalogoSourceProductId = product.miCatalogoSourceProductId ?: existing.miCatalogoSourceProductId,
            miCatalogoVolumeMl = if (product.miCatalogoSaleUnit != null) product.miCatalogoVolumeMl else existing.miCatalogoVolumeMl,
            miCatalogoAvailableMl = existing.miCatalogoAvailableMl,
            miCatalogoOpenedBottles = existing.miCatalogoOpenedBottles,
            miCatalogoSaleUnit = product.miCatalogoSaleUnit ?: existing.miCatalogoSaleUnit, tenant=existing.tenant)) == 1
    }
    suspend fun deleteProduct(id: UUID, at: Instant, shopId: String? = null) = products.softDelete(id, at, shopId) == 1

    fun observeSuppliers(shopId: String? = null) = suppliers.observeAll(shopId)
    suspend fun findSupplier(id: UUID, shopId: String? = null) = suppliers.findById(id, shopId)
    suspend fun insert(supplier: SupplierEntity) = suppliers.insert(supplier)
    suspend fun update(supplier: SupplierEntity) = findSupplier(supplier.id, supplier.tenant.shopId) != null && suppliers.update(supplier) == 1
    suspend fun deleteSupplier(id: UUID, at: Instant, shopId: String? = null) = suppliers.softDelete(id, at, shopId) == 1
}
