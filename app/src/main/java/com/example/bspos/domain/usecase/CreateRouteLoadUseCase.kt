package com.example.bspos.domain.usecase

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.RouteLoadRepository
import com.example.bspos.domain.repository.RouteRepository
import com.example.bspos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class RouteLoadLineInput(val productId:UUID,val quantity:Long)
data class CreateRouteLoadRequest(val routeId:UUID,val date:Instant=Instant.now(),val lines:List<RouteLoadLineInput>,val notes:String?=null)
class CreateRouteLoadUseCase @Inject constructor(private val transactor:AppDatabaseTransactor,private val loads:RouteLoadRepository,private val routes:RouteRepository,private val products:ProductRepository,private val inventory:InventoryRepository,private val settings:SettingsRepository){suspend operator fun invoke(request:CreateRouteLoadRequest):RouteLoad=transactor.runInTransaction{require(request.lines.isNotEmpty()&&request.lines.map{it.productId}.distinct().size==request.lines.size);val route=checkNotNull(routes.findById(request.routeId)){"Route not found"};require(route.isActive);val loadId=UUID.randomUUID();val load=RouteLoad(loadId,route.id,request.date,notes=request.notes,createdAt=request.date);val routeLocation=InventoryLocation.route(route.id);val items=request.lines.map{line->require(line.quantity>0);val product=checkNotNull(products.findById(line.productId)){"Product not found"};require(product.isActive&&product.deletedAt==null);RouteLoadItem(UUID.randomUUID(),loadId,line.productId,line.quantity,product.averageCost)};loads.insert(load,items);val movements=items.flatMap{item->val main=inventory.findStock(item.productId,InventoryLocation.MAIN)?.quantity?:0L;val routeStock=inventory.findStock(item.productId,routeLocation)?.quantity?:0L;listOf(InventoryMovement(UUID.randomUUID(),item.productId,InventoryLocation.MAIN,InventoryMovementType.TRANSFER_OUT,-item.quantity,main,Math.subtractExact(main,item.quantity),item.unitCostSnapshot,Math.multiplyExact(-item.quantity,item.unitCostSnapshot),InventoryReferenceType.ROUTE_LOAD,loadId,createdAt=request.date),InventoryMovement(UUID.randomUUID(),item.productId,routeLocation,InventoryMovementType.TRANSFER_IN,item.quantity,routeStock,Math.addExact(routeStock,item.quantity),item.unitCostSnapshot,Math.multiplyExact(item.quantity,item.unitCostSnapshot),InventoryReferenceType.ROUTE_LOAD,loadId,createdAt=request.date))};inventory.recordMovements(movements,settings.observe().first().allowNegativeStock);load}}
