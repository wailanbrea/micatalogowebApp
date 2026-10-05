package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.CommercialRoute
import com.example.bspos.domain.model.RouteCustomer
import com.example.bspos.domain.repository.RouteRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class RouteInput(val name:String,val code:String,val description:String?=null)
class RouteUseCases @Inject constructor(private val repository:RouteRepository){fun observe():Flow<List<CommercialRoute>> = repository.observeAll();fun observeCustomers(routeId:UUID):Flow<List<RouteCustomer>> = repository.observeCustomers(routeId);suspend fun create(input:RouteInput):CommercialRoute{val now=Instant.now();val route=CommercialRoute(UUID.randomUUID(),input.name.clean("Route name"),input.code.clean("Route code").uppercase(),input.description.cleanOptional(),createdAt=now,updatedAt=now);repository.insert(route);return route};suspend fun update(current:CommercialRoute,input:RouteInput,isActive:Boolean=current.isActive):Boolean=repository.update(current.copy(name=input.name.clean("Route name"),code=input.code.clean("Route code").uppercase(),description=input.description.cleanOptional(),isActive=isActive,updatedAt=Instant.now()));suspend fun assign(routeId:UUID,customerId:UUID,visitOrder:Int=0){require(visitOrder>=0);repository.assign(RouteCustomer(routeId,customerId,visitOrder))};suspend fun unassign(customerId:UUID):Boolean=repository.unassign(customerId);suspend fun reorder(routeId:UUID,customerIds:List<UUID>){require(customerIds.distinct().size==customerIds.size);repository.reorder(routeId,customerIds)}}
