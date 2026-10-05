package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class InventoryAdjustmentInput(val productId:UUID,val type:InventoryMovementType,val quantity:Long,val unitCost:Long,val reasonId:UUID,val notes:String,val at:Instant=Instant.now())
class RegisterInventoryAdjustmentUseCase @Inject constructor(private val inventory:InventoryRepository,private val products:ProductRepository,private val settings:SettingsRepository){suspend operator fun invoke(input:InventoryAdjustmentInput){require(input.type in setOf(InventoryMovementType.ADJUSTMENT_IN,InventoryMovementType.ADJUSTMENT_OUT,InventoryMovementType.DAMAGED,InventoryMovementType.LOSS,InventoryMovementType.EXPIRED,InventoryMovementType.INTERNAL_USE));require(input.quantity!=0L&&input.unitCost>=0&&input.notes.isNotBlank());val product=checkNotNull(products.findById(input.productId)){"Product not found"};require(product.isActive&&product.deletedAt==null);val location=InventoryLocation.MAIN;val previous=inventory.findStock(input.productId,location)?.quantity?:0L;val expectedDirection=input.type.direction;require((expectedDirection==AdjustmentDirection.IN&&input.quantity>0)||(expectedDirection==AdjustmentDirection.OUT&&input.quantity<0));val movement=InventoryMovement(UUID.randomUUID(),input.productId,location,input.type,input.quantity,previous,Math.addExact(previous,input.quantity),input.unitCost,Math.multiplyExact(input.quantity,input.unitCost),reasonId=input.reasonId,notes=input.notes,createdAt=input.at);inventory.recordMovements(listOf(movement),settings.observe().first().allowNegativeStock)}}
