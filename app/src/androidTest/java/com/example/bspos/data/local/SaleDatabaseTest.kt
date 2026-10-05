package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.local.entity.*
import com.example.bspos.data.repository.SaleRepositoryImpl
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SaleDatabaseTest {
 private lateinit var db:AppDatabase;private lateinit var sales:SaleRepositoryImpl
 private val at=Instant.parse("2026-09-20T22:00:00Z");private val productId=UUID.randomUUID();private val customerId=UUID.randomUUID()
 @Before fun setup()=runTest{db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build();sales=SaleRepositoryImpl(db.saleDao());val cat=UUID.randomUUID();val unit=UUID.randomUUID();db.categoryDao().insert(CategoryEntity(cat,"C",createdAt=at,updatedAt=at));db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unit,"U","u",createdAt=at,updatedAt=at));db.productDao().insert(ProductEntity(productId,"P","SALE-1",categoryId=cat,unitId=unit,salePrice=150,averageCost=90,createdAt=at,updatedAt=at));db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"Cliente",createdAt=at,updatedAt=at))}
 @After fun close()=db.close()
 @Test fun cashSalePersistsHistoricalCostAndTotals()=runTest{val sale=Sale(UUID.randomUUID(),"F-1",date=at,subtotal=300,total=300,paymentType=SalePaymentType.CASH,paidAmount=300,createdAt=at,updatedAt=at);val item=SaleItem(UUID.randomUUID(),sale.id,productId,2,150,90,subtotal=300);sales.insert(sale,listOf(item));Assert.assertEquals(listOf(sale),sales.observeAll().first());Assert.assertEquals(listOf(item),sales.observeItems(sale.id).first());Assert.assertEquals(90,db.saleDao().findById(sale.id)!!.let{sales.observeItems(it.id).first().single().unitCostSnapshot})}
 @Test fun creditRequiresCustomerAndPendingBalance()=runTest{val credit=Sale(UUID.randomUUID(),"F-2",customerId,date=at,subtotal=100,total=100,paymentType=SalePaymentType.CREDIT,pendingAmount=100,createdAt=at,updatedAt=at);val item=SaleItem(UUID.randomUUID(),credit.id,productId,1,100,90,subtotal=100);sales.insert(credit,listOf(item));fail<IllegalArgumentException>{sales.insert(credit.copy(id=UUID.randomUUID(),invoiceNumber="F-3",customerId=null),listOf(item.copy(id=UUID.randomUUID(),saleId=UUID.randomUUID())))} }
 @Test fun invalidOrDuplicateLinesRollbackHeader()=runTest{val sale=Sale(UUID.randomUUID(),"F",date=at,subtotal=100,total=100,paymentType=SalePaymentType.CASH,paidAmount=100,createdAt=at,updatedAt=at);val item=SaleItem(UUID.randomUUID(),sale.id,productId,1,100,90,subtotal=100);fail<IllegalArgumentException>{sales.insert(sale.copy(total=99),listOf(item))};fail<IllegalArgumentException>{sales.insert(sale,listOf(item,item.copy(id=UUID.randomUUID())))};Assert.assertNull(db.saleDao().findById(sale.id))}
 @Test fun foreignKeysAndInvoiceUniquenessProtectHistory()=runTest{val sale=Sale(UUID.randomUUID(),"F",customerId,date=at,subtotal=100,total=100,paymentType=SalePaymentType.CASH,paidAmount=100,createdAt=at,updatedAt=at);sales.insert(sale,listOf(SaleItem(UUID.randomUUID(),sale.id,productId,1,100,90,subtotal=100)));fail<SQLiteConstraintException>{db.openHelper.writableDatabase.execSQL("DELETE FROM products")};fail<SQLiteConstraintException>{db.openHelper.writableDatabase.execSQL("DELETE FROM customers")};val duplicate=sale.copy(id=UUID.randomUUID());fail<SQLiteConstraintException>{sales.insert(duplicate,listOf(SaleItem(UUID.randomUUID(),duplicate.id,productId,1,100,90,subtotal=100)))} }
 private suspend inline fun <reified T:Throwable> fail(block:suspend()->Unit){try{block()}catch(e:Throwable){if(e is T)return;throw e};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
