package com.example.bspos.domain.repository

import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.Supplier
import com.example.bspos.domain.model.UnitOfMeasure
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/** Base persistence contract; business validation belongs to the corresponding use cases. */
interface CatalogRecordRepository<T> {
    /** Includes inactive records, excludes soft-deleted records. */
    fun observeAll(): Flow<List<T>>

    /** Includes deleted records so historical documents can still resolve their references. */
    suspend fun findById(id: UUID): T?
    suspend fun insert(record: T)
    suspend fun update(record: T): Boolean
    suspend fun softDelete(id: UUID, at: Instant): Boolean
}

interface CategoryRepository : CatalogRecordRepository<Category> {
    suspend fun reorder(ids: List<UUID>, at: Instant)
}

interface UnitOfMeasureRepository : CatalogRecordRepository<UnitOfMeasure>
interface ProductRepository : CatalogRecordRepository<Product>
interface SupplierRepository : CatalogRecordRepository<Supplier>
