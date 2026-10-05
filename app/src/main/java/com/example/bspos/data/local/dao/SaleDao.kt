package com.example.bspos.data.local.dao
import androidx.room.*
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
@Dao abstract class SaleDao {
 @Query("SELECT * FROM sales WHERE id=:id") abstract suspend fun findById(id:UUID):SaleEntity?
 @Query("SELECT * FROM sales ORDER BY date DESC,id DESC") abstract fun observeAll():Flow<List<SaleEntity>>
 @Query("SELECT * FROM sale_items WHERE sale_id=:saleId ORDER BY rowid") abstract fun observeItems(saleId:UUID):Flow<List<SaleItemEntity>>
 @Query("SELECT * FROM sale_items WHERE sale_id=:saleId ORDER BY rowid") abstract suspend fun itemsInInvoiceOrder(saleId:UUID):List<SaleItemEntity>
 @Insert protected abstract suspend fun insertSale(sale:SaleEntity)
 @Insert protected abstract suspend fun insertItems(items:List<SaleItemEntity>)
 @Query("UPDATE sales SET paid_amount=:paidAmount,pending_amount=:pendingAmount,updated_at=:updatedAt WHERE id=:saleId") protected abstract suspend fun updateBalances(saleId:UUID,paidAmount:Long,pendingAmount:Long,updatedAt:java.time.Instant):Int
 @Transaction open suspend fun applyPayment(saleId:UUID,amount:Long,at:java.time.Instant){val sale=checkNotNull(findById(saleId)){"Sale not found"};require(amount>0&&amount<=sale.pendingAmount);check(updateBalances(saleId,Math.addExact(sale.paidAmount,amount),Math.subtractExact(sale.pendingAmount,amount),at)==1)}
 @Transaction open suspend fun insertWithItems(sale:SaleEntity,items:List<SaleItemEntity>){
  require(sale.invoiceNumber.isNotBlank()&&items.isNotEmpty())
  require(items.map{it.productId}.distinct().size==items.size&&items.all{it.saleId==sale.id})
  require(sale.discount>=0&&sale.tax>=0&&sale.paidAmount>=0&&sale.pendingAmount>=0)
  require(Math.addExact(sale.paidAmount,sale.pendingAmount)==sale.total)
  if(sale.paymentType==SalePaymentType.CREDIT) require(sale.customerId!=null&&sale.paidAmount==0L)
  items.forEach{require(it.quantity>0&&it.unitPrice>=0&&it.unitCostSnapshot>=0&&it.discount>=0&&it.tax>=0);require(Math.addExact(Math.subtractExact(Math.multiplyExact(it.quantity,it.unitPrice),it.discount),it.tax)==it.subtotal)}
  require(items.sumOf{it.subtotal}==sale.subtotal)
  require(Math.addExact(Math.subtractExact(sale.subtotal,sale.discount),sale.tax)==sale.total)
  insertSale(sale);insertItems(items)
 }
}
