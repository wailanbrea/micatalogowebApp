package com.example.bspos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.bspos.data.local.entity.RouteLoadEntity
import com.example.bspos.data.local.entity.RouteLoadItemEntity
import com.example.bspos.domain.model.RouteLoadStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
abstract class RouteLoadDao {
    @Query("SELECT * FROM route_loads WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND route_id = :routeId ORDER BY date DESC, id DESC") abstract fun observeForRoute(routeId: UUID, shopId: String? = null): Flow<List<RouteLoadEntity>>
    @Query("SELECT * FROM route_loads WHERE id = :id AND remote_shop_id=:shopId AND tenancy_status='assigned'") abstract suspend fun findById(id: UUID, shopId: String? = null): RouteLoadEntity?
    @Query("SELECT * FROM route_load_items WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND route_load_id = :loadId ORDER BY product_id") abstract fun observeItems(loadId: UUID, shopId: String? = null): Flow<List<RouteLoadItemEntity>>
    @Query("SELECT * FROM route_load_items WHERE remote_shop_id=:shop AND tenancy_status='assigned' AND route_load_id=:loadId ORDER BY product_id") abstract suspend fun itemsForSync(loadId: UUID, shop: String): List<RouteLoadItemEntity>
    @Insert protected abstract suspend fun insertLoad(load: RouteLoadEntity)
    @Insert protected abstract suspend fun insertItems(items: List<RouteLoadItemEntity>)
    @Update protected abstract suspend fun updateLoad(load: RouteLoadEntity): Int

    @Transaction
    open suspend fun applyRemote(load: RouteLoadEntity, items: List<RouteLoadItemEntity>) {
        val shop = checkNotNull(load.tenant.shopId)
        val previous = findById(load.id, shop)
        if (previous == null) {
            insertLoad(load); insertItems(items.map { it.copy(tenant = load.tenant) })
        } else {
            // Quantity and cost snapshots are historical, not overwritten by later receipts.
            val oldItems = itemsForSync(load.id, shop)
            require(oldItems.map { Triple(it.id, it.productId, it.quantity) }.toSet() == items.map { Triple(it.id, it.productId, it.quantity) }.toSet())
            require(previous.routeId == load.routeId && previous.createdAt == load.createdAt)
            check(updateLoad(previous.copy(status = load.status)) == 1)
        }
    }

    @Transaction
    open suspend fun insertWithItems(load: RouteLoadEntity, items: List<RouteLoadItemEntity>) {
        val shop=checkNotNull(load.tenant.shopId){"La carga necesita tienda"}
        require(items.all { it.tenant.shopId==null || it.tenant.shopId==shop })
        require(load.status == RouteLoadStatus.OPEN) { "A new route load must be OPEN" }
        require(items.isNotEmpty()) { "A route load must include at least one product" }
        require(items.map { it.productId }.distinct().size == items.size) { "A product can only appear once" }
        items.forEach {
            require(it.routeLoadId == load.id) { "Route load item belongs to another document" }
            require(it.quantity > 0 && it.unitCostSnapshot >= 0) { "Invalid route load quantity or cost" }
        }
        insertLoad(load)
        insertItems(items.map { it.copy(tenant=load.tenant) })
    }

    @Transaction
    open suspend fun updateStatus(id: UUID, status: RouteLoadStatus, shopId: String? = null) {
        val current = checkNotNull(findById(id, shopId)) { "Route load not found in shop" }
        require((current.status == RouteLoadStatus.OPEN && (status == RouteLoadStatus.SETTLED || status == RouteLoadStatus.CANCELLED))) { "Invalid route load transition" }
        check(updateLoad(current.copy(status = status)) == 1)
    }
}
