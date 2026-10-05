package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.local.dao.ReturnDao
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReturnDatabaseTest {
 private lateinit var db:AppDatabase;private lateinit var returns:ReturnDao
 private val at=Instant.parse("2026-09-21T00:00:00Z");private val customerId=UUID.randomUUID();private val productId=UUID.randomUUID()
 @Before fun setup()=runTest{db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build();returns=db.returnDao();val category=UUID.randomUUID();val unit=UUID.randomUUID();db.categoryDao().insert(CategoryEntity(category,"C",createdAt=at,updatedAt=at));db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unit,"U","u",createdAt=at,updatedAt=at));db.productDao().insert(ProductEntity(productId,"P","RETURN-1",categoryId=category,unitId=unit,salePrice=100,averageCost=50,createdAt=at,updatedAt=at));db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"Cliente",createdAt=at,updatedAt=at))}
 @After fun close()=db.close()
 @Test fun returnPersistsHistoricalValuesAndLines()=runTest{val (sale,item)=insertSale();val value=ReturnEntity(UUID.randomUUID(),"D-1",sale.id,customerId,at,100,"Damaged",at);val line=ReturnItemEntity(UUID.randomUUID(),value.id,item.id,productId,1,100,50,false);returns.insertWithItems(value,listOf(line));Assert.assertEquals(listOf(value),returns.observeForSale(sale.id).first());Assert.assertEquals(listOf(line),returns.observeItems(value.id).first())}
 @Test fun returnCannotExceedSoldQuantityOrAlterSnapshots()=runTest{val (sale,item)=insertSale();val value=ReturnEntity(UUID.randomUUID(),"D-2",sale.id,customerId,at,200,createdAt=at);fails<IllegalArgumentException>{returns.insertWithItems(value,listOf(ReturnItemEntity(UUID.randomUUID(),value.id,item.id,productId,3,100,50)))};val altered=value.copy(id=UUID.randomUUID(),returnNumber="D-3",totalAmount=100);fails<IllegalArgumentException>{returns.insertWithItems(altered,listOf(ReturnItemEntity(UUID.randomUUID(),altered.id,item.id,productId,1,100,51)))} }
 @Test fun returnNumberAndSaleReferencesAreProtected()=runTest{val (sale,item)=insertSale();val value=ReturnEntity(UUID.randomUUID(),"D-4",sale.id,customerId,at,100,createdAt=at);returns.insertWithItems(value,listOf(ReturnItemEntity(UUID.randomUUID(),value.id,item.id,productId,1,100,50)));val duplicate=value.copy(id=UUID.randomUUID());fails<SQLiteConstraintException>{returns.insertWithItems(duplicate,listOf(ReturnItemEntity(UUID.randomUUID(),duplicate.id,item.id,productId,1,100,50)))};fails<SQLiteConstraintException>{db.openHelper.writableDatabase.execSQL("DELETE FROM sales")}}
 private suspend fun insertSale():Pair<SaleEntity,SaleItemEntity>{val sale=SaleEntity(UUID.randomUUID(),"F-${UUID.randomUUID()}",customerId,date=at,subtotal=200,total=200,paymentType=SalePaymentType.CREDIT,pendingAmount=200,createdAt=at,updatedAt=at);val item=SaleItemEntity(UUID.randomUUID(),sale.id,productId,2,100,50,subtotal=200);db.saleDao().insertWithItems(sale,listOf(item));return sale to item}
 private suspend inline fun <reified T:Throwable> fails(block:suspend()->Unit){try{block()}catch(e:Throwable){if(e is T)return;throw e};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
