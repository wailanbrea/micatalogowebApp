package com.example.bspos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecordPaymentUseCaseTest {
 private lateinit var db:AppDatabase;private lateinit var useCase:RecordPaymentUseCase
 private val at=Instant.parse("2026-09-21T04:00:00Z");private val customerId=UUID.randomUUID();private val productId=UUID.randomUUID()
  @Before fun setup()=runTest{db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build();useCase=RecordPaymentUseCase(RoomDatabaseTransactor(db),db.paymentDao(),db.saleDao(),db.customerRouteDao(),db.cashSessionDao(),db.paymentSyncDao(),db.posSaleOutboxDao(),com.example.bspos.data.micatalogo.PosSaleSyncScheduler(ApplicationProvider.getApplicationContext<Context>()),kotlinx.serialization.json.Json);val category=UUID.randomUUID();val unit=UUID.randomUUID();db.categoryDao().insert(CategoryEntity(category,"C",createdAt=at,updatedAt=at));db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unit,"U","u",createdAt=at,updatedAt=at));db.productDao().insert(ProductEntity(productId,"P","PAY-ATOMIC",categoryId=category,unitId=unit,salePrice=100,createdAt=at,updatedAt=at));db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"Customer",creditLimit=100,balance=100,createdAt=at,updatedAt=at))}
 @After fun close()=db.close()
  @Test fun paymentUpdatesReceiptAllocationSaleAndCashTogether()=runTest{val sale=creditSale();val session=CashSessionEntity(UUID.randomUUID(),at,openingAmount=0);db.cashSessionDao().open(session);val payment=useCase(RecordPaymentRequest("R-ATOMIC",customerId,at.plusSeconds(1),PaymentMethod.CASH,listOf(PaymentAllocationInput(sale.id,100)),cashSessionId=session.id));Assert.assertEquals(100L,payment.amount);Assert.assertEquals(100L,db.saleDao().findById(sale.id)!!.paidAmount);Assert.assertEquals(0L,db.saleDao().findById(sale.id)!!.pendingAmount);Assert.assertEquals(1,db.paymentDao().observeAllocations(payment.id).first().size);Assert.assertEquals(1,db.cashSessionDao().observeMovements(session.id).first().size)}
  @Test fun cashCollectionRequiresAnOpenSession()=runTest{val sale=creditSale();fails<IllegalStateException>{useCase(RecordPaymentRequest("R-NO-SESSION",customerId,at,PaymentMethod.CASH,listOf(PaymentAllocationInput(sale.id,100))))};Assert.assertTrue(db.paymentDao().observeForCustomer(customerId).first().isEmpty());Assert.assertEquals(100L,db.saleDao().findById(sale.id)!!.pendingAmount);Assert.assertEquals(100L,db.customerRouteDao().findCustomer(customerId)!!.balance)}
 @Test fun overpaymentRollsBackReceiptAndBalances()=runTest{val sale=creditSale();fails<IllegalArgumentException>{useCase(RecordPaymentRequest("R-FAIL",customerId,at,PaymentMethod.TRANSFER,listOf(PaymentAllocationInput(sale.id,101))))};Assert.assertTrue(db.paymentDao().observeForCustomer(customerId).first().isEmpty());Assert.assertEquals(0L,db.saleDao().findById(sale.id)!!.paidAmount);Assert.assertEquals(100L,db.saleDao().findById(sale.id)!!.pendingAmount)}
 private suspend fun creditSale():SaleEntity{val sale=SaleEntity(UUID.randomUUID(),"F-${UUID.randomUUID()}",customerId,date=at,subtotal=100,total=100,paymentType=SalePaymentType.CREDIT,pendingAmount=100,createdAt=at,updatedAt=at);db.saleDao().insertWithItems(sale,listOf(SaleItemEntity(UUID.randomUUID(),sale.id,productId,1,100,0,subtotal=100)));return sale}
 private suspend inline fun <reified T:Throwable> fails(block:suspend()->Unit){try{block()}catch(e:Throwable){if(e is T)return;throw e};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
