package com.example.bspos.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.data.local.entity.OperationOutboxEntity
import com.example.bspos.data.micatalogo.OperationSubmission
import com.example.bspos.data.micatalogo.OperationSyncRepository
import com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class FinancialOutboxTest {
    @Test fun reconciliationAckRequiresTheRequestedClosureCountAndReason() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = java.util.UUID.randomUUID().toString()
            val row = OperationOutboxEntity(id, "shop-1", "",
                """{"type":"day_close_adjustment","resource_id":"closure-1","client_operation_uuid":"$id","request":{"reason":"QA count","counted_cash":"125.00"}}""", Instant.now())
            val dao = db.operationOutboxDao()
            dao.insert(row)
            var closure = "wrong"
            var count = "125.00"
            var reason = "QA count"
            val api = object : MiCatalogoOperationApi {
                override suspend fun submitAccount(payload: JsonObject): Response<JsonObject> = error("Not an account operation")
                override suspend fun submit(shopId: String, payload: JsonObject): Response<JsonObject> = Response.success(buildJsonObject {
                    put("client_operation_uuid", id)
                    put("adjustment", buildJsonObject { put("id", "adjustment-1"); put("closure_id", closure)
                        put("expected_cash", "120.00"); put("counted_cash", count); put("reason", reason) })
                })
            }
            val sync = OperationSyncRepository(api, dao, Json)
            assertEquals(OperationSubmission.RETRY, sync.submit(dao.find(id)!!))
            closure = "closure-1"; count = "126.00"
            assertEquals(OperationSubmission.RETRY, sync.submit(dao.find(id)!!))
            count = "125.00"; reason = "wrong reason"
            assertEquals(OperationSubmission.RETRY, sync.submit(dao.find(id)!!))
            reason = "QA count"
            assertEquals(OperationSubmission.SENT, sync.submit(dao.find(id)!!))
            assertEquals(row.payload, dao.find(id)!!.payload)
            assertEquals(1, dao.pendingRefreshes().size)
        } finally { db.close() }
    }

    @Test fun financialRetryPreservesPayloadAndAckConflictBlocks() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.operationOutboxDao()
            val id = "00000000-0000-4000-8000-000000000001"
            val row = OperationOutboxEntity(id, "shop-1", "",
                """{"type":"expense_payment","resource_id":"expense-1","client_operation_uuid":"$id","request":{"amount":"3000.00","payment_method":"cash","client_operation_uuid":"$id"}}""", Instant.now())
            dao.insert(row)
            var status = 500
            val requests = mutableListOf<String>()
            val api = object : MiCatalogoOperationApi {
                override suspend fun submitAccount(payload: JsonObject): Response<JsonObject> = Response.success(buildJsonObject {})

                override suspend fun submit(shopId: String, payload: JsonObject): Response<JsonObject> {
                    assertEquals("shop-1", shopId)
                    requests.add(payload.toString())
                    return if (status == 200) Response.success(buildJsonObject {
                        put("client_operation_uuid", id)
                        put("expense", buildJsonObject { put("id", "expense-1") })
                        put("payment", buildJsonObject { put("id", "payment-1") })
                    })
                    else Response.error(status, """{"message":"Retry or conflict"}""".toResponseBody())
                }
            }
            val sync = OperationSyncRepository(api, dao, Json)
            for (code in listOf(401, 426, 429, 500)) {
                status = code
                assertEquals(OperationSubmission.RETRY, sync.submit(dao.find(id)!!))
                assertEquals(row.payload, dao.find(id)!!.payload)
            }
            for (code in listOf(403, 409, 422)) {
                status = code
                assertEquals(OperationSubmission.BLOCKED, sync.submit(dao.find(id)!!))
                assertEquals("BLOCKED", dao.find(id)!!.state)
                assertEquals(1, dao.retryBlocked(id))
            }
            status = 200
            assertEquals(OperationSubmission.SENT, sync.submit(dao.find(id)!!))
            assertEquals("SENT", dao.find(id)!!.state)
            assertEquals(row.payload, dao.find(id)!!.payload)
            assertEquals(1, requests.distinct().size)
        } finally { db.close() }
    }
}
