package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.UnitOfMeasure
import com.example.bspos.domain.repository.CategoryRepository
import com.example.bspos.domain.repository.UnitOfMeasureRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CategoryUseCases @Inject constructor(private val repository:CategoryRepository){
 fun observe():Flow<List<Category>> = repository.observeAll()
 suspend fun create(name:String,description:String?=null,icon:String="category_default"):Category{val now=Instant.now();val category=Category(UUID.randomUUID(),name.clean("Category name"),description.cleanOptional(),icon.clean("Category icon"),createdAt=now,updatedAt=now);repository.insert(category);return category}
 suspend fun update(current:Category,name:String,description:String?=current.description,icon:String=current.icon,isActive:Boolean=current.isActive):Boolean=repository.update(current.copy(name=name.clean("Category name"),description=description.cleanOptional(),icon=icon.clean("Category icon"),isActive=isActive,updatedAt=Instant.now()))
 suspend fun delete(id:UUID):Boolean=repository.softDelete(id,Instant.now())
 suspend fun reorder(ids:List<UUID>){require(ids.isNotEmpty()&&ids.distinct().size==ids.size);repository.reorder(ids,Instant.now())}
}
class UnitOfMeasureUseCases @Inject constructor(private val repository:UnitOfMeasureRepository){
 fun observe():Flow<List<UnitOfMeasure>> = repository.observeAll()
 suspend fun create(name:String,abbreviation:String):UnitOfMeasure{val now=Instant.now();val unit=UnitOfMeasure(UUID.randomUUID(),name.clean("Unit name"),abbreviation.clean("Unit abbreviation").uppercase(),createdAt=now,updatedAt=now);repository.insert(unit);return unit}
 suspend fun update(current:UnitOfMeasure,name:String,abbreviation:String,isActive:Boolean=current.isActive):Boolean=repository.update(current.copy(name=name.clean("Unit name"),abbreviation=abbreviation.clean("Unit abbreviation").uppercase(),isActive=isActive,updatedAt=Instant.now()))
 suspend fun delete(id:UUID):Boolean=repository.softDelete(id,Instant.now())
}
internal fun String.clean(label:String):String=trim().also{require(it.isNotEmpty()&&it.length<=80){"$label is required and must be at most 80 characters"}}
internal fun String?.cleanOptional():String?=this?.trim()?.takeIf{it.isNotEmpty()}?.also{require(it.length<=300){"Description must be at most 300 characters"}}
