package com.example.bspos.data.repository

import com.example.bspos.data.local.CatalogLocalDataSource
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.Supplier
import com.example.bspos.domain.model.UnitOfMeasure
import com.example.bspos.domain.repository.CategoryRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.SupplierRepository
import com.example.bspos.domain.repository.UnitOfMeasureRepository
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val local: CatalogLocalDataSource,
    private val shops: ShopScope
) : CategoryRepository {
    override fun observeAll() = shops.observe(local::observeCategories).map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findCategory(id, shops.current())?.toDomain()
    override suspend fun insert(record: Category) = shops.mutate { local.insert(record.toEntity().copy(tenant=TenantMetadata.assigned(it))) }
    override suspend fun update(record: Category) = shops.mutate { local.update(record.toEntity().copy(tenant=TenantMetadata.assigned(it))) }
    override suspend fun softDelete(id: UUID, at: Instant) = shops.mutate { local.deleteCategory(id, at, it) }
    override suspend fun reorder(ids: List<UUID>, at: Instant) = shops.mutate { local.reorderCategories(ids, at, it) }
}

class UnitOfMeasureRepositoryImpl @Inject constructor(
    private val local: CatalogLocalDataSource,
    private val shops: ShopScope
) : UnitOfMeasureRepository {
    override fun observeAll() = shops.observe(local::observeUnits).map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findUnit(id, shops.current())?.toDomain()
    override suspend fun insert(record: UnitOfMeasure) = shops.mutate { local.insert(record.toEntity().copy(tenant=TenantMetadata.assigned(it))) }
    override suspend fun update(record: UnitOfMeasure) = shops.mutate { local.update(record.toEntity().copy(tenant=TenantMetadata.assigned(it))) }
    override suspend fun softDelete(id: UUID, at: Instant) = shops.mutate { local.deleteUnit(id, at, it) }
}

class ProductRepositoryImpl @Inject constructor(
    private val local: CatalogLocalDataSource,
    private val sync: com.example.bspos.data.micatalogo.RemoteMutationRecorder,
    private val shops: ShopScope
) : ProductRepository {
    override fun observeAll() = shops.observe(local::observeProducts).map { rows -> rows.map { it.toDomain() } }
    override fun observeForShop(shopId: String) = shops.observe { active -> local.observeProductsForShop(if (active==shopId) shopId else "") }.map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findProduct(id, shops.current())?.toDomain()
    override suspend fun insert(record: Product) {
        shops.mutate { shop ->
            require(record.remoteShopId == null || record.remoteShopId == shop) { "El producto pertenece a otra tienda" }
            sync.transaction {
                val entity = record.toEntity().copy(miCatalogoShopId=shop,
                    miCatalogoProductId=record.remoteProductId ?: com.example.bspos.data.micatalogo.RemoteMutationRecorder.productId(record.id),
                    tenant=TenantMetadata.assigned(shop))
                local.insert(entity); sync.product(entity)
            }
        }
        sync.wake()
    }
    override suspend fun update(record: Product): Boolean {
        val changed = shops.mutate { shop -> sync.transaction {
            require(record.remoteShopId == null || record.remoteShopId == shop) { "El producto pertenece a otra tienda" }
            val previous = local.findProduct(record.id, shop) ?: return@transaction false
            val updated = local.update(record.toEntity().copy(tenant=previous.tenant))
            if (updated) sync.product(checkNotNull(local.findProduct(record.id, shop)), previous)
            updated
        }
        }
        if (changed && record.remoteShopId != null) sync.wake()
        return changed
    }
    override suspend fun softDelete(id: UUID, at: Instant): Boolean {
        val changed = shops.mutate { shop -> sync.transaction {
            val previous = local.findProduct(id, shop) ?: return@transaction false
            val deleted = local.deleteProduct(id, at, shop)
            if (deleted) sync.archive(previous, at)
            deleted
        }
        }
        if (changed) sync.wake()
        return changed
    }
}

class SupplierRepositoryImpl @Inject constructor(
    private val local: CatalogLocalDataSource,
    private val shops: ShopScope
) : SupplierRepository {
    override fun observeAll() = shops.observe(local::observeSuppliers).map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findSupplier(id, shops.current())?.toDomain()
    override suspend fun insert(record: Supplier) = shops.mutate { local.insert(record.toEntity().copy(tenant=TenantMetadata.assigned(it))) }
    override suspend fun update(record: Supplier) = shops.mutate { local.update(record.toEntity().copy(tenant=TenantMetadata.assigned(it))) }
    override suspend fun softDelete(id: UUID, at: Instant) = shops.mutate { local.deleteSupplier(id, at, it) }
}
