package com.example.bspos.data.local.dao

import androidx.room.*
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao abstract class CashSessionDao {
 @Query("SELECT * FROM cash_sessions WHERE status='OPEN' LIMIT 1") abstract fun observeOpen():Flow<CashSessionEntity?>
 @Query("SELECT id FROM cash_sessions WHERE status='OPEN' LIMIT 1") abstract suspend fun findOpenId():UUID?
 @Query("SELECT * FROM cash_movements WHERE session_id=:sessionId ORDER BY created_at,id") abstract fun observeMovements(sessionId:UUID):Flow<List<CashMovementEntity>>
 @Query("SELECT * FROM cash_sessions WHERE id=:id") protected abstract suspend fun find(id:UUID):CashSessionEntity?
 @Query("SELECT COUNT(*) FROM cash_sessions WHERE status='OPEN'") protected abstract suspend fun openCount():Int
 @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('EXPENSE','REFUND') THEN -amount ELSE amount END),0) FROM cash_movements WHERE session_id=:sessionId") protected abstract suspend fun movementBalance(sessionId:UUID):Long
 @Insert protected abstract suspend fun insertSession(value:CashSessionEntity)
 @Insert protected abstract suspend fun insertMovement(value:CashMovementEntity)
 @Update protected abstract suspend fun updateSession(value:CashSessionEntity):Int
 @Transaction open suspend fun open(value:CashSessionEntity){require(value.status==CashSessionStatus.OPEN&&value.openingAmount>=0&&value.closedAt==null&&value.expectedAmount==null&&value.actualAmount==null&&value.difference==null&&openCount()==0);insertSession(value)}
 @Transaction open suspend fun recordMovement(value:CashMovementEntity){require(value.amount>0&&value.reason.isNotBlank());require(find(value.sessionId)?.status==CashSessionStatus.OPEN);insertMovement(value)}
 @Transaction open suspend fun close(sessionId:UUID,actualAmount:Long,closedAt:Instant,notes:String?=null):CashSessionEntity{require(actualAmount>=0);val current=checkNotNull(find(sessionId)){"Cash session not found"};require(current.status==CashSessionStatus.OPEN&&closedAt>=current.openedAt);val expected=current.openingAmount+movementBalance(sessionId);return current.copy(closedAt=closedAt,expectedAmount=expected,actualAmount=actualAmount,difference=actualAmount-expected,status=CashSessionStatus.CLOSED,notes=notes?:current.notes).also{check(updateSession(it)==1)}}
}
