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
    @Query("SELECT * FROM products WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL ORDER BY name, id")
    fun observeAll(shopId: String? = null): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE deleted_at IS NULL AND remote_shop_id=:shopId AND tenancy_status='assigned' ORDER BY name, id")
    fun observeForShop(shopId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id AND remote_shop_id=:shopId AND tenancy_status='assigned'")
    suspend fun findById(id: UUID, shopId: String? = null): ProductEntity?

    @Query("SELECT * FROM products WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL")
    suspend fun findForShop(shopId: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND micatalogo_product_id = :remoteId LIMIT 1")
    suspend fun findRemote(shopId: String, remoteId: String): ProductEntity?

    // Only the synchronizer may reconcile a single record with a verified remote identity.
    @Query("SELECT * FROM products WHERE micatalogo_shop_id=:shopId AND micatalogo_product_id=:remoteId AND (remote_shop_id=:shopId OR remote_shop_id IS NULL)")
    suspend fun remoteReconciliationCandidates(shopId: String, remoteId: String): List<ProductEntity>

    @Insert
    suspend fun insert(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity): Int

    @Query("UPDATE products SET deleted_at = :at, updated_at = :at WHERE id = :id AND remote_shop_id=:shopId AND tenancy_status='assigned' AND deleted_at IS NULL")
    suspend fun softDelete(id: UUID, at: Instant, shopId: String? = null): Int

    @Query("UPDATE products SET deleted_at = :at, updated_at = :at WHERE micatalogo_shop_id IS NOT NULL AND deleted_at IS NULL AND micatalogo_shop_id NOT IN (:shopIds)")
    suspend fun archiveRemoteProductsExcept(shopIds: List<String>, at: Instant): Int

    @Query("UPDATE products SET deleted_at = :at, updated_at = :at WHERE micatalogo_shop_id IS NOT NULL AND deleted_at IS NULL")
    suspend fun archiveAllRemoteProducts(at: Instant): Int
}
