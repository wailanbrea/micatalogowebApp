package com.example.bspos.domain.usecase

import com.example.bspos.data.local.dao.CashSessionDao
import com.example.bspos.data.local.entity.CashSessionEntity
import com.example.bspos.data.local.entity.CashMovementEntity
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.core.database.ShopScope
import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.domain.repository.FinanceRepository
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.CashSessionStatus
import com.example.bspos.data.micatalogo.MiCatalogoImportMapper
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import com.example.bspos.domain.model.CashMovement
import com.example.bspos.domain.model.CashMovementType
import com.example.bspos.domain.model.CashSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CashSessionUseCases @Inject constructor(
    private val cash:CashSessionDao, private val shops:ShopScope,
    private val finance:FinanceRepository, private val transactor:AppDatabaseTransactor
) {
    fun observeOpen():Flow<CashSession?> = shops.observeValue { shop ->
        cash.observeOpen(shop).onStart { if (shop!=null) refresh(shop) }
    }.map { it?.toDomain() }
    fun observeMovements(id:UUID):Flow<List<CashMovement>> = shops.observe { cash.observeMovements(id,it) }.map { rows -> rows.map { it.toDomain() } }

    suspend fun open(amount:Long,notes:String?=null) = shops.mutate { shop ->
        require(amount>=0)
        val result=finance.openCashSession(shop,MiCatalogoPosSaleOutboxMapper.decimalPrice(amount),notes,UUID.randomUUID().toString())
        requireSuccess(result)
        check(refresh(shop)) { "La caja se abrió en el servidor; vuelve a sincronizar para confirmar su estado." }
    }

    suspend fun close(id:UUID,actualAmount:Long,notes:String?=null):CashSession = shops.mutate { shop ->
        require(actualAmount>=0)
        val current=checkNotNull(cash.find(id,shop)) { "La caja no pertenece a la tienda activa" }
        val remoteId=checkNotNull(current.remoteSessionId) { "Esta caja histórica requiere conciliación" }
        require(current.status==CashSessionStatus.OPEN)
        val result=requireSuccess(finance.closeCashSession(shop,remoteId,
            MiCatalogoPosSaleOutboxMapper.decimalPrice(actualAmount),notes,UUID.randomUUID().toString()))
        val closed=checkNotNull(result.session) { "El servidor no devolvió la caja cerrada" }
        require(closed.id==remoteId && closed.status.equals("closed",ignoreCase=true))
        val projection=current.copy(closedAt=Instant.parse(checkNotNull(closed.closedAt)),
            expectedAmount=closed.expectedClosingAmount?.let(::cents),actualAmount=closed.countedClosingAmount?.let(::cents),
            difference=closed.difference?.let(::cents),status=CashSessionStatus.CLOSED,notes=closed.notes ?: current.notes)
        cash.applyRemote(projection)
        projection.toDomain()
    }

    private suspend fun refresh(shop:String):Boolean {
        val response=finance.getCurrentCashSession(shop)
        if(response is MiCatalogoResult.Failure) return false // Keep only a same-shop verified offline mirror.
        val current=(response as MiCatalogoResult.Success).value
        val session=current.session?.takeIf { current.hasOpenSession && it.status.equals("open",ignoreCase=true) }
        if(current.hasOpenSession) require(session!=null && session.id.isNotBlank()) { "La respuesta de caja es inconsistente" }
        shops.mutate { active ->
            if(active!=shop) throw CancellationException("La tienda cambió durante la consulta de caja")
            transactor.runInTransaction {
                cash.invalidateRemoteOpen(shop)
                session?.let {
                    cash.applyRemote(CashSessionEntity(UUID.nameUUIDFromBytes("micatalogo:cash:$shop:${it.id}".toByteArray(Charsets.UTF_8)),
                        Instant.parse(it.openedAt),openingAmount=cents(it.openingAmount),
                        expectedAmount=it.summary?.expectedClosingAmount?.let(::cents),notes=it.notes,
                        remoteSessionId=it.id,tenant=TenantMetadata.assigned(shop)))
                }
            }
        }
        return true
    }

    private fun cents(value:Double)=MiCatalogoImportMapper.cents(value.toString())
    private fun <T> requireSuccess(result:MiCatalogoResult<T>):T = when(result) {
        is MiCatalogoResult.Success -> result.value
        is MiCatalogoResult.Failure -> error(result.message)
    }
    private fun CashSessionEntity.toDomain()=CashSession(id,openedAt,closedAt,openingAmount,expectedAmount,actualAmount,difference,status,notes)
    private fun CashMovementEntity.toDomain()=CashMovement(id,sessionId,type,amount,reason,createdAt)
}
