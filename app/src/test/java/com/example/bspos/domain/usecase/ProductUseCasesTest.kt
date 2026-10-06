package com.example.bspos.domain.usecase

import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ProductUseCasesTest {
 @Test fun createRequiresActiveParentsAndValidMoney()=runTest{val category=category();val unit=unit();val products=Products();val useCases=ProductUseCases(products,Categories(category),Units(unit));val product=useCases.create(ProductInput("  Agua "," ag-1 ",category.id,unit.id,125));assertEquals("Agua",product.name);assertEquals("AG-1",product.internalCode);assertFails<IllegalArgumentException>{useCases.create(ProductInput("Agua","A",category.id,unit.id,-1))};assertFails<IllegalArgumentException>{ProductUseCases(products,Categories(category.copy(isActive=false)),Units(unit)).create(ProductInput("Agua","A",category.id,unit.id,1))}}
 @Test fun updatePreservesHistoricalCosts()=runTest{val category=category();val unit=unit();val products=Products();val useCases=ProductUseCases(products,Categories(category),Units(unit));val current=Product(UUID.randomUUID(),"A","A",categoryId=category.id,unitId=unit.id,salePrice=10,averageCost=7,lastPurchaseCost=8,createdAt=Instant.now(),updatedAt=Instant.now());useCases.update(current,ProductInput("B","B",category.id,unit.id,20));assertEquals(7L,products.updated!!.averageCost);assertEquals(8L,products.updated!!.lastPurchaseCost)}
 private fun category()=Category(UUID.randomUUID(),"C",createdAt=Instant.now(),updatedAt=Instant.now())
 private fun unit()=UnitOfMeasure(UUID.randomUUID(),"U","u",createdAt=Instant.now(),updatedAt=Instant.now())
 private class Products:ProductRepository{var updated:Product?=null;override fun observeAll():Flow<List<Product>> = emptyFlow();override fun observeForShop(shopId:String):Flow<List<Product>> = emptyFlow();override suspend fun findById(id:UUID)=null;override suspend fun insert(record:Product){};override suspend fun update(record:Product):Boolean{updated=record;return true};override suspend fun softDelete(id:UUID,at:Instant)=true}
 private class Categories(private val value:Category):CategoryRepository{override fun observeAll():Flow<List<Category>> = emptyFlow();override suspend fun findById(id:UUID)=value;override suspend fun insert(record:Category){};override suspend fun update(record:Category)=true;override suspend fun softDelete(id:UUID,at:Instant)=true;override suspend fun reorder(ids:List<UUID>,at:Instant){}}
 private class Units(private val value:UnitOfMeasure):UnitOfMeasureRepository{override fun observeAll():Flow<List<UnitOfMeasure>> = emptyFlow();override suspend fun findById(id:UUID)=value;override suspend fun insert(record:UnitOfMeasure){};override suspend fun update(record:UnitOfMeasure)=true;override suspend fun softDelete(id:UUID,at:Instant)=true}
 private suspend inline fun <reified T:Throwable> assertFails(block:suspend()->Unit){try{block()}catch(error:Throwable){if(error is T)return;throw error};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
