package com.example.bspos.data.local.dao
import androidx.room.*
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.SaleStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
@Dao abstract class PaymentDao{
 @Query("SELECT * FROM payments WHERE remote_shop_id=:shopId AND tenancy_status='assigned' ORDER BY date DESC,id DESC") abstract fun observeAll(shopId:String?=null):Flow<List<PaymentEntity>>
 @Query("SELECT * FROM payments WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND customer_id=:customerId ORDER BY date DESC,id DESC") abstract fun observeForCustomer(customerId:UUID,shopId:String?=null):Flow<List<PaymentEntity>>
 @Query("SELECT * FROM payment_allocations WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND payment_id=:paymentId ORDER BY sale_id") abstract fun observeAllocations(paymentId:UUID,shopId:String?=null):Flow<List<PaymentAllocationEntity>>
 @Query("SELECT * FROM sales WHERE id=:id AND remote_shop_id=:shopId AND tenancy_status='assigned'") protected abstract suspend fun findSale(id:UUID,shopId:String?):SaleEntity?
 @Query("SELECT COALESCE(SUM(allocated_amount),0) FROM payment_allocations WHERE sale_id=:saleId AND remote_shop_id=:shopId AND tenancy_status='assigned'") protected abstract suspend fun allocatedAmount(saleId:UUID,shopId:String?):Long
 @Insert protected abstract suspend fun insertPayment(payment:PaymentEntity)
 @Insert protected abstract suspend fun insertAllocations(rows:List<PaymentAllocationEntity>)
 @Transaction open suspend fun insertWithAllocations(payment:PaymentEntity,allocations:List<PaymentAllocationEntity>){
  require(payment.receiptNumber.isNotBlank()&&payment.amount>0&&allocations.isNotEmpty())
  val shop=checkNotNull(payment.tenant.shopId){"El pago necesita tienda"}
  require(payment.tenant.status==TenantMetadata.ASSIGNED)
  require(allocations.all{it.tenant.shopId==null||it.tenant.shopId==shop}){"Asignación de otra tienda"}
  require(allocations.map{it.saleId}.distinct().size==allocations.size&&allocations.all{it.paymentId==payment.id&&it.allocatedAmount>0})
  require(allocations.sumOf{it.allocatedAmount}==payment.amount)
  allocations.forEach{val sale=checkNotNull(findSale(it.saleId,shop)){"Sale not found in shop"};require(sale.customerId==payment.customerId&&sale.status==SaleStatus.COMPLETED&&it.allocatedAmount<=sale.pendingAmount-allocatedAmount(it.saleId,shop))}
  insertPayment(payment);insertAllocations(allocations.map{it.copy(tenant=TenantMetadata.assigned(shop))})
 }
}
