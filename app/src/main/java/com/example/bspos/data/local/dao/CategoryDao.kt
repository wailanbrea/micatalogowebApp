package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.bspos.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
abstract class CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted_at IS NULL ORDER BY sort_order, name, id")
    abstract fun observeAll(): Flow<List<CategoryEntity>>

    // Includes soft-deleted records for historical references and future synchronization.
    @Query("SELECT * FROM categories WHERE id = :id")
    abstract suspend fun findById(id: UUID): CategoryEntity?

    @Insert
    abstract suspend fun insert(category: CategoryEntity)

    @Update
    abstract suspend fun update(category: CategoryEntity): Int

    @Query("UPDATE categories SET deleted_at = :at, updated_at = :at WHERE id = :id AND deleted_at IS NULL")
    abstract suspend fun softDelete(id: UUID, at: Instant): Int

    @Query("UPDATE categories SET sort_order = :position, updated_at = :at WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun updateOrder(id: UUID, position: Int, at: Instant): Int

    /** Rolls back the entire reorder if a category disappeared or an ID was repeated. */
    @Transaction
    open suspend fun reorder(ids: List<UUID>, at: Instant) {
        require(ids.distinct().size == ids.size) { "Duplicate category IDs" }
        ids.forEachIndexed { position, id ->
            check(updateOrder(id, position, at) == 1) { "Category not found: $id" }
        }
    }
}
