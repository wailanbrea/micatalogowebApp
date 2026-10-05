package com.example.bspos.domain.model
import java.time.Instant
import java.util.UUID
enum class PaymentMethod { CASH, TRANSFER, CHECK, CARD }
data class Payment(val id:UUID,val receiptNumber:String,val customerId:UUID,val routeId:UUID?=null,val date:Instant,val amount:Long,val method:PaymentMethod,val reference:String?=null,val notes:String?=null,val createdAt:Instant)
data class PaymentAllocation(val id:UUID,val paymentId:UUID,val saleId:UUID,val allocatedAmount:Long)
