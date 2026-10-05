package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

data class Return(val id:UUID,val returnNumber:String,val saleId:UUID,val customerId:UUID?=null,val date:Instant,val totalAmount:Long,val reason:String?=null,val createdAt:Instant)
data class ReturnItem(val id:UUID,val returnId:UUID,val saleItemId:UUID,val productId:UUID,val quantity:Long,val refundPrice:Long,val unitCostSnapshot:Long,val restockInventory:Boolean=true)
