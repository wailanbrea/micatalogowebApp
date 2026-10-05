package com.example.bspos.domain.repository

import com.example.bspos.domain.model.RouteLoad
import com.example.bspos.domain.model.RouteLoadItem
import com.example.bspos.domain.model.RouteLoadStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface RouteLoadRepository {
    suspend fun insert(load: RouteLoad, items: List<RouteLoadItem>)
    fun observeForRoute(routeId: UUID): Flow<List<RouteLoad>>
    fun observeItems(loadId: UUID): Flow<List<RouteLoadItem>>
    suspend fun updateStatus(id: UUID, status: RouteLoadStatus)
}
