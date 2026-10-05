package com.example.bspos.data.repository

import com.example.bspos.data.local.dao.RouteLoadDao
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.mapper.toEntity
import com.example.bspos.domain.model.RouteLoad
import com.example.bspos.domain.model.RouteLoadItem
import com.example.bspos.domain.model.RouteLoadStatus
import com.example.bspos.domain.repository.RouteLoadRepository
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class RouteLoadRepositoryImpl @Inject constructor(private val local: RouteLoadDao) : RouteLoadRepository {
    override suspend fun insert(load: RouteLoad, items: List<RouteLoadItem>) = local.insertWithItems(load.toEntity(), items.map { it.toEntity() })
    override fun observeForRoute(routeId: UUID) = local.observeForRoute(routeId).map { it.map { row -> row.toDomain() } }
    override fun observeItems(loadId: UUID) = local.observeItems(loadId).map { it.map { row -> row.toDomain() } }
    override suspend fun updateStatus(id: UUID, status: RouteLoadStatus) = local.updateStatus(id, status)
}
