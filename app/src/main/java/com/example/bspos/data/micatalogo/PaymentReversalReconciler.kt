package com.example.bspos.data.micatalogo

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.data.local.dao.*
import com.example.bspos.data.micatalogo.api.RemotePaymentHistoryDto
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Applies a server-confirmed correction once, retaining the receipt and original allocations. */
class PaymentReversalReconciler @Inject constructor(
    private val transactions: AppDatabaseTransactor, private val queue: PaymentSyncDao,
    private val payments: PaymentDao, private val sales: SaleDao, private val cash: CashSessionDao,
    private val customers: CustomerRouteDao
) {
    suspend fun reconcile(shop: String, remote: RemotePaymentHistoryDto) {
        val reversal = remote.reversal ?: return
        val id = remote.clientTransactionUuid?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        transactions.runInTransaction {
            val queued = queue.find(id.toString()) ?: return@runInTransaction
            if (queued.shopId != shop || queued.state != "SENT") return@runInTransaction
            val ack = Json.decodeFromString<com.example.bspos.data.micatalogo.api.CustomerPaymentResponseDto>(queued.serverResponse ?: return@runInTransaction)
            require(ack.paymentId.toString() == remote.id && ack.customer.id == remote.customerId) { "La anulación no corresponde al recibo local confirmado" }
            if (queue.markReversed(id.toString(), shop, reversal.reason) != 1) return@runInTransaction
            queue.requestRefresh(com.example.bspos.data.local.entity.CatalogRefreshEntity(shop, UUID.randomUUID().toString()))
            val local = payments.findPayment(id, shop) ?: return@runInTransaction
            val at = Instant.parse(reversal.createdAt)
            payments.allocations(id, shop).forEach { allocation -> sales.reversePayment(allocation.saleId, allocation.allocatedAmount, at, shop) }
            cash.reversePaymentCollection(shop, local.receiptNumber, reversal.reason, at)
            customers.findCustomer(local.customerId, shop)?.let { customer ->
                check(customers.updateCustomer(customer.copy(balance = Math.addExact(customer.balance, local.amount), updatedAt = at)) == 1)
            }
        }
    }
}
