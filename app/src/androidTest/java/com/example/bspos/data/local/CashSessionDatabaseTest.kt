package com.example.bspos.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.local.dao.CashSessionDao
import com.example.bspos.data.local.entity.*
import com.example.bspos.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CashSessionDatabaseTest {
 private lateinit var db:AppDatabase;private lateinit var cash:CashSessionDao
 private val at=Instant.parse("2026-09-21T02:00:00Z")
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build();cash=db.cashSessionDao()}
 @After fun close()=db.close()
 @Test fun openingMovementsAndCloseCalculateExpectedAmount()=runTest{val session=CashSessionEntity(UUID.randomUUID(),at,openingAmount=1_000);cash.open(session);val sale=CashMovementEntity(UUID.randomUUID(),session.id,CashMovementType.SALE,500,"Factura",at.plusSeconds(1));val expense=CashMovementEntity(UUID.randomUUID(),session.id,CashMovementType.EXPENSE,200,"Cambio",at.plusSeconds(2));cash.recordMovement(sale);cash.recordMovement(expense);val closed=cash.close(session.id,1_250,at.plusSeconds(3));Assert.assertEquals(1_300L,closed.expectedAmount);Assert.assertEquals(-50L,closed.difference);Assert.assertEquals(CashSessionStatus.CLOSED,closed.status);Assert.assertNull(cash.observeOpen().first());Assert.assertEquals(listOf(sale,expense),cash.observeMovements(session.id).first())}
 @Test fun oneOpenSessionAndNoMovementAfterClose()=runTest{val session=CashSessionEntity(UUID.randomUUID(),at,openingAmount=0);cash.open(session);fails<IllegalArgumentException>{cash.open(CashSessionEntity(UUID.randomUUID(),at,openingAmount=0))};cash.close(session.id,0,at.plusSeconds(1));fails<IllegalArgumentException>{cash.recordMovement(CashMovementEntity(UUID.randomUUID(),session.id,CashMovementType.INCOME,1,"late",at.plusSeconds(2)))};fails<IllegalArgumentException>{cash.close(session.id,0,at.plusSeconds(2))}}
 @Test fun invalidOpeningAndMovementAreRejected()=runTest{fails<IllegalArgumentException>{cash.open(CashSessionEntity(UUID.randomUUID(),at,openingAmount=-1))};val session=CashSessionEntity(UUID.randomUUID(),at,openingAmount=0);cash.open(session);fails<IllegalArgumentException>{cash.recordMovement(CashMovementEntity(UUID.randomUUID(),session.id,CashMovementType.INCOME,0,"",at))};Assert.assertTrue(cash.observeMovements(session.id).first().isEmpty())}
 private suspend inline fun <reified T:Throwable> fails(block:suspend()->Unit){try{block()}catch(e:Throwable){if(e is T)return;throw e};throw AssertionError("Expected ${T::class.java.simpleName}")}
}
