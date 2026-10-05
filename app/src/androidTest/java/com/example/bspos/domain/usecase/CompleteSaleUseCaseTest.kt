package com.example.bspos.domain.usecase

import android.content.Context
import androidx.room.Room
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json

@RunWith(AndroidJUnit4::class)
class CompleteSaleUseCaseTest {
 private lateinit var db:AppDatabase;private lateinit var useCase:CompleteSaleUseCase
 private val at=Instant.parse("2026-09-21T03:00:00Z");private val productId=UUID.randomUUID()
  @Before fun setup()=runTest{db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build();useCase=CompleteSaleUseCase(RoomDatabaseTransactor(db),db.saleDao(),db.inventoryDao(),db.productDao(),db.customerRouteDao(),db.cashSessionDao(),db.posSaleOutboxDao(),SafeSettings,Json{ignoreUnknownKeys=true},com.example.bspos.data.micatalogo.PosSaleSyncScheduler(ApplicationProvider.getApplicationContext<Context>()));val category=UUID.randomUUID();val unit=UUID.randomUUID();db.categoryDao().insert(CategoryEntity(category,"C",createdAt=at,updatedAt=at));db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unit,"U","u",createdAt=at,updatedAt=at));db.productDao().insert(ProductEntity(productId,"P","ATOMIC-1",categoryId=category,unitId=unit,salePrice=150,averageCost=90,createdAt=at,updatedAt=at));db.inventoryDao().recordMovements(listOf(InventoryMovementEntity(UUID.randomUUID(),productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN",InventoryMovementType.INITIAL,5,0,5,90,450,createdAt=at)),false)}
 @After fun close()=db.close()
  @Test fun cashSaleCommitsInvoiceKardexStockAndCashTogether()=runTest{val session=CashSessionEntity(UUID.randomUUID(),at,openingAmount=0);db.cashSessionDao().open(session);val sale=useCase(CompleteSaleRequest("A-1",date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,2,150)),paymentType=SalePaymentType.CASH,paidAmount=300,pendingAmount=0,cashSessionId=session.id));Assert.assertEquals(300L,sale.total);Assert.assertEquals(3L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity);Assert.assertEquals(1,db.saleDao().observeItems(sale.id).first().size);Assert.assertEquals(1,db.cashSessionDao().observeMovements(session.id).first().size)}
  @Test fun cashSaleRequiresAnOpenSession()=runTest{fails<IllegalStateException>{useCase(CompleteSaleRequest("A-NO-SESSION",date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,1,150)),paymentType=SalePaymentType.CASH,paidAmount=150,pendingAmount=0))};Assert.assertTrue(db.saleDao().observeAll().first().isEmpty());Assert.assertEquals(5L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)}
  @Test fun insufficientStockRollsBackSale()=runTest{db.cashSessionDao().open(CashSessionEntity(UUID.randomUUID(),at,openingAmount=0));fails<IllegalStateException>{useCase(CompleteSaleRequest("A-2",date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,6,150)),paymentType=SalePaymentType.CASH,paidAmount=900,pendingAmount=0))};Assert.assertTrue(db.saleDao().observeAll().first().isEmpty());Assert.assertEquals(5L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)}
  @Test fun linkedDecantSaleConsumesSourceMlAndUpdatesSiblingStock() = runTest {
      val source = db.productDao().findById(productId)!!
      db.productDao().update(source.copy(miCatalogoShopId="shop", miCatalogoProductId="source", miCatalogoSaleUnit="bottle", miCatalogoVolumeMl=100, miCatalogoAvailableMl=500))
      val decantId=UUID.randomUUID()
      val siblingId=UUID.randomUUID()
      db.productDao().insert(source.copy(id=decantId, internalCode="DECANT-5", miCatalogoShopId="shop", miCatalogoProductId="decant", miCatalogoSourceProductId="source", miCatalogoSaleUnit="decant", miCatalogoVolumeMl=5))
      db.productDao().insert(source.copy(id=siblingId, internalCode="DECANT-10", miCatalogoShopId="shop", miCatalogoProductId="sibling", miCatalogoSourceProductId="source", miCatalogoSaleUnit="decant", miCatalogoVolumeMl=10))
      for ((id,qty) in listOf(decantId to 100L,siblingId to 50L)) db.inventoryDao().recordMovements(listOf(InventoryMovementEntity(UUID.randomUUID(),id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN",InventoryMovementType.INITIAL,qty,0,qty,90,qty*90,createdAt=at)),false)
      val session=CashSessionEntity(UUID.randomUUID(),at,openingAmount=0)
      db.cashSessionDao().open(session)
      useCase(CompleteSaleRequest("DECANT-1",date=at.plusSeconds(1),lines=listOf(SaleLineInput(decantId,2,150)),paymentType=SalePaymentType.CASH,paidAmount=300,pendingAmount=0,cashSessionId=session.id))
      Assert.assertEquals(490,db.productDao().findById(productId)!!.miCatalogoAvailableMl)
      Assert.assertEquals(4L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
      Assert.assertEquals(98L,db.inventoryDao().findStock(decantId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
      Assert.assertEquals(49L,db.inventoryDao().findStock(siblingId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
  }
  @Test fun creditSaleUpdatesInvoiceStockAndCustomerBalance() = runTest {
      val customerId = UUID.randomUUID()
      db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"Credit test",creditLimit=1000,createdAt=at,updatedAt=at))
      val sale = useCase(CompleteSaleRequest("CREDIT-1",customerId=customerId,date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,2,150)),paymentType=SalePaymentType.CREDIT,paidAmount=0,pendingAmount=300))
      Assert.assertEquals(300L,sale.pendingAmount)
      Assert.assertEquals(300L,db.customerRouteDao().findCustomer(customerId)!!.balance)
      Assert.assertEquals(3L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
      Assert.assertEquals(1,db.saleDao().observeItems(sale.id).first().size)
  }
  @Test fun exceedingCreditLimitLeavesInvoiceBalanceAndStockUntouched() = runTest {
      val customerId = UUID.randomUUID()
      db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"Credit limit test",creditLimit=100,createdAt=at,updatedAt=at))
      fails<IllegalArgumentException>{useCase(CompleteSaleRequest("CREDIT-FAIL",customerId=customerId,date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,2,150)),paymentType=SalePaymentType.CREDIT,paidAmount=0,pendingAmount=300))}
      Assert.assertTrue(db.saleDao().observeAll().first().isEmpty())
      Assert.assertEquals(0L,db.customerRouteDao().findCustomer(customerId)!!.balance)
      Assert.assertEquals(5L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
  }
  private object SafeSettings:SettingsRepository{override fun observe():Flow<AppSettings> = flowOf(AppSettings());override suspend fun setAllowNegativeStock(enabled:Boolean){};override suspend fun setAutomaticBackupsEnabled(enabled:Boolean){};override suspend fun setRoutesEnabled(enabled:Boolean){}}
 private suspend inline fun <reified T:Throwable> fails(block:suspend()->Unit){try{block()}catch(e:Throwable){if(e is T)return;throw e};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
