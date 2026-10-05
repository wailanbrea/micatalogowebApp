package com.example.bspos.domain.usecase

import com.example.bspos.data.local.dao.CashSessionDao
import com.example.bspos.data.local.entity.CashSessionEntity
import com.example.bspos.data.local.entity.CashMovementEntity
import com.example.bspos.domain.model.CashMovement
import com.example.bspos.domain.model.CashMovementType
import com.example.bspos.domain.model.CashSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CashSessionUseCases @Inject constructor(private val cash:CashSessionDao){fun observeOpen():Flow<CashSession?> = cash.observeOpen().map{it?.toDomain()};fun observeMovements(id:UUID):Flow<List<CashMovement>> = cash.observeMovements(id).map{rows->rows.map{it.toDomain()}};suspend fun open(amount:Long,notes:String?=null){cash.open(CashSessionEntity(UUID.randomUUID(),Instant.now(),openingAmount=amount,notes=notes))};suspend fun close(id:UUID,actualAmount:Long,notes:String?=null):CashSession=cash.close(id,actualAmount,Instant.now(),notes).toDomain();private fun CashSessionEntity.toDomain()=CashSession(id,openedAt,closedAt,openingAmount,expectedAmount,actualAmount,difference,status,notes);private fun CashMovementEntity.toDomain()=CashMovement(id,sessionId,type,amount,reason,createdAt)}
