package com.example.bspos.data.local.dao

import androidx.room.*
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao abstract class CashSessionDao {
 @Query("SELECT * FROM cash_sessions WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND remote_session_id IS NOT NULL AND status='OPEN' ORDER BY opened_at DESC LIMIT 1") abstract fun observeOpen(shopId:String?=null):Flow<CashSessionEntity?>
 @Query("SELECT id FROM cash_sessions WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND remote_session_id IS NOT NULL AND status='OPEN' ORDER BY opened_at DESC LIMIT 1") abstract suspend fun findOpenId(shopId:String?=null):UUID?
 @Query("SELECT * FROM cash_movements WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND session_id=:sessionId ORDER BY created_at,id") abstract fun observeMovements(sessionId:UUID,shopId:String?=null):Flow<List<CashMovementEntity>>
 @Query("SELECT * FROM cash_sessions WHERE id=:id AND remote_shop_id=:shopId AND tenancy_status='assigned'") abstract suspend fun find(id:UUID,shopId:String?=null):CashSessionEntity?
 @Query("SELECT * FROM cash_sessions WHERE remote_session_id=:remoteId AND remote_shop_id=:shopId") abstract suspend fun findRemote(remoteId:String,shopId:String):CashSessionEntity?
 @Query("UPDATE cash_sessions SET tenancy_status='remote_unconfirmed' WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND remote_session_id IS NOT NULL AND status='OPEN'") abstract suspend fun invalidateRemoteOpen(shopId:String):Int
 @Query("SELECT COUNT(*) FROM cash_sessions WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND remote_session_id IS NOT NULL AND status='OPEN'") protected abstract suspend fun openCount(shopId:String?):Int
 @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('EXPENSE','REFUND') THEN -amount ELSE amount END),0) FROM cash_movements WHERE remote_shop_id=:shopId AND tenancy_status='assigned' AND session_id=:sessionId") protected abstract suspend fun movementBalance(sessionId:UUID,shopId:String?):Long
 @Insert protected abstract suspend fun insertSession(value:CashSessionEntity)
 @Insert protected abstract suspend fun insertMovement(value:CashMovementEntity)
 @Update protected abstract suspend fun updateSession(value:CashSessionEntity):Int
 @Transaction open suspend fun open(value:CashSessionEntity){require(value.tenant.shopId!=null&&!value.remoteSessionId.isNullOrBlank());require(value.status==CashSessionStatus.OPEN&&value.openingAmount>=0&&value.closedAt==null&&value.expectedAmount==null&&value.actualAmount==null&&value.difference==null&&openCount(value.tenant.shopId)==0);insertSession(value)}
 @Transaction open suspend fun recordMovement(value:CashMovementEntity){require(value.amount>0&&value.reason.isNotBlank());require(find(value.sessionId,value.tenant.shopId)?.status==CashSessionStatus.OPEN);insertMovement(value)}
 @Transaction open suspend fun close(sessionId:UUID,actualAmount:Long,closedAt:Instant,notes:String?=null,shopId:String?=null):CashSessionEntity{require(actualAmount>=0);val current=checkNotNull(find(sessionId,shopId)){"Cash session not found in shop"};require(current.status==CashSessionStatus.OPEN&&closedAt>=current.openedAt);val expected=current.openingAmount+movementBalance(sessionId,shopId);return current.copy(closedAt=closedAt,expectedAmount=expected,actualAmount=actualAmount,difference=actualAmount-expected,status=CashSessionStatus.CLOSED,notes=notes?:current.notes).also{check(updateSession(it)==1)}}
 @Transaction open suspend fun applyRemote(value:CashSessionEntity) {
     require(value.tenant.shopId!=null&&!value.remoteSessionId.isNullOrBlank())
     val existing=findRemote(value.remoteSessionId!!,value.tenant.shopId!!)
     if(existing==null) insertSession(value) else updateSession(value.copy(id=existing.id))
 }
}
