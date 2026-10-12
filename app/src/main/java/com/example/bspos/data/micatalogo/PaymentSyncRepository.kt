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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PaymentSyncRepository @Inject constructor(private val queue: PaymentSyncDao,
    private val sales: PosSaleOutboxDao, private val customers: CustomerRouteDao,
    private val api: MiCatalogoCustomerApi, private val json: Json) {
    private val mutex = Mutex()
    suspend fun blockedCount(): Int = queue.blockedCount()
    suspend fun sync(): Boolean = mutex.withLock {
        var retry = false
        queue.pending().forEach { row ->
            val dependencies = row.dependencies.split(',').filter { it.isNotBlank() }
            if (dependencies.any { sales.stateForSale(UUID.fromString(it)) != "SENT" }) {
                retry = true
                return@forEach
            }
            try {
                val customer = checkNotNull(customers.findCustomer(UUID.fromString(row.customerId),row.shopId))
                require(dependencies.all { sales.shopForSale(UUID.fromString(it))==row.shopId }) { "Los abonos pertenecen a otra tienda" }
                val remoteId = checkNotNull(customer.miCatalogoCustomerId)
                val body = json.decodeFromString<CustomerPaymentDto>(row.payload)
                require(body.uuid == row.id) { "La identidad del abono no corresponde a su instantánea." }
                val response = api.payment(row.shopId, remoteId, body)
                when {
                    response.isSuccessful -> {
                        val ack = response.body()
                        if (ack == null || ack.clientTransactionUuid != row.id || ack.customer.id != remoteId ||
                            ack.paymentId <= 0 || MiCatalogoImportMapper.cents(ack.amount) != MiCatalogoImportMapper.cents(body.amount)) {
                            queue.mark(row.id, "PENDING", "La respuesta no confirma este abono; se conserva el UUID para reintentar.")
                            retry = true
                        } else queue.confirmSent(row, json.encodeToString(ack))
                    }
                    response.code() == 401 || response.code() == 426 || response.code() == 429 || response.code() >= 500 -> {
                        queue.mark(row.id, "PENDING", response.apiErrorMessage("Reintentando el abono pendiente de confirmación.")); retry = true
                    }
                    else -> queue.mark(row.id, "BLOCKED", response.apiErrorMessage("No se pudo sincronizar el abono."))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is IllegalArgumentException || error is IllegalStateException || error is kotlinx.serialization.SerializationException) {
                    queue.mark(row.id, "BLOCKED", "La instantánea del abono requiere revisión antes de enviarlo.")
                } else {
                    queue.mark(row.id, "PENDING", "No se pudo confirmar el abono. Se conserva para reintentar sin duplicarlo.")
                    retry = true
                }
            }
        }
        retry || queue.pending().isNotEmpty()
    }
}
