package com.example.bspos.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.local.entity.*
import com.example.bspos.data.local.dao.PaymentDao
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PaymentDatabaseTest {
 private lateinit var db:AppDatabase;private lateinit var payments:PaymentDao
 private val at=Instant.parse("2026-09-20T23:00:00Z");private val customerId=UUID.randomUUID();private val productId=UUID.randomUUID()
 @Before fun setup()=runTest{db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build();payments=db.paymentDao();val category=UUID.randomUUID();val unit=UUID.randomUUID();db.categoryDao().insert(CategoryEntity(category,"C",createdAt=at,updatedAt=at));db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unit,"U","u",createdAt=at,updatedAt=at));db.productDao().insert(ProductEntity(productId,"P","PAY-1",categoryId=category,unitId=unit,salePrice=100,averageCost=50,createdAt=at,updatedAt=at));db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"Cliente",createdAt=at,updatedAt=at))}
 @After fun close()=db.close()
 @Test fun receiptAndFullAllocationPersist()=runTest{val sale=creditSale(300);insertSale(sale);val payment=PaymentEntity(UUID.randomUUID(),"R-1",customerId,date=at,amount=300,method=PaymentMethod.CASH,createdAt=at);val allocation=PaymentAllocationEntity(UUID.randomUUID(),payment.id,sale.id,300);payments.insertWithAllocations(payment,listOf(allocation));Assert.assertEquals(listOf(payment),payments.observeForCustomer(customerId).first());Assert.assertEquals(listOf(allocation),payments.observeAllocations(payment.id).first())}
 @Test fun allocationsRequireExactAmountMatchingCustomerAndPendingBalance()=runTest{val sale=creditSale(100);insertSale(sale);val payment=PaymentEntity(UUID.randomUUID(),"R-2",customerId,date=at,amount=100,method=PaymentMethod.TRANSFER,createdAt=at);val wrongAmount=PaymentAllocationEntity(UUID.randomUUID(),payment.id,sale.id,99);fails<IllegalArgumentException>{payments.insertWithAllocations(payment,listOf(wrongAmount))};val otherCustomer=payment.copy(id=UUID.randomUUID(),receiptNumber="R-3",customerId=UUID.randomUUID());val mismatch=PaymentAllocationEntity(UUID.randomUUID(),otherCustomer.id,sale.id,100);fails<IllegalArgumentException>{payments.insertWithAllocations(otherCustomer,listOf(mismatch))};val tooMuch=payment.copy(id=UUID.randomUUID(),receiptNumber="R-4",amount=101);fails<IllegalArgumentException>{payments.insertWithAllocations(tooMuch,listOf(PaymentAllocationEntity(UUID.randomUUID(),tooMuch.id,sale.id,101)))}}
 @Test fun receiptNumberAndReferencesAreProtected()=runTest{val sale=creditSale(100);insertSale(sale);val payment=PaymentEntity(UUID.randomUUID(),"R-5",customerId,date=at,amount=100,method=PaymentMethod.CASH,createdAt=at);payments.insertWithAllocations(payment,listOf(PaymentAllocationEntity(UUID.randomUUID(),payment.id,sale.id,100)));val duplicate=payment.copy(id=UUID.randomUUID());fails<IllegalArgumentException>{payments.insertWithAllocations(duplicate,listOf(PaymentAllocationEntity(UUID.randomUUID(),duplicate.id,sale.id,100)))};fails<SQLiteConstraintException>{db.openHelper.writableDatabase.execSQL("DELETE FROM customers")};fails<SQLiteConstraintException>{db.openHelper.writableDatabase.execSQL("DELETE FROM sales")}}
 private fun creditSale(amount:Long)=Sale(UUID.randomUUID(),"F-${UUID.randomUUID()}",customerId,date=at,subtotal=amount,total=amount,paymentType=SalePaymentType.CREDIT,pendingAmount=amount,createdAt=at,updatedAt=at)
 private suspend fun insertSale(sale:Sale){db.saleDao().insertWithItems(SaleEntity(id=sale.id,invoiceNumber=sale.invoiceNumber,customerId=sale.customerId,date=sale.date,subtotal=sale.subtotal,discount=sale.discount,tax=sale.tax,total=sale.total,paymentType=sale.paymentType,paidAmount=sale.paidAmount,pendingAmount=sale.pendingAmount,status=sale.status,notes=sale.notes,createdAt=sale.createdAt,updatedAt=sale.updatedAt),listOf(SaleItemEntity(UUID.randomUUID(),sale.id,productId,1,sale.total,50,subtotal=sale.total)))}
 private suspend inline fun <reified T:Throwable> fails(block:suspend()->Unit){try{block()}catch(e:Throwable){if(e is T)return;throw e};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
