package com.example.bspos.data.local.entity

import androidx.room.*
import com.example.bspos.domain.model.CashMovementType
import com.example.bspos.domain.model.CashSessionStatus
import java.time.Instant
import java.util.UUID

@Entity(tableName="cash_sessions",indices=[Index(value=["status"],name="idx_cash_sessions_status"),Index(value=["opened_at"],name="idx_cash_sessions_opened_at")])
data class CashSessionEntity(@PrimaryKey val id:UUID,@ColumnInfo(name="opened_at") val openedAt:Instant,@ColumnInfo(name="closed_at") val closedAt:Instant?=null,@ColumnInfo(name="opening_amount") val openingAmount:Long,@ColumnInfo(name="expected_amount") val expectedAmount:Long?=null,@ColumnInfo(name="actual_amount") val actualAmount:Long?=null,val difference:Long?=null,val status:CashSessionStatus=CashSessionStatus.OPEN,val notes:String?=null)
@Entity(tableName="cash_movements",foreignKeys=[ForeignKey(entity=CashSessionEntity::class,parentColumns=["id"],childColumns=["session_id"],onUpdate=ForeignKey.CASCADE,onDelete=ForeignKey.CASCADE)],indices=[Index(value=["session_id"],name="idx_cash_movements_session_id"),Index(value=["created_at"],name="idx_cash_movements_created_at")])
data class CashMovementEntity(@PrimaryKey val id:UUID,@ColumnInfo(name="session_id") val sessionId:UUID,val type:CashMovementType,val amount:Long,val reason:String,@ColumnInfo(name="created_at") val createdAt:Instant)
