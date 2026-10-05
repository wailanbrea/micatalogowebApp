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
                override suspend fun submit(shopId: String, payload: JsonObject): Response<JsonObject> = error("Wrong endpoint")
                override suspend fun submitFinancial(path: String, payload: JsonObject): Response<JsonObject> {
                    assertEquals("api/v1/shops/shop-1/expenses/expense-1/payments", path)
                    requests.add(payload.toString())
                    return if (status == 200) Response.success(buildJsonObject { put("client_operation_uuid", id) })
                    else Response.error(status, """{"message":"Retry or conflict"}""".toResponseBody())
                }
            }
            val sync = OperationSyncRepository(api, dao, Json)
            for (code in listOf(401, 426, 429, 500)) {
                status = code
                assertEquals(OperationSubmission.RETRY, sync.submit(dao.find(id)!!))
                assertEquals(row.payload, dao.find(id)!!.payload)
            }
            status = 409
            assertEquals(OperationSubmission.BLOCKED, sync.submit(dao.find(id)!!))
            assertEquals("BLOCKED", dao.find(id)!!.state)
            dao.retryBlocked(id)
            status = 200
            assertEquals(OperationSubmission.SENT, sync.submit(dao.find(id)!!))
            assertNotNull(dao.find(id)!!.serverResponse)
            assertEquals(1, requests.distinct().size)
        } finally { db.close() }
    }
}
