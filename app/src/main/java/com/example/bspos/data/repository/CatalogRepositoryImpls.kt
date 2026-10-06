package com.example.bspos.data.repository

import com.example.bspos.data.local.CatalogLocalDataSource
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
    private val local: CatalogLocalDataSource
) : CategoryRepository {
    override fun observeAll() = local.observeCategories().map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findCategory(id)?.toDomain()
    override suspend fun insert(record: Category) = local.insert(record.toEntity())
    override suspend fun update(record: Category) = local.update(record.toEntity())
    override suspend fun softDelete(id: UUID, at: Instant) = local.deleteCategory(id, at)
    override suspend fun reorder(ids: List<UUID>, at: Instant) = local.reorderCategories(ids, at)
}

class UnitOfMeasureRepositoryImpl @Inject constructor(
    private val local: CatalogLocalDataSource
) : UnitOfMeasureRepository {
    override fun observeAll() = local.observeUnits().map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findUnit(id)?.toDomain()
    override suspend fun insert(record: UnitOfMeasure) = local.insert(record.toEntity())
    override suspend fun update(record: UnitOfMeasure) = local.update(record.toEntity())
    override suspend fun softDelete(id: UUID, at: Instant) = local.deleteUnit(id, at)
}

class ProductRepositoryImpl @Inject constructor(
    private val local: CatalogLocalDataSource,
    private val sync: com.example.bspos.data.micatalogo.RemoteMutationRecorder
) : ProductRepository {
    override fun observeAll() = local.observeProducts().map { rows -> rows.map { it.toDomain() } }
    override fun observeForShop(shopId: String) = local.observeProductsForShop(shopId).map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findProduct(id)?.toDomain()
    override suspend fun insert(record: Product) {
        sync.transaction { val entity = record.toEntity(); local.insert(entity); sync.product(entity) }
        if (record.remoteShopId != null) sync.wake()
    }
    override suspend fun update(record: Product): Boolean {
        val changed = sync.transaction {
            val previous = local.findProduct(record.id) ?: return@transaction false
            val updated = local.update(record.toEntity())
            if (updated) sync.product(checkNotNull(local.findProduct(record.id)), previous)
            updated
        }
        if (changed && record.remoteShopId != null) sync.wake()
        return changed
    }
    override suspend fun softDelete(id: UUID, at: Instant): Boolean {
        val changed = sync.transaction {
            val previous = local.findProduct(id) ?: return@transaction false
            val deleted = local.deleteProduct(id, at)
            if (deleted) sync.archive(previous, at)
            deleted
        }
        if (changed) sync.wake()
        return changed
    }
}

class SupplierRepositoryImpl @Inject constructor(
    private val local: CatalogLocalDataSource
) : SupplierRepository {
    override fun observeAll() = local.observeSuppliers().map { rows -> rows.map { it.toDomain() } }
    override suspend fun findById(id: UUID) = local.findSupplier(id)?.toDomain()
    override suspend fun insert(record: Supplier) = local.insert(record.toEntity())
    override suspend fun update(record: Supplier) = local.update(record.toEntity())
    override suspend fun softDelete(id: UUID, at: Instant) = local.deleteSupplier(id, at)
}
