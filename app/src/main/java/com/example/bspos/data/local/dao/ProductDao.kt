package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.bspos.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE deleted_at IS NULL ORDER BY name, id")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE deleted_at IS NULL AND (micatalogo_shop_id = :shopId OR micatalogo_shop_id IS NULL) ORDER BY name, id")
    fun observeForShop(shopId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: UUID): ProductEntity?

    @Query("SELECT * FROM products WHERE micatalogo_shop_id = :shopId AND deleted_at IS NULL")
    suspend fun findForShop(shopId: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE micatalogo_shop_id = :shopId AND micatalogo_product_id = :remoteId LIMIT 1")
    suspend fun findRemote(shopId: String, remoteId: String): ProductEntity?

    @Insert
    suspend fun insert(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity): Int

    @Query("UPDATE products SET deleted_at = :at, updated_at = :at WHERE id = :id AND deleted_at IS NULL")
    suspend fun softDelete(id: UUID, at: Instant): Int

    @Query("UPDATE products SET deleted_at = :at, updated_at = :at WHERE micatalogo_shop_id IS NOT NULL AND deleted_at IS NULL AND micatalogo_shop_id NOT IN (:shopIds)")
    suspend fun archiveRemoteProductsExcept(shopIds: List<String>, at: Instant): Int

    @Query("UPDATE products SET deleted_at = :at, updated_at = :at WHERE micatalogo_shop_id IS NOT NULL AND deleted_at IS NULL")
    suspend fun archiveAllRemoteProducts(at: Instant): Int
}
