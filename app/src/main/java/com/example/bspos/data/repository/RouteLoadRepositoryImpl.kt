package com.example.bspos.data.repository

import com.example.bspos.data.local.dao.RouteLoadDao
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.domain.model.RouteLoad
import com.example.bspos.domain.model.RouteLoadItem
import com.example.bspos.domain.model.RouteLoadStatus
import com.example.bspos.domain.repository.RouteLoadRepository
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class RouteLoadRepositoryImpl @Inject constructor(private val local: RouteLoadDao, private val shops: ShopScope) : RouteLoadRepository {
    override suspend fun insert(load: RouteLoad, items: List<RouteLoadItem>) = shops.mutate { local.insertWithItems(load.toEntity().copy(tenant=TenantMetadata.assigned(it)), items.map { it.toEntity() }) }
    override fun observeForRoute(routeId: UUID) = shops.observe { local.observeForRoute(routeId, it) }.map { it.map { row -> row.toDomain() } }
    override fun observeItems(loadId: UUID) = shops.observe { local.observeItems(loadId, it) }.map { it.map { row -> row.toDomain() } }
    override suspend fun updateStatus(id: UUID, status: RouteLoadStatus) = shops.mutate { local.updateStatus(id, status, it) }
}
