package com.example.bspos.data.micatalogo
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.PaymentSyncDao
import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.micatalogo.api.CustomerPaymentDto
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject

class PaymentSyncRepository @Inject constructor(private val queue: PaymentSyncDao,
    private val sales: PosSaleOutboxDao, private val customers: CustomerRouteDao,
    private val api: MiCatalogoCustomerApi, private val json: Json) {
    suspend fun blockedCount(): Int = queue.blockedCount()
    suspend fun sync(): Boolean {
        var retry = false
        queue.pending().forEach { row ->
            if (row.dependencies.split(',').any { sales.stateForSale(UUID.fromString(it)) != "SENT" }) {
                retry = true
                return@forEach
            }
            try {
                val customer = checkNotNull(customers.findCustomer(UUID.fromString(row.customerId)))
                val remoteId = checkNotNull(customer.miCatalogoCustomerId)
                val response = api.payment(row.shopId, remoteId, json.decodeFromString<CustomerPaymentDto>(row.payload))
                when {
                    response.isSuccessful -> {
                        val receipt = response.body()
                        if (receipt == null || receipt.clientTransactionUuid != row.id) {
                            retry = true
                        } else {
                            queue.confirm(row.id, json.encodeToString(receipt))
                        }
                    }
                    response.code() == 401 || response.code() == 426 || response.code() == 429 || response.code() >= 500 -> retry = true
                    else -> queue.mark(row.id, "BLOCKED", response.apiErrorMessage("No se pudo sincronizar el abono."))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                retry = true
            }
        }
        return retry || queue.pending().isNotEmpty()
    }
}
