package com.example.bspos.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.database.RoomDatabaseTransactor
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.*
import com.example.bspos.data.micatalogo.*
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CompleteCashReturnUseCaseTest {
    private lateinit var db: AppDatabase
    private lateinit var useCase: CompleteCashReturnUseCase
    private lateinit var product: ProductEntity
    private val at = Instant.parse("2026-10-04T10:00:00Z")

    @Before fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
        val category = UUID.randomUUID(); val unit = UUID.randomUUID()
        db.categoryDao().insert(CategoryEntity(category,"Prueba",createdAt=at,updatedAt=at))
        db.unitOfMeasureDao().insert(UnitOfMeasureEntity(unit,"Unidad","ud",createdAt=at,updatedAt=at))
        product = ProductEntity(UUID.randomUUID(),"Producto","REFUND-TEST",categoryId=category,unitId=unit,
            salePrice=100,averageCost=999,lastPurchaseCost=999,miCatalogoShopId="shop",miCatalogoProductId="remote",
            miCatalogoSaleUnit="unit",createdAt=at,updatedAt=at)
        db.productDao().insert(product)
        val recorder = RemoteMutationRecorder(RoomDatabaseTransactor(db),db.categoryDao(),db.operationOutboxDao(),PosSaleSyncScheduler(context))
        useCase = CompleteCashReturnUseCase(RoomDatabaseTransactor(db),db.returnDao(),db.saleDao(),db.inventoryDao(),
            db.cashSessionDao(),SafeSettings,db.productDao(),db.posSaleOutboxDao(),recorder)
    }
    @After fun close() = db.close()

    @Test fun discountedPartialReturnsKeepCentsAndOriginalCostAndCannotRepeat() = runTest {
        stock(product.id,7)
        val (sale,item) = sale(3,discount=1)
        val session = UUID.randomUUID()
        db.cashSessionDao().open(CashSessionEntity(session,at,openingAmount=1000))
        val first = useCase(request(sale,item,1,"R-1"))
        val second = useCase(request(sale,item,2,"R-2"))
        Assert.assertEquals(99L,first.totalAmount); Assert.assertEquals(200L,second.totalAmount)
        Assert.assertEquals(299L,db.cashSessionDao().observeMovements(session).first().sumOf { it.amount })
        Assert.assertEquals(10L,db.inventoryDao().findStock(product.id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
        Assert.assertEquals(50L,db.returnDao().observeItems(first.id).first().single().unitCostSnapshot)
        Assert.assertEquals(999L,db.productDao().findById(product.id)!!.lastPurchaseCost)
        Assert.assertTrue(db.operationOutboxDao().oldestActive("shop")!!.payload.contains("0.99"))
        Assert.assertTrue(runCatching { useCase(request(sale,item,1,"R-3")) }.isFailure)
        Assert.assertEquals(2,db.operationOutboxDao().activeForShop("shop").size)
        Assert.assertEquals(2,db.returnDao().observeForSale(sale.id).first().size)
    }

    @Test fun absentCashSessionRollsBackRefundStockAndQueue() = runTest {
        stock(product.id,7)
        val (sale,item) = sale(3)
        Assert.assertTrue(runCatching { useCase(request(sale,item,1,"R-NOCASH")) }.isFailure)
        Assert.assertTrue(db.returnDao().observeForSale(sale.id).first().isEmpty())
        Assert.assertTrue(db.operationOutboxDao().activeForShop("shop").isEmpty())
        Assert.assertEquals(7L,db.inventoryDao().findStock(product.id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
    }

    @Test fun decantReturnRestoresSourceAndSiblingsWithCapturedCost() = runTest {
        val source = product.copy(id=UUID.randomUUID(),internalCode="SOURCE",miCatalogoProductId="source",
            miCatalogoSaleUnit="bottle",miCatalogoVolumeMl=100,miCatalogoAvailableMl=90)
        db.productDao().insert(source); stock(source.id,0)
        product = product.copy(miCatalogoSaleUnit="decant",miCatalogoVolumeMl=5,miCatalogoSourceProductId="source")
        db.productDao().update(product); stock(product.id,18)
        val sibling = product.copy(id=UUID.randomUUID(),internalCode="SIBLING",miCatalogoProductId="sibling",miCatalogoVolumeMl=10)
        db.productDao().insert(sibling); stock(sibling.id,9)
        val (sale,item) = sale(2)
        db.cashSessionDao().open(CashSessionEntity(UUID.randomUUID(),at,openingAmount=1000))
        useCase(request(sale,item,2,"R-DECANT"))
        Assert.assertEquals(100,db.productDao().findById(source.id)!!.miCatalogoAvailableMl)
        Assert.assertEquals(1L,db.inventoryDao().findStock(source.id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
        Assert.assertEquals(20L,db.inventoryDao().findStock(product.id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
        Assert.assertEquals(10L,db.inventoryDao().findStock(sibling.id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN")!!.quantity)
        Assert.assertEquals(50L,db.inventoryDao().observeMovements(product.id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN",null,null).first().first().unitCost)
        Assert.assertEquals(1,db.operationOutboxDao().activeForShop("shop").size)
    }

    @Test fun changedPresentationRequiresReconciliationBeforeRestocking() = runTest {
        product = product.copy(miCatalogoSaleUnit="decant",miCatalogoVolumeMl=5,miCatalogoSourceProductId="source")
        db.productDao().update(product)
        val (sale,item) = sale(1)
        db.productDao().update(product.copy(miCatalogoVolumeMl=6))
        db.cashSessionDao().open(CashSessionEntity(UUID.randomUUID(),at,openingAmount=1000))
        Assert.assertTrue(runCatching { useCase(request(sale,item,1,"R-CHANGED")) }.isFailure)
        Assert.assertTrue(db.returnDao().observeForSale(sale.id).first().isEmpty())
        Assert.assertTrue(db.operationOutboxDao().activeForShop("shop").isEmpty())
        val refund = useCase(request(sale,item,1,"R-NORESTOCK").copy(lines=listOf(CashReturnLineInput(item.id,product.id,1,100,50,false))))
        Assert.assertEquals(100L,refund.totalAmount)
    }

    private suspend fun stock(id:UUID,quantity:Long) {
        // Zero stocks need no ledger row; the recorder starts from zero.
        if (quantity == 0L) return
        db.inventoryDao().recordMovements(listOf(InventoryMovementEntity(UUID.randomUUID(),id,InventoryLocationType.MAIN_WAREHOUSE,"MAIN",
            InventoryMovementType.INITIAL,quantity,0,quantity,50,quantity*50,createdAt=at)),false)
    }
    private suspend fun sale(quantity:Long,discount:Long=0):Pair<SaleEntity,SaleItemEntity> {
        val sale=SaleEntity(UUID.randomUUID(),"F-${UUID.randomUUID()}",date=at,subtotal=quantity*100,discount=discount,
            total=quantity*100-discount,paymentType=SalePaymentType.CASH,paidAmount=quantity*100-discount,createdAt=at,updatedAt=at)
        val item=SaleItemEntity(UUID.randomUUID(),sale.id,product.id,quantity,100,50,subtotal=quantity*100,
            saleUnitSnapshot=product.miCatalogoSaleUnit,volumeMlSnapshot=product.miCatalogoVolumeMl,remoteSourceSnapshot=product.miCatalogoSourceProductId)
        db.saleDao().insertWithItems(sale,listOf(item))
        db.posSaleOutboxDao().insert(PosSaleOutboxEntity(sale.id,"shop","{}",nextAttemptAt=at,createdAt=at,updatedAt=at))
        return sale to item
    }
    private fun request(sale:SaleEntity,item:SaleItemEntity,quantity:Long,number:String)=CompleteCashReturnRequest(number,sale.id,at.plusSeconds(1),
        listOf(CashReturnLineInput(item.id,product.id,quantity,item.unitPrice,item.unitCostSnapshot,true)),"Prueba",refundChargedAmount=true)
    private object SafeSettings:SettingsRepository {
        override fun observe()=flowOf(AppSettings())
        override suspend fun setAllowNegativeStock(enabled:Boolean){}
        override suspend fun setAutomaticBackupsEnabled(enabled:Boolean){}
        override suspend fun setRoutesEnabled(enabled:Boolean){}
    }
}
