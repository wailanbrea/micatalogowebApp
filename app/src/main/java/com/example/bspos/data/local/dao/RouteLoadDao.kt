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
    @Query("SELECT * FROM route_loads WHERE route_id = :routeId ORDER BY date DESC, id DESC") abstract fun observeForRoute(routeId: UUID): Flow<List<RouteLoadEntity>>
    @Query("SELECT * FROM route_loads WHERE id = :id") abstract suspend fun findById(id: UUID): RouteLoadEntity?
    @Query("SELECT * FROM route_load_items WHERE route_load_id = :loadId ORDER BY product_id") abstract fun observeItems(loadId: UUID): Flow<List<RouteLoadItemEntity>>
    @Insert protected abstract suspend fun insertLoad(load: RouteLoadEntity)
    @Insert protected abstract suspend fun insertItems(items: List<RouteLoadItemEntity>)
    @Update protected abstract suspend fun updateLoad(load: RouteLoadEntity): Int

    @Transaction
    open suspend fun insertWithItems(load: RouteLoadEntity, items: List<RouteLoadItemEntity>) {
        require(load.status == RouteLoadStatus.OPEN) { "A new route load must be OPEN" }
        require(items.isNotEmpty()) { "A route load must include at least one product" }
        require(items.map { it.productId }.distinct().size == items.size) { "A product can only appear once" }
        items.forEach {
            require(it.routeLoadId == load.id) { "Route load item belongs to another document" }
            require(it.quantity > 0 && it.unitCostSnapshot >= 0) { "Invalid route load quantity or cost" }
        }
        insertLoad(load)
        insertItems(items)
    }

    @Transaction
    open suspend fun updateStatus(id: UUID, status: RouteLoadStatus) {
        val current = checkNotNull(findById(id)) { "Route load not found" }
        require((current.status == RouteLoadStatus.OPEN && (status == RouteLoadStatus.SETTLED || status == RouteLoadStatus.CANCELLED))) { "Invalid route load transition" }
        check(updateLoad(current.copy(status = status)) == 1)
    }
}
