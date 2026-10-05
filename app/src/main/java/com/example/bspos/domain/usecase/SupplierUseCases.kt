package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.Supplier
import com.example.bspos.domain.repository.SupplierRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class SupplierInput(val name:String,val contactName:String?=null,val phone:String?=null,val email:String?=null,val address:String?=null,val taxId:String?=null,val notes:String?=null)
class SupplierUseCases @Inject constructor(private val repository:SupplierRepository){fun observe():Flow<List<Supplier>> = repository.observeAll();suspend fun create(input:SupplierInput):Supplier{val now=Instant.now();val supplier=Supplier(UUID.randomUUID(),input.name.clean("Supplier name"),input.contactName.cleanOptional(),input.phone.cleanOptional(),input.email.cleanOptional(),input.address.cleanOptional(),input.taxId.cleanOptional(),input.notes.cleanOptional(),createdAt=now,updatedAt=now);repository.insert(supplier);return supplier};suspend fun update(current:Supplier,input:SupplierInput):Boolean=repository.update(current.copy(name=input.name.clean("Supplier name"),contactName=input.contactName.cleanOptional(),phone=input.phone.cleanOptional(),email=input.email.cleanOptional(),address=input.address.cleanOptional(),taxId=input.taxId.cleanOptional(),notes=input.notes.cleanOptional(),updatedAt=Instant.now()));suspend fun delete(id:UUID):Boolean=repository.softDelete(id,Instant.now())}
