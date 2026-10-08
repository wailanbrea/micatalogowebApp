package com.example.bspos.data.local.dao
import androidx.room.*
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.SaleStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
@Dao abstract class PaymentDao{
 @Query("SELECT * FROM payments ORDER BY date DESC,id DESC") abstract fun observeAll():Flow<List<PaymentEntity>>
 @Query("SELECT * FROM payments WHERE customer_id=:customerId ORDER BY date DESC,id DESC") abstract fun observeForCustomer(customerId:UUID):Flow<List<PaymentEntity>>
 @Query("SELECT * FROM payment_allocations WHERE payment_id=:paymentId ORDER BY sale_id") abstract fun observeAllocations(paymentId:UUID):Flow<List<PaymentAllocationEntity>>
 @Query("SELECT * FROM sales WHERE id=:id") protected abstract suspend fun findSale(id:UUID):SaleEntity?
 @Query("SELECT COALESCE(SUM(allocated_amount),0) FROM payment_allocations WHERE sale_id=:saleId") protected abstract suspend fun allocatedAmount(saleId:UUID):Long
 @Insert protected abstract suspend fun insertPayment(payment:PaymentEntity)
 @Insert protected abstract suspend fun insertAllocations(rows:List<PaymentAllocationEntity>)
 @Transaction open suspend fun insertWithAllocations(payment:PaymentEntity,allocations:List<PaymentAllocationEntity>){
  require(payment.receiptNumber.isNotBlank()&&payment.amount>0&&allocations.isNotEmpty())
  require(allocations.map{it.saleId}.distinct().size==allocations.size&&allocations.all{it.paymentId==payment.id&&it.allocatedAmount>0})
  require(allocations.sumOf{it.allocatedAmount}==payment.amount)
  allocations.forEach{val sale=checkNotNull(findSale(it.saleId)){"Sale not found"};require(sale.customerId==payment.customerId&&sale.status==SaleStatus.COMPLETED&&it.allocatedAmount<=sale.pendingAmount-allocatedAmount(it.saleId))}
  insertPayment(payment);insertAllocations(allocations)
 }
}
