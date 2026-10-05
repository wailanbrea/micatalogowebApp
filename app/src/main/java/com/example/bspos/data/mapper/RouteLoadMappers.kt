package com.example.bspos.data.mapper

import com.example.bspos.data.local.entity.RouteLoadEntity
import com.example.bspos.data.local.entity.RouteLoadItemEntity
import com.example.bspos.domain.model.RouteLoad
import com.example.bspos.domain.model.RouteLoadItem

fun RouteLoadEntity.toDomain() = RouteLoad(id, routeId, date, status, notes, createdAt)
fun RouteLoad.toEntity() = RouteLoadEntity(id, routeId, date, status, notes, createdAt)
fun RouteLoadItemEntity.toDomain() = RouteLoadItem(id, routeLoadId, productId, quantity, unitCostSnapshot)
fun RouteLoadItem.toEntity() = RouteLoadItemEntity(id, routeLoadId, productId, quantity, unitCostSnapshot)
