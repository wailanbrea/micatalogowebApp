package com.example.bspos.domain.usecase

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.dao.ReturnDao
import com.example.bspos.data.local.dao.SaleDao
import com.example.bspos.data.mapper.toDomain
import com.example.bspos.data.micatalogo.RemoteMutationRecorder
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SaleStatus
import com.example.bspos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class CancelSaleUseCase @Inject constructor(
    private val transactor: AppDatabaseTransactor,
    private val sales: SaleDao,
    private val inventory: InventoryDao,
    private val products: ProductDao,
    private val customers: CustomerRouteDao,
    private val returns: ReturnDao,
    private val saleQueue: PosSaleOutboxDao,
    private val remote: RemoteMutationRecorder,
    private val settings: SettingsRepository,
    private val shops: ShopScope
) {
    suspend operator fun invoke(saleId: UUID, reason: String? = null, at: Instant = Instant.now()): Sale {
        var queued = false
        val cancelledSale = transactor.runInTransaction {
            val activeShop = shops.current()
            val sale = checkNotNull(sales.findById(saleId, activeShop)) { "Venta no encontrada en la tienda." }
            require(sale.status == SaleStatus.COMPLETED) { "Solo se pueden anular ventas activas." }

            val sold = sales.itemsInInvoiceOrder(sale.id, activeShop)
            val restocked = sold.map { item ->
                val alreadyReturned = returns.returnedQuantity(item.id, activeShop)
                val remaining = maxOf(0L, item.quantity - alreadyReturned)
                item.copy(quantity = remaining)
            }.filter { it.quantity > 0 }

            val targetLocation = sale.routeId?.let(InventoryLocation::route) ?: InventoryLocation.MAIN
            if (restocked.isNotEmpty()) {
                LinkedInventorySaleRecorder.record(
                    restocked,
                    sale.id,
                    at,
                    targetLocation,
                    products,
                    inventory,
                    settings.observe().first().allowNegativeStock,
                    restoring = true
                )
            }

            // Wipe remaining customer pending debt on this sale
            if (sale.customerId != null && sale.pendingAmount > 0L) {
                val customer = customers.findCustomer(sale.customerId, activeShop)
                if (customer != null) {
                    val newBalance = maxOf(0L, customer.balance - sale.pendingAmount)
                    customers.updateCustomer(customer.copy(balance = newBalance, updatedAt = at))
                }
            }

            sales.updateBalancesAndStatus(sale.id, sale.paidAmount, 0L, SaleStatus.CANCELLED, at, activeShop)

            val shop = saleQueue.shopForSale(sale.id)
            if (shop != null) {
                val operationUuid = UUID.randomUUID()
                remote.enqueue(shop, restocked.mapNotNull { it.productId.toString() }, buildJsonObject {
                    put("type", "sale_cancel")
                    put("client_sale_uuid", sale.id.toString())
                    reason?.takeIf { it.isNotBlank() }?.let { put("notes", it) }
                }, at, operationUuid.toString())
                queued = true
            }

            sale.copy(status = SaleStatus.CANCELLED, pendingAmount = 0L, updatedAt = at).toDomain()
        }

        if (queued) remote.wake()
        return cancelledSale
    }
}
