package com.example.bspos.data.local.dao
import androidx.room.*
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class SaleCostTotal(@ColumnInfo(name = "sale_id") val saleId: UUID, val cost: Long)

@Dao abstract class SaleDao {
 @Query("SELECT * FROM sales WHERE id=:id AND remote_shop_id=:shopId AND tenancy_status='assigned'") abstract suspend fun findById(id:UUID,shopId:String?=null):SaleEntity?
 @Query("SELECT * FROM sales WHERE remote_shop_id=:shopId AND tenancy_status='assigned' ORDER BY date DESC,id DESC") abstract fun observeAll(shopId:String?=null):Flow<List<SaleEntity>>
 @Query("SELECT i.* FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE i.sale_id=:saleId AND i.remote_shop_id=:shopId AND i.tenancy_status='assigned' AND s.remote_shop_id=:shopId AND s.tenancy_status='assigned' ORDER BY i.rowid") abstract fun observeItems(saleId:UUID,shopId:String?=null):Flow<List<SaleItemEntity>>
 @Query("SELECT i.* FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE i.remote_shop_id=:shopId AND i.tenancy_status='assigned' AND s.remote_shop_id=:shopId AND s.tenancy_status='assigned' ORDER BY i.rowid") abstract fun observeAllItems(shopId:String?=null):Flow<List<SaleItemEntity>>
 @Query("SELECT i.sale_id, SUM(i.quantity * i.unit_cost_snapshot) AS cost FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE i.remote_shop_id=:shopId AND i.tenancy_status='assigned' AND s.remote_shop_id=:shopId AND s.tenancy_status='assigned' GROUP BY i.sale_id") abstract fun observeCostTotals(shopId:String?=null):Flow<List<SaleCostTotal>>
 @Query("SELECT i.* FROM sale_items i JOIN sales s ON s.id=i.sale_id WHERE i.sale_id=:saleId AND i.remote_shop_id=:shopId AND i.tenancy_status='assigned' AND s.remote_shop_id=:shopId AND s.tenancy_status='assigned' ORDER BY i.rowid") abstract suspend fun itemsInInvoiceOrder(saleId:UUID,shopId:String?=null):List<SaleItemEntity>
 @Insert protected abstract suspend fun insertSale(sale:SaleEntity)
 @Insert protected abstract suspend fun insertItems(items:List<SaleItemEntity>)
 @Query("UPDATE sales SET paid_amount=:paidAmount,pending_amount=:pendingAmount,updated_at=:updatedAt WHERE id=:saleId AND remote_shop_id=:shopId AND tenancy_status='assigned'") protected abstract suspend fun updateBalances(saleId:UUID,paidAmount:Long,pendingAmount:Long,updatedAt:java.time.Instant,shopId:String?):Int
 @Transaction open suspend fun applyPayment(saleId:UUID,amount:Long,at:java.time.Instant,shopId:String?=null){val sale=checkNotNull(findById(saleId,shopId)){"Sale not found in shop"};require(amount>0&&amount<=sale.pendingAmount);check(updateBalances(saleId,Math.addExact(sale.paidAmount,amount),Math.subtractExact(sale.pendingAmount,amount),at,shopId)==1)}
 @Transaction open suspend fun insertWithItems(sale:SaleEntity,items:List<SaleItemEntity>){
  require(sale.invoiceNumber.isNotBlank()&&items.isNotEmpty())
  val shop=checkNotNull(sale.tenant.shopId){"La venta necesita tienda"}
  require(sale.tenant.status==TenantMetadata.ASSIGNED)
  require(items.all{it.tenant.shopId==null||it.tenant.shopId==shop}){"Las líneas pertenecen a otra tienda"}
  require(items.map{it.productId}.distinct().size==items.size&&items.all{it.saleId==sale.id})
  require(sale.discount>=0&&sale.tax>=0&&sale.paidAmount>=0&&sale.pendingAmount>=0)
  require(Math.addExact(sale.paidAmount,sale.pendingAmount)==sale.total)
  if(sale.paymentType==SalePaymentType.CREDIT) require(sale.customerId!=null&&sale.paidAmount==0L)
  items.forEach{require(it.quantity>0&&it.unitPrice>=0&&it.unitCostSnapshot>=0&&it.discount>=0&&it.tax>=0);require(Math.addExact(Math.subtractExact(Math.multiplyExact(it.quantity,it.unitPrice),it.discount),it.tax)==it.subtotal)}
  require(items.sumOf{it.subtotal}==sale.subtotal)
  require(Math.addExact(Math.subtractExact(sale.subtotal,sale.discount),sale.tax)==sale.total)
  insertSale(sale);insertItems(items.map{it.copy(tenant=TenantMetadata.assigned(shop))})
 }
}
