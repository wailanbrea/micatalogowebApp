package com.example.bspos.data.local.dao

import androidx.room.*
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.SaleStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import com.example.bspos.domain.usecase.CashRefundAmounts
import com.example.bspos.domain.usecase.RefundableLine

data class ReturnedQuantity(val saleItemId:UUID, val quantity:Long)

@Dao abstract class ReturnDao {
 @Query("SELECT * FROM returns WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND sale_id=:saleId ORDER BY date DESC,id DESC") abstract fun observeForSale(saleId:UUID,shopId:String?=null):Flow<List<ReturnEntity>>
 @Query("SELECT * FROM return_items WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND return_id=:returnId ORDER BY sale_item_id") abstract fun observeItems(returnId:UUID,shopId:String?=null):Flow<List<ReturnItemEntity>>
 @Query("SELECT * FROM sales WHERE id=:id AND remote_shop_id=:shopId AND tenancy_status='assigned'") protected abstract suspend fun findSale(id:UUID,shopId:String?):SaleEntity?
 @Query("SELECT * FROM sale_items WHERE id=:id AND remote_shop_id=:shopId AND tenancy_status='assigned'") protected abstract suspend fun findSaleItem(id:UUID,shopId:String?):SaleItemEntity?
 @Query("SELECT COALESCE(SUM(quantity),0) FROM return_items WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND sale_item_id=:saleItemId") abstract suspend fun returnedQuantity(saleItemId:UUID,shopId:String?=null):Long
 @Query("SELECT sale_item_id AS saleItemId,SUM(quantity) AS quantity FROM return_items JOIN returns ON returns.id=return_items.return_id WHERE returns.sale_id=:saleId AND returns.remote_shop_id=:shopId AND returns.tenancy_status='assigned' AND return_items.remote_shop_id=:shopId AND return_items.tenancy_status='assigned' GROUP BY sale_item_id") abstract fun observeQuantities(saleId:UUID,shopId:String?=null):Flow<List<ReturnedQuantity>>
 @Query("SELECT * FROM sale_items WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND sale_id=:saleId ORDER BY rowid") protected abstract suspend fun invoiceLines(saleId:UUID,shopId:String?):List<SaleItemEntity>
 @Insert protected abstract suspend fun insertReturn(value:ReturnEntity)
 @Insert protected abstract suspend fun insertItems(values:List<ReturnItemEntity>)
 @Transaction open suspend fun insertWithItems(value:ReturnEntity,items:List<ReturnItemEntity>){
  require(value.returnNumber.isNotBlank()&&value.totalAmount>=0&&items.isNotEmpty())
  val shop=checkNotNull(value.tenant.shopId){"La devolución necesita tienda"}
  require(items.all{it.tenant.shopId==null||it.tenant.shopId==shop}){"Las líneas pertenecen a otra tienda"}
  val sale=checkNotNull(findSale(value.saleId,shop)){"Sale not found in shop"};require(sale.status==SaleStatus.COMPLETED&&value.customerId==sale.customerId)
  require(items.map{it.saleItemId}.distinct().size==items.size&&items.all{it.returnId==value.id&&it.quantity>0&&it.refundPrice>=0&&it.unitCostSnapshot>=0})
  require(items.fold(0L){sum,item->Math.addExact(sum,item.refundedCents)}==value.totalAmount)
  val charged=CashRefundAmounts.allocate(invoiceLines(sale.id,shop).map{RefundableLine(it.id.toString(),it.quantity,it.subtotal)},sale.discount,sale.tax)
  items.forEach{item->val sold=checkNotNull(findSaleItem(item.saleItemId,shop)){"Sale item not found in shop"};require(sold.saleId==value.saleId&&sold.productId==item.productId&&sold.unitCostSnapshot==item.unitCostSnapshot);require(item.refundedCents>=0&&item.refundedCents<=CashRefundAmounts.portion(charged.getValue(sold.id.toString()),sold.quantity,returnedQuantity(item.saleItemId,shop),item.quantity)){"El reembolso supera lo cobrado por esas unidades."}}
  insertReturn(value);insertItems(items.map{it.copy(tenant=value.tenant)})
 }
}
