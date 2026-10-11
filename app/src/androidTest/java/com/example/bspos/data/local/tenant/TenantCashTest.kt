package com.example.bspos.data.local.tenant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.database.*
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.CashSessionStatus
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.FinanceRepository
import com.example.bspos.domain.usecase.CashSessionUseCases
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TenantCashTest {
    @Test fun historicalLocalOpenBoxCannotOverrideServerWithoutOpenSession() = runTest {
        val environment=Environment()
        try {
            environment.select("qa-a")
            val id=UUID.randomUUID().toString()
            val sql=environment.db.openHelper.writableDatabase
            sql.execSQL("INSERT INTO cash_sessions(id,opened_at,opening_amount,status,remote_shop_id,tenancy_status) VALUES (?,1234,8765,'OPEN','qa-a','assigned')",arrayOf(id))
            assertNull(environment.cash.observeOpen().drop(1).first())
            assertEquals(1,environment.currentQueries)
            assertNull(environment.db.cashSessionDao().findOpenId("qa-a"))
            sql.query("SELECT opening_amount,status FROM cash_sessions WHERE id=?",arrayOf(id)).use {
                assertTrue(it.moveToFirst()); assertEquals(8765L,it.getLong(0)); assertEquals("OPEN",it.getString(1))
            }
        } finally { environment.db.close() }
    }

    @Test fun rejectLeavesNoPhantomAndTwoRemoteBoxesNeverCrossShop() = runTest {
        val environment=Environment()
        try {
            environment.select("qa-a")
            environment.rejectOpen=true
            assertTrue(runCatching { environment.cash.open(1000) }.isFailure)
            assertNull(environment.db.cashSessionDao().findOpenId("qa-a"))
            environment.rejectOpen=false
            environment.cash.open(1000)
            val a=environment.db.cashSessionDao().observeOpen("qa-a").first()!!
            environment.select("qa-b")
            assertNull(environment.cash.observeOpen().drop(1).first())
            environment.cash.open(1000)
            val b=environment.db.cashSessionDao().observeOpen("qa-b").first()!!
            assertNotEquals(a.id,b.id)
            assertTrue(runCatching { environment.cash.close(a.id,1000) }.isFailure)
            assertEquals(0,environment.closeCalls)
            val closed=environment.cash.close(b.id,1000)
            assertEquals(CashSessionStatus.CLOSED,closed.status)
            assertEquals(1000L,closed.actualAmount)
            assertEquals(CashSessionStatus.OPEN,environment.db.cashSessionDao().find(a.id,"qa-a")!!.status)
            assertNull(environment.db.cashSessionDao().findOpenId("qa-b"))
        } finally { environment.db.close() }
    }

    private class Environment {
        private val preferences=object:DataStore<Preferences> {
            val values=MutableStateFlow<Preferences>(emptyPreferences())
            override val data:Flow<Preferences> = values
            override suspend fun updateData(transform:suspend(Preferences)->Preferences)=transform(values.value).also { values.value=it }
        }
        private val shops=ShopScope(preferences,ShopSwitchLock())
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java)
            .addCallback(InventoryIntegrity).addCallback(TenantIntegrity).build()
        var rejectOpen=false
        var closeCalls=0
        var currentQueries=0
        private val opened=mutableSetOf<String>()
        private val finance=Proxy.newProxyInstance(FinanceRepository::class.java.classLoader,arrayOf(FinanceRepository::class.java)) { _,method,args ->
            val shop=args!![0] as String
            when(method.name) {
                "getCurrentCashSession" -> {
                    currentQueries++
                    MiCatalogoResult.Success(CashCurrentSessionResponseDto(
                        session=if(shop in opened) CashSessionDetailDto("same-remote-id","2026-10-10T12:00:00Z",10.0,"open") else null,
                        hasOpenSession=shop in opened))
                }
                "openCashSession" -> if(rejectOpen) MiCatalogoResult.Failure("Servidor rechazó apertura") else {
                    opened+=shop
                    MiCatalogoResult.Success(CashSessionActionResponseDto(session=CashSessionActionDetailDto("same-remote-id",status="open")))
                }
                "closeCashSession" -> {
                    closeCalls++
                    opened-=shop
                    MiCatalogoResult.Success(CashSessionActionResponseDto(session=CashSessionActionDetailDto("same-remote-id",
                        closedAt="2026-10-10T13:00:00Z",expectedClosingAmount=10.0,countedClosingAmount=10.0,difference=0.0,status="closed")))
                }
                else -> error("Unexpected finance call: ${method.name}")
            }
        } as FinanceRepository
        val cash=CashSessionUseCases(db.cashSessionDao(),shops,finance,RoomDatabaseTransactor(db,shops))
        suspend fun select(shop:String) { preferences.edit { it[ShopScope.ACTIVE_SHOP_ID]=shop } }
    }
}
