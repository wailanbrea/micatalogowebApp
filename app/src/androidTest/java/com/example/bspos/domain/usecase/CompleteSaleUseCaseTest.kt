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
  @Before fun setup()=runTest{db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build();useCase=CompleteSaleUseCase(RoomDatabaseTransactor(db),db.saleDao(),db.inventoryDao(),db.productDao(),db.customerRouteDao(),db.posSaleOutboxDao(),SafeSettings,Json{ignoreUnknownKeys=true},com.example.bspos.data.micatalogo.PosSaleSyncScheduler(ApplicationProvider.getApplicationContext<Context>()),com.example.bspos.data.local.tenant.tenantTestScope("shop"),com.example.bspos.data.local.tenant.tenantTestMenuPolicy("shop"));val category=UUID.randomUUID();val unit=UUID.randomUUID();val tenant=TenantMetadata.assigned("shop");db.categoryDao().insert(CategoryEntity(category,"C",createdAt=at,updatedAt=at,tenant=tenant));db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unit,"U","u",createdAt=at,updatedAt=at,tenant=tenant));db.productDao().insert(ProductEntity(productId,"P","ATOMIC-1",categoryId=category,unitId=unit,salePrice=150,averageCost=90,createdAt=at,updatedAt=at,tenant=tenant));db.inventoryDao().recordMovements(listOf(InventoryMovementEntity(UUID.randomUUID(),productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN",InventoryMovementType.INITIAL,5,0,5,90,450,createdAt=at,tenant=tenant)),false)}
  @After fun close()=db.close()
  @Test fun collectionAuthorityCannotIssueCreditOrConsumeStockButCanKeepPaidSales()=runTest {
      val row=db.productDao().findById(productId,"shop")!!
      db.productDao().update(row.copy(miCatalogoShopId="shop",miCatalogoProductId="remote-product"))
      val customerId=UUID.randomUUID()
      db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"QA_NAV_CreditBoundary",creditLimit=1000,createdAt=at,updatedAt=at,
          miCatalogoCustomerId="remote-customer",miCatalogoCustomerShopId="shop",tenant=TenantMetadata.assigned("shop")))
      val restricted=CompleteSaleUseCase(RoomDatabaseTransactor(db),db.saleDao(),db.inventoryDao(),db.productDao(),db.customerRouteDao(),
          db.posSaleOutboxDao(),SafeSettings,Json,com.example.bspos.data.micatalogo.PosSaleSyncScheduler(ApplicationProvider.getApplicationContext<Context>()),
          com.example.bspos.data.local.tenant.tenantTestScope("shop"),com.example.bspos.data.local.tenant.tenantTestMenuPolicy("shop",listOf("sales","collections")))
      fails<IllegalStateException> { restricted(CompleteSaleRequest("QA_NAV_NoCredit",customerId=customerId,date=at,
          lines=listOf(SaleLineInput(productId,1,150)),paymentType=SalePaymentType.CREDIT,paidAmount=0,pendingAmount=150)) }
      Assert.assertTrue(db.saleDao().observeAll("shop").first().isEmpty())
      Assert.assertEquals(0L,db.customerRouteDao().findCustomer(customerId,"shop")!!.balance)
      Assert.assertEquals(5L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)
      val paid=restricted(CompleteSaleRequest("QA_NAV_Paid",date=at,lines=listOf(SaleLineInput(productId,1,150)),
          paymentType=SalePaymentType.TRANSFER,paidAmount=150,pendingAmount=0))
      Assert.assertEquals(0L,paid.pendingAmount)
      Assert.assertEquals(4L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)
  }
   @Test fun cashSaleCommitsInvoiceAndKardexWithoutLocalCashMovement()=runTest{val session=CashSessionEntity(UUID.randomUUID(),at,openingAmount=0,remoteSessionId="qa-cash",tenant=TenantMetadata.assigned("shop"));db.cashSessionDao().open(session);val sale=useCase(CompleteSaleRequest("A-1",date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,2,150)),paymentType=SalePaymentType.CASH,paidAmount=300,pendingAmount=0,cashSessionId=session.id));Assert.assertEquals(300L,sale.total);Assert.assertEquals(3L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity);Assert.assertEquals(1,db.saleDao().observeItems(sale.id,"shop").first().size);Assert.assertTrue(db.cashSessionDao().observeMovements(session.id,"shop").first().isEmpty())}
   @Test fun cashSaleDoesNotRequireLocalSessionForRemoteCashSync()=runTest{val sale=useCase(CompleteSaleRequest("A-NO-SESSION",date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,1,150)),paymentType=SalePaymentType.CASH,paidAmount=150,pendingAmount=0));Assert.assertEquals(150L,sale.total);Assert.assertEquals(1,db.saleDao().observeAll("shop").first().size);Assert.assertEquals(4L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)}
   @Test fun insufficientStockRollsBackSale()=runTest{fails<IllegalStateException>{useCase(CompleteSaleRequest("A-2",date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,6,150)),paymentType=SalePaymentType.CASH,paidAmount=900,pendingAmount=0))};Assert.assertTrue(db.saleDao().observeAll("shop").first().isEmpty());Assert.assertEquals(5L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)}
  @Test fun linkedDecantSaleConsumesSourceMlAndUpdatesSiblingStock() = runTest {
       val source = db.productDao().findById(productId,"shop")!!
      db.productDao().update(source.copy(miCatalogoShopId="shop", miCatalogoProductId="source", miCatalogoSaleUnit="bottle", miCatalogoVolumeMl=100, miCatalogoAvailableMl=500))
      val decantId=UUID.randomUUID()
      val siblingId=UUID.randomUUID()
      db.productDao().insert(source.copy(id=decantId, internalCode="DECANT-5", miCatalogoShopId="shop", miCatalogoProductId="decant", miCatalogoSourceProductId="source", miCatalogoSaleUnit="decant", miCatalogoVolumeMl=5))
      db.productDao().insert(source.copy(id=siblingId, internalCode="DECANT-10", miCatalogoShopId="shop", miCatalogoProductId="sibling", miCatalogoSourceProductId="source", miCatalogoSaleUnit="decant", miCatalogoVolumeMl=10))
       for ((id,qty) in listOf(decantId to 100L,siblingId to 50L)) db.inventoryDao().recordMovements(listOf(InventoryMovementEntity(UUID.randomUUID(),id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN",InventoryMovementType.INITIAL,qty,0,qty,90,qty*90,createdAt=at,tenant=TenantMetadata.assigned("shop"))),false)
       val session=CashSessionEntity(UUID.randomUUID(),at,openingAmount=0,remoteSessionId="qa-cash",tenant=TenantMetadata.assigned("shop"))
      db.cashSessionDao().open(session)
      useCase(CompleteSaleRequest("DECANT-1",date=at.plusSeconds(1),lines=listOf(SaleLineInput(decantId,2,150)),paymentType=SalePaymentType.CASH,paidAmount=300,pendingAmount=0,cashSessionId=session.id))
       Assert.assertEquals(490,db.productDao().findById(productId,"shop")!!.miCatalogoAvailableMl)
       Assert.assertEquals(4L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)
       Assert.assertEquals(98L,db.inventoryDao().findStock(decantId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)
       Assert.assertEquals(49L,db.inventoryDao().findStock(siblingId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)
  }
  @Test fun creditSaleUpdatesInvoiceStockAndCustomerBalance() = runTest {
      val customerId = UUID.randomUUID()
       db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"Credit test",creditLimit=1000,createdAt=at,updatedAt=at,tenant=TenantMetadata.assigned("shop")))
      val sale = useCase(CompleteSaleRequest("CREDIT-1",customerId=customerId,date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,2,150)),paymentType=SalePaymentType.CREDIT,paidAmount=0,pendingAmount=300))
      Assert.assertEquals(300L,sale.pendingAmount)
       Assert.assertEquals(300L,db.customerRouteDao().findCustomer(customerId,"shop")!!.balance)
       Assert.assertEquals(3L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)
       Assert.assertEquals(1,db.saleDao().observeItems(sale.id,"shop").first().size)
  }
  @Test fun exceedingCreditLimitLeavesInvoiceBalanceAndStockUntouched() = runTest {
      val customerId = UUID.randomUUID()
       db.customerRouteDao().insertCustomer(CustomerEntity(customerId,"Credit limit test",creditLimit=100,createdAt=at,updatedAt=at,tenant=TenantMetadata.assigned("shop")))
      fails<IllegalArgumentException>{useCase(CompleteSaleRequest("CREDIT-FAIL",customerId=customerId,date=at.plusSeconds(1),lines=listOf(SaleLineInput(productId,2,150)),paymentType=SalePaymentType.CREDIT,paidAmount=0,pendingAmount=300))}
       Assert.assertTrue(db.saleDao().observeAll("shop").first().isEmpty())
       Assert.assertEquals(0L,db.customerRouteDao().findCustomer(customerId,"shop")!!.balance)
       Assert.assertEquals(5L,db.inventoryDao().findStock(productId,InventoryLocationType.MAIN_WAREHOUSE,"MAIN","shop")!!.quantity)
  }
  private object SafeSettings:SettingsRepository{override fun observe():Flow<AppSettings> = flowOf(AppSettings());override suspend fun setAllowNegativeStock(enabled:Boolean){};override suspend fun setAutomaticBackupsEnabled(enabled:Boolean){};override suspend fun setRoutesEnabled(enabled:Boolean){}}
 private suspend inline fun <reified T:Throwable> fails(block:suspend()->Unit){try{block()}catch(e:Throwable){if(e is T)return;throw e};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
