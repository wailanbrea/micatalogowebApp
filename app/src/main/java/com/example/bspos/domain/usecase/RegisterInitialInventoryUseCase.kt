package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.ProductRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RegisterInitialInventoryUseCase @Inject constructor(private val inventory:InventoryRepository,private val products:ProductRepository){suspend operator fun invoke(productId:UUID,quantity:Long,unitCost:Long,at:Instant=Instant.now()){require(quantity>0&&unitCost>=0);val product=checkNotNull(products.findById(productId)){"Product not found"};require(product.isActive&&product.deletedAt==null);val location=InventoryLocation.MAIN;check(inventory.findStock(productId,location)==null){"Initial inventory already exists"};inventory.recordMovements(listOf(InventoryMovement(UUID.randomUUID(),productId,location,InventoryMovementType.INITIAL,quantity,0,quantity,unitCost,Math.multiplyExact(quantity,unitCost),createdAt=at)),false)}}
