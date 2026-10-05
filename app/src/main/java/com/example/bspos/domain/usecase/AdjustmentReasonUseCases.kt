package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.AdjustmentDirection
import com.example.bspos.domain.model.InventoryAdjustmentReason
import com.example.bspos.domain.repository.InventoryAdjustmentReasonRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class AdjustmentReasonUseCases @Inject constructor(private val repository:InventoryAdjustmentReasonRepository){fun observe():Flow<List<InventoryAdjustmentReason>> = repository.observeAll();suspend fun create(name:String,direction:AdjustmentDirection):InventoryAdjustmentReason{val now=Instant.now();val reason=InventoryAdjustmentReason(UUID.randomUUID(),name.clean("Adjustment reason"),direction,createdAt=now,updatedAt=now);repository.insert(reason);return reason};suspend fun update(current:InventoryAdjustmentReason,name:String,direction:AdjustmentDirection,isActive:Boolean=current.isActive):Boolean=repository.update(current.copy(name=name.clean("Adjustment reason"),direction=direction,isActive=isActive,updatedAt=Instant.now()));suspend fun delete(id:UUID):Boolean=repository.softDelete(id,Instant.now())}
