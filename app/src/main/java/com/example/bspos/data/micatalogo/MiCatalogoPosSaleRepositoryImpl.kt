package com.example.bspos.data.micatalogo

import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.SaleDao
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.api.MiCatalogoCustomerApi
import com.example.bspos.data.micatalogo.dto.CustomerUploadRequestDto
import com.example.bspos.data.micatalogo.dto.PosSaleUploadRequestDto
import com.example.bspos.domain.model.MiCatalogoPosSaleSyncResult
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.repository.MiCatalogoPosSaleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiCatalogoPosSaleRepositoryImpl @Inject constructor(
    private val api: MiCatalogoApi,
    private val customerApi: MiCatalogoCustomerApi,
    private val outbox: PosSaleOutboxDao,
    private val customers: CustomerRouteDao,
    private val sales: SaleDao,
    private val json: Json,
    private val operations: OperationOutboxDao,
    private val operationSync: OperationSyncRepository
) : MiCatalogoPosSaleRepository {
    private val syncMutex = Mutex()

    override suspend fun syncDueSales(): MiCatalogoResult<MiCatalogoPosSaleSyncResult> = syncMutex.withLock { runCatching {
        var sent = 0
        var retried = 0
        var blocked = 0
        val recoveredBottles = linkedSetOf<String>()
        val haltedShops = mutableSetOf<String>()
        val blockedShops = mutableSetOf<String>()
        suspend fun drainOperations(shopId: String, before: Long?): Boolean {
            repeat(MAX_BATCH_SIZE) {
                val row = operations.oldestActive(shopId) ?: return true
                if (before != null && row.queueSequence > before) return true
                when (operationSync.submit(row)) {
                    OperationSubmission.SENT -> sent++
                    OperationSubmission.RETRY -> { retried++; haltedShops.add(shopId); return false }
                    OperationSubmission.BLOCKED -> { blocked++; haltedShops.add(shopId); blockedShops.add(shopId); return false }
                }
            }
            retried++
            haltedShops.add(shopId)
            return false
        }
        outbox.findDue(Instant.now(), MAX_BATCH_SIZE).sortedBy { it.queueSequence }.forEach { record ->
            if (record.remoteShopId in haltedShops) return@forEach
            val firstSale = outbox.findActiveForShop(record.remoteShopId).minByOrNull { it.queueSequence }
            // Never leapfrog a blocked sale or an older sale whose backoff has not elapsed.
            if (firstSale?.saleId != record.saleId) {
                haltedShops.add(record.remoteShopId)
                if (firstSale?.state == com.example.bspos.domain.model.PosSaleOutboxState.BLOCKED) blockedShops.add(record.remoteShopId) else retried++
                return@forEach
            }
            if (!drainOperations(record.remoteShopId, record.queueSequence)) return@forEach
            val now = Instant.now()
            val request = runCatching { json.decodeFromString<PosSaleUploadRequestDto>(record.payloadJson) }.getOrNull()
            if (request == null || request.clientSaleUuid != record.saleId.toString() || request.items.isEmpty()) {
                outbox.markBlocked(record.saleId, "Invalid local POS sale snapshot", now)
                blocked++
                haltedShops.add(record.remoteShopId)
                blockedShops.add(record.remoteShopId)
                return@forEach
            }

            try {
                val preparedRequest = prepareRequest(record.remoteShopId, record.saleId, request)
                outbox.freezePayload(record.saleId, json.encodeToString(preparedRequest))
                val response = api.uploadPosSale(record.remoteShopId, preparedRequest)
                when {
                    response.isSuccessful -> {
                        val acknowledgement = response.body()
                        if (acknowledgement?.clientSaleUuid != record.saleId.toString()) {
                            outbox.markRetry(record.saleId, "La respuesta no confirma esta venta; se reintentará sin duplicarla.", now, now)
                            retried++
                            haltedShops.add(record.remoteShopId)
                        } else {
                            outbox.markSent(record.saleId, acknowledgement.invoiceNumber, now)
                            acknowledgement.bottleRecovery
                                .filter { it.justCovered }
                                .mapNotNull { it.sourceProductName ?: it.sourceProductId }
                                .forEach(recoveredBottles::add)
                            sent++
                        }
                    }
                    response.code() in 400..499 && response.code() !in listOf(401, 426, 429) -> {
                        outbox.markBlocked(record.saleId, errorMessage(response), now)
                        blocked++
                        haltedShops.add(record.remoteShopId)
                        blockedShops.add(record.remoteShopId)
                    }
                    else -> {
                        outbox.markRetry(record.saleId, errorMessage(response), now, now)
                        retried++
                        haltedShops.add(record.remoteShopId)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (error is RemoteSyncException && error.code in 400..499 && error.code !in listOf(401, 426, 429)) {
                    outbox.markBlocked(record.saleId, error.message ?: "Customer data is invalid", now)
                    blocked++
                    blockedShops.add(record.remoteShopId)
                } else {
                    outbox.markRetry(record.saleId, error.message ?: "Network error uploading POS sale", now, now)
                    retried++
                }
                haltedShops.add(record.remoteShopId)
            }
        }
        // Operation-only queues also progress, but cannot cross an outstanding sale.
        operations.activeShopIds().filter { it !in haltedShops }.forEach { shopId ->
            val nextSale = outbox.findActiveForShop(shopId).minByOrNull { it.queueSequence }
            drainOperations(shopId, nextSale?.queueSequence)
        }
        // A future next_attempt_at still needs WorkManager to wake again. Looking
        // only at findDue() incorrectly reported success and abandoned that sale.
        for (shopId in outbox.activeShopIds().filter { it !in blockedShops }) {
            val first = outbox.findActiveForShop(shopId).minByOrNull { it.queueSequence } ?: continue
            if (first.state != com.example.bspos.domain.model.PosSaleOutboxState.BLOCKED) {
                val operation = operations.oldestActive(shopId)
                if (operation == null || operation.queueSequence > first.queueSequence || operation.state != "BLOCKED") retried++
            }
        }
        MiCatalogoPosSaleSyncResult(sent, retried, blocked, recoveredBottles.toList())
    }.fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = {
            if (it is CancellationException) throw it
            MiCatalogoResult.Failure(it.message ?: "No se pudieron sincronizar las ventas POS.")
        }
    ) }

    private fun errorMessage(response: Response<*>): String {
        val fallback = "HTTP ${response.code()} ${response.message()}".trim()
        val body = response.errorBody()?.string().orEmpty()
        if (body.isBlank()) return fallback
        return runCatching {
            val objectBody = json.parseToJsonElement(body).jsonObject
            objectBody["reason"]?.jsonPrimitive?.contentOrNull
                ?: objectBody["message"]?.jsonPrimitive?.contentOrNull
                ?: fallback
        }.getOrDefault(fallback)
    }

    private suspend fun prepareRequest(
        remoteShopId: String,
        saleId: java.util.UUID,
        request: PosSaleUploadRequestDto
    ): PosSaleUploadRequestDto {
        val sale = checkNotNull(sales.findById(saleId)) { "La venta local no existe." }
        if (request.creditAmount == null || request.creditAmount.toBigDecimal().signum() <= 0) return request
        if (!request.customerId.isNullOrBlank()) return request

        val customerId = checkNotNull(sale.customerId) { "La venta a crédito no tiene cliente." }
        val customer = checkNotNull(customers.findCustomer(customerId)) { "El cliente local no existe." }
        customer.miCatalogoCustomerId?.takeIf { it.isNotBlank() && customer.miCatalogoCustomerShopId == remoteShopId }?.let {
            return request.copy(customerId = it)
        }
        val firstName = customer.firstName?.trim().orEmpty()
        val lastName = customer.lastName?.trim().orEmpty()
        val documentType = customer.documentType?.trim()?.lowercase().orEmpty()
        val documentNumber = customer.documentNumber?.trim().orEmpty()
        val phone = customer.phone?.trim().orEmpty()
        val address = customer.address?.trim().orEmpty()
        if (firstName.isBlank() || lastName.isBlank() || documentNumber.isBlank() || phone.isBlank() || address.isBlank() || documentType !in setOf("cedula", "pasaporte")) {
            throw RemoteSyncException(422, "Actualiza los datos obligatorios del cliente antes de sincronizar la venta.")
        }

        val remoteCustomerId = customer.miCatalogoCustomerId
            ?.takeIf { it.isNotBlank() && customer.miCatalogoCustomerShopId == remoteShopId }
            ?: createRemoteCustomer(remoteShopId, customer, firstName, lastName, documentType, documentNumber, phone, address)

        return request.copy(
            customerId = remoteCustomerId,
            creditAmount = request.creditAmount
        )
    }

    private suspend fun createRemoteCustomer(
        remoteShopId: String,
        customer: com.example.bspos.data.local.entity.CustomerEntity,
        firstName: String,
        lastName: String,
        documentType: String,
        documentNumber: String,
        phone: String,
        address: String
    ): String {
        val response = customerApi.createCustomer(
            remoteShopId,
            CustomerUploadRequestDto(
                clientCustomerUuid = customer.id.toString(),
                firstName = firstName,
                lastName = lastName,
                documentType = documentType,
                documentNumber = documentNumber,
                name = null,
                phone = phone,
                email = customer.email,
                address = address,
                whatsapp = customer.whatsapp,
                reference = customer.reference,
                creditLimit = MiCatalogoPosSaleOutboxMapper.decimalPrice(customer.creditLimit),
                notes = customer.notes
            )
        )
        if (!response.isSuccessful) throw RemoteSyncException(response.code(), errorMessage(response))
        val remoteId = response.body()?.id?.takeIf { it.isNotBlank() }
            ?: throw RemoteSyncException(502, "MiCatalogo no devolvió el identificador del cliente.")
        check(customers.updateCustomer(customer.copy(miCatalogoCustomerId = remoteId, miCatalogoCustomerShopId = remoteShopId)) == 1) {
            "No se pudo guardar el identificador remoto del cliente."
        }
        return remoteId
    }

    private class RemoteSyncException(val code: Int, override val message: String) : Exception(message)

    private companion object {
        const val MAX_BATCH_SIZE = 100
    }
}
