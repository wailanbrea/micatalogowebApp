package com.example.bspos.domain.model

import java.time.Instant
import java.util.UUID

enum class CashSessionStatus { OPEN, CLOSED }
enum class CashMovementType { INCOME, EXPENSE, SALE, PAYMENT_COLLECTION, REFUND }
data class CashSession(val id:UUID,val openedAt:Instant,val closedAt:Instant?=null,val openingAmount:Long,val expectedAmount:Long?=null,val actualAmount:Long?=null,val difference:Long?=null,val status:CashSessionStatus=CashSessionStatus.OPEN,val notes:String?=null)
data class CashMovement(val id:UUID,val sessionId:UUID,val type:CashMovementType,val amount:Long,val reason:String,val createdAt:Instant)
