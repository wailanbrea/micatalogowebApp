package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.UnitOfMeasure
import com.example.bspos.domain.repository.CategoryRepository
import com.example.bspos.domain.repository.UnitOfMeasureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CatalogUseCasesTest {
 @Test fun categoryTrimsFieldsAndOrdersUniqueIds()=runTest{val repo=Categories();val useCases=CategoryUseCases(repo);val category=useCases.create("  Bebidas  ","  Frías  ");assertEquals("Bebidas",category.name);assertEquals("Frías",category.description);useCases.reorder(listOf(category.id));assertEquals(listOf(category.id),repo.order);assertFails<IllegalArgumentException>{useCases.reorder(listOf(category.id,category.id))}}
 @Test fun unitUppercasesAbbreviationAndRejectsBlankValues()=runTest{val repo=Units();val useCases=UnitOfMeasureUseCases(repo);val unit=useCases.create("  Kilogramo "," kg ");assertEquals("Kilogramo",unit.name);assertEquals("KG",unit.abbreviation);assertFails<IllegalArgumentException>{useCases.create(" ","kg")};assertFails<IllegalArgumentException>{useCases.create("Caja"," ")}}
 private class Categories:CategoryRepository{var order=emptyList<UUID>();override fun observeAll():Flow<List<Category>> = emptyFlow();override suspend fun findById(id:UUID)=null;override suspend fun insert(record:Category){};override suspend fun update(record:Category)=true;override suspend fun softDelete(id:UUID,at:Instant)=true;override suspend fun reorder(ids:List<UUID>,at:Instant){order=ids}}
 private class Units:UnitOfMeasureRepository{override fun observeAll():Flow<List<UnitOfMeasure>> = emptyFlow();override suspend fun findById(id:UUID)=null;override suspend fun insert(record:UnitOfMeasure){};override suspend fun update(record:UnitOfMeasure)=true;override suspend fun softDelete(id:UUID,at:Instant)=true}
 private suspend inline fun <reified T:Throwable> assertFails(block:suspend()->Unit){try{block()}catch(error:Throwable){if(error is T)return;throw error};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
