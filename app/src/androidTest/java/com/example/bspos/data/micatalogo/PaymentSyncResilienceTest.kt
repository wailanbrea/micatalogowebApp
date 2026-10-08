package com.example.bspos.data.micatalogo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.data.local.AppDatabase
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.local.entity.PaymentSyncEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.PosSaleOutboxEntity
import com.example.bspos.data.local.entity.SaleEntity
import com.example.bspos.data.local.entity.SaleItemEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import com.example.bspos.data.micatalogo.api.CustomerListDto
import com.example.bspos.data.micatalogo.api.CustomerPaymentDto
import com.example.bspos.data.micatalogo.api.CustomerPaymentResponseDto
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import com.example.bspos.domain.model.SalePaymentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PaymentSyncResilienceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PaymentSyncRepository
    private lateinit var api: FakeCustomerApi
    private val now = Instant.parse("2026-10-08T04:00:00Z")
    private val shopId = "qa-payment-shop"
    private val customerId = UUID.randomUUID()
    private val saleId = UUID.randomUUID()
    private val paymentId = UUID.randomUUID().toString()

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        api = FakeCustomerApi()
        repository = PaymentSyncRepository(db.paymentSyncDao(), db.posSaleOutboxDao(), db.customerRouteDao(), api, Json)

        db.customerRouteDao().insertCustomer(
            CustomerEntity(
                id = customerId,
                businessName = "Cliente QA",
                creditLimit = 100000,
                balance = 50000,
                createdAt = now,
                updatedAt = now,
                miCatalogoCustomerId = "remote-customer",
                miCatalogoCustomerShopId = shopId,
            )
        )
        val categoryId = UUID.randomUUID()
        val unitId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        db.categoryDao().insert(
            CategoryEntity(
                id = categoryId,
                name = "QA",
                createdAt = now,
                updatedAt = now,
            )
        )
        db.unitOfMeasureDao().insert(
            UnitOfMeasureEntity(
                id = unitId,
                name = "Unidad",
                abbreviation = "ud",
                createdAt = now,
                updatedAt = now,
            )
        )
        db.productDao().insert(
            ProductEntity(
                id = productId,
                name = "Producto QA",
                internalCode = "QA-PAYMENT-001",
                categoryId = categoryId,
                unitId = unitId,
                salePrice = 50000,
                averageCost = 25000,
                lastPurchaseCost = 25000,
                createdAt = now,
                updatedAt = now,
            )
        )
        val sale = SaleEntity(
            id = saleId,
            invoiceNumber = "QA-PAYMENT-SALE",
            customerId = customerId,
            date = now,
            subtotal = 50000,
            total = 50000,
            paymentType = SalePaymentType.CREDIT,
            pendingAmount = 50000,
            createdAt = now,
            updatedAt = now,
        )
        db.saleDao().insertWithItems(
            sale,
            listOf(
                SaleItemEntity(
                    id = UUID.randomUUID(),
                    saleId = saleId,
                    productId = productId,
                    quantity = 1,
                    unitPrice = 50000,
                    unitCostSnapshot = 25000,
                    subtotal = 50000,
                )
            )
        )
        db.posSaleOutboxDao().insert(
            PosSaleOutboxEntity(
                saleId = saleId,
                remoteShopId = shopId,
                payloadJson = "{}",
                state = com.example.bspos.domain.model.PosSaleOutboxState.SENT,
                nextAttemptAt = now,
                createdAt = now,
                updatedAt = now,
            )
        )
        db.paymentSyncDao().insert(
            PaymentSyncEntity(
                id = paymentId,
                shopId = shopId,
                customerId = customerId.toString(),
                payload = Json.encodeToString(CustomerPaymentDto(paymentId, "100.00", "cash")),
                dependencies = saleId.toString(),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun transientAndDefinitiveResponsesKeepReplaySafeState() = runTest {
        for (code in listOf(401, 426, 429, 500)) {
            api.status = code
            assertTrue("HTTP $code must request retry", repository.sync())
            assertEquals("PENDING", db.paymentSyncDao().pending().single().state)
        }

        for (code in listOf(403, 409, 422)) {
            api.status = code
            assertFalse("HTTP $code must stop retry loop", repository.sync())
            assertEquals("BLOCKED", db.paymentSyncDao().observeOutstanding().first().single().state)
            assertEquals(1, db.paymentSyncDao().retryBlocked(paymentId))
        }

        api.status = 200
        assertFalse(repository.sync())
        assertTrue(db.paymentSyncDao().pending().isEmpty())
        assertTrue(db.paymentSyncDao().observeOutstanding().first().isEmpty())
        assertEquals(paymentId, api.lastRequest?.uuid)
    }

    private class FakeCustomerApi : MiCatalogoCustomerApi {
        var status = 500
        var lastRequest: CustomerPaymentDto? = null

        override suspend fun payment(shopId: String, customerId: String, body: CustomerPaymentDto): Response<CustomerPaymentResponseDto> {
            lastRequest = body
            return if (status == 200) {
                Response.success(
                    CustomerPaymentResponseDto(
                        customer = RemoteCustomerDto(id = customerId),
                        paymentId = 1,
                        clientTransactionUuid = body.uuid,
                        amount = body.amount,
                    )
                )
            } else {
                Response.error(status, "{\"message\":\"QA HTTP $status\"}".toResponseBody())
            }
        }

        override suspend fun customers(shopId: String): Response<CustomerListDto> = error("Unused")

        override suspend fun createCustomer(
            shopId: String,
            request: com.example.bspos.data.micatalogo.dto.CustomerUploadRequestDto,
        ): Response<RemoteCustomerDto> = error("Unused")
    }
}
