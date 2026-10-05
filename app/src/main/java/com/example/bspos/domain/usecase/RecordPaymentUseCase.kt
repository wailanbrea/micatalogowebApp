package com.example.bspos.domain.usecase

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.data.local.dao.CashSessionDao
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.PaymentDao
import com.example.bspos.data.local.dao.SaleDao
import com.example.bspos.data.local.entity.CashMovementEntity
import com.example.bspos.data.local.entity.PaymentAllocationEntity
import com.example.bspos.data.local.entity.PaymentEntity
import com.example.bspos.domain.model.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.example.bspos.data.local.dao.PaymentSyncDao
import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.local.entity.PaymentSyncEntity
import com.example.bspos.data.micatalogo.PosSaleSyncScheduler
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import com.example.bspos.data.micatalogo.api.CustomerPaymentDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

data class PaymentAllocationInput(val saleId:UUID,val amount:Long)
data class RecordPaymentRequest(val receiptNumber:String,val customerId:UUID,val date:Instant,val method:PaymentMethod,val allocations:List<PaymentAllocationInput>,val routeId:UUID?=null,val reference:String?=null,val notes:String?=null,val cashSessionId:UUID?=null)
class RecordPaymentUseCase @Inject constructor(
    private val transactor: AppDatabaseTransactor, private val payments: PaymentDao,
    private val sales: SaleDao, private val customers: CustomerRouteDao, private val cash: CashSessionDao,
    private val paymentSync: PaymentSyncDao, private val saleOutbox: PosSaleOutboxDao,
    private val scheduler: PosSaleSyncScheduler, private val json: Json
) {
    suspend operator fun invoke(request: RecordPaymentRequest): Payment {
        var queued = false
        val result = transactor.runInTransaction {
            require(request.receiptNumber.isNotBlank() && request.allocations.isNotEmpty()
                && (request.cashSessionId == null || request.method == PaymentMethod.CASH))
            val session = if (request.method == PaymentMethod.CASH)
                checkNotNull(request.cashSessionId ?: cash.findOpenId()) { "Open cash session required" } else null
            val id = UUID.randomUUID()
            val amount = request.allocations.sumOf { it.amount }
            val customer = checkNotNull(customers.findCustomer(request.customerId)) { "Customer not found" }
            require(customer.deletedAt == null && customer.isActive && amount > 0 && amount <= customer.balance)
            val remoteShops = request.allocations.map { saleOutbox.shopForSale(it.saleId) }
            val remoteCount = remoteShops.count { it != null }
            require(remoteCount == 0 || (remoteCount == request.allocations.size && remoteShops.distinct().size == 1)) {
                "Registra por separado abonos de ventas locales y de distintas tiendas."
            }
            val payment = PaymentEntity(id, request.receiptNumber, request.customerId, request.routeId,
                request.date, amount, request.method, request.reference, request.notes, request.date)
            payments.insertWithAllocations(payment, request.allocations.map {
                PaymentAllocationEntity(UUID.randomUUID(), id, it.saleId, it.amount)
            })
            request.allocations.forEach { sales.applyPayment(it.saleId, it.amount, request.date) }
            check(customers.updateCustomer(customer.copy(balance = Math.subtractExact(customer.balance, amount), updatedAt = request.date)) == 1)
            session?.let { cash.recordMovement(CashMovementEntity(UUID.randomUUID(), it,
                CashMovementType.PAYMENT_COLLECTION, amount, "Payment ${request.receiptNumber}", request.date)) }
            if (remoteCount > 0) {
                paymentSync.insert(PaymentSyncEntity(id.toString(), remoteShops.first()!!, customer.id.toString(),
                    json.encodeToString(CustomerPaymentDto(
                        uuid = id.toString(),
                        amount = MiCatalogoPosSaleOutboxMapper.decimalPrice(amount),
                        paymentMethod = request.method.toMiCatalogoPaymentMethod(),
                        reference = request.reference,
                        notes = request.notes
                    )),
                    request.allocations.joinToString(",") { it.saleId.toString() }))
                queued = true
            }
            Payment(payment.id, payment.receiptNumber, payment.customerId, payment.routeId, payment.date,
                payment.amount, payment.method, payment.reference, payment.notes, payment.createdAt)
        }
        if (queued) scheduler.enqueue()
        return result
    }
}

internal fun PaymentMethod.toMiCatalogoPaymentMethod(): String = when (this) {
    PaymentMethod.CASH -> "cash"
    PaymentMethod.CARD -> "card"
    PaymentMethod.TRANSFER -> "bank_transfer"
    PaymentMethod.CHECK -> "other"
}
