package com.example.bspos.domain.usecase

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.data.local.dao.CashSessionDao
import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.dao.ReturnDao
import com.example.bspos.data.local.dao.SaleDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.local.entity.CashMovementEntity
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.ReturnEntity
import com.example.bspos.data.local.entity.ReturnItemEntity
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import com.example.bspos.data.micatalogo.RemoteMutationRecorder
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import kotlinx.serialization.json.*

data class CashReturnLineInput(val saleItemId:UUID,val productId:UUID,val quantity:Long,val refundPrice:Long,val unitCostSnapshot:Long,val restockInventory:Boolean)
data class CompleteCashReturnRequest(val returnNumber:String,val saleId:UUID,val date:Instant,val lines:List<CashReturnLineInput>,val reason:String?=null,
    val refundChargedAmount:Boolean=false)

class CompleteCashReturnUseCase @Inject constructor(private val transactor:AppDatabaseTransactor,
    private val returns:ReturnDao, private val sales:SaleDao, private val inventory:InventoryDao,
    private val cash:CashSessionDao, private val settings:SettingsRepository, private val products:ProductDao,
    private val saleQueue:PosSaleOutboxDao, private val remote:RemoteMutationRecorder) {
    suspend operator fun invoke(request:CompleteCashReturnRequest):Return {
        var queued = false
        val result = transactor.runInTransaction {
            require(request.returnNumber.isNotBlank() && request.lines.isNotEmpty())
            require(request.lines.map { it.saleItemId }.distinct().size == request.lines.size)
            val sale = checkNotNull(sales.findById(request.saleId)) { "Venta no encontrada." }
            require(sale.status == SaleStatus.COMPLETED && sale.paymentType == SalePaymentType.CASH &&
                sale.pendingAmount == 0L && sale.paidAmount == sale.total) { "Solo se devuelven ventas de contado totalmente pagadas." }
            val sold = sales.itemsInInvoiceOrder(sale.id)
            val charged = CashRefundAmounts.allocate(sold.map { RefundableLine(it.id.toString(),it.quantity,it.subtotal) },sale.discount,sale.tax)
            val id = UUID.randomUUID()
            val items = request.lines.map { line ->
                val original = checkNotNull(sold.find { it.id == line.saleItemId }) { "La línea no pertenece a la venta." }
                require(line.productId == original.productId && line.unitCostSnapshot == original.unitCostSnapshot)
                val limit = CashRefundAmounts.portion(charged.getValue(original.id.toString()), original.quantity,
                    returns.returnedQuantity(original.id), line.quantity)
                val amount = if (request.refundChargedAmount) limit else Math.multiplyExact(line.quantity,line.refundPrice)
                require(amount in 0..limit) { "El reembolso supera lo cobrado por esas unidades." }
                ReturnItemEntity(UUID.randomUUID(),id,line.saleItemId,line.productId,line.quantity,
                    amount / line.quantity,original.unitCostSnapshot,line.restockInventory,amount)
            }
            val total = items.fold(0L) { sum,item -> Math.addExact(sum,item.refundedCents) }
            val resolved = items.associateWith { checkNotNull(products.findById(it.productId)) }
            for ((item,product) in resolved.filterKeys { it.restockInventory }) {
                val original = checkNotNull(sold.find { it.id == item.saleItemId })
                if (product.miCatalogoSaleUnit in setOf("bottle","ml","decant") || original.saleUnitSnapshot in setOf("bottle","ml","decant")) {
                    require(original.saleUnitSnapshot == product.miCatalogoSaleUnit &&
                        original.volumeMlSnapshot == product.miCatalogoVolumeMl && original.remoteSourceSnapshot == product.miCatalogoSourceProductId) {
                        "La presentación cambió o la venta antigua no conserva su fuente: concilia antes de reintegrar."
                    }
                }
            }
            val remoteProducts = resolved.values.filter { it.miCatalogoShopId != null }
            val shop = remoteProducts.firstOrNull()?.miCatalogoShopId
            if (shop != null) {
                require(items.size <= 100 && items.all { it.quantity <= 10000 }) { "La devolución supera los límites de sincronización." }
                require((request.reason?.length ?: 0) <= 1000) { "El motivo admite hasta 1,000 caracteres." }
                require(remoteProducts.size == items.size && remoteProducts.all { it.miCatalogoShopId == shop })
                require(saleQueue.shopForSale(sale.id) == shop) { "La venta original requiere conciliación antes de devolverla a la tienda." }
                remote.enqueue(shop,remoteProducts.map { checkNotNull(it.miCatalogoProductId) }, buildJsonObject {
                    put("type","return"); put("client_sale_uuid",sale.id.toString())
                    request.reason?.let { put("notes",it) }
                    put("items",buildJsonArray { resolved.forEach { (item,product) -> add(buildJsonObject {
                        put("product_id",checkNotNull(product.miCatalogoProductId)); put("quantity",item.quantity)
                        put("refund_price",MiCatalogoPosSaleOutboxMapper.decimalPrice(item.refundPrice))
                        put("refund_total",MiCatalogoPosSaleOutboxMapper.decimalPrice(item.refundedCents))
                        put("restock",item.restockInventory)
                    }) } })
                },request.date,id.toString())
                queued = true
            }
            returns.insertWithItems(ReturnEntity(id,request.returnNumber,sale.id,sale.customerId,request.date,total,request.reason,request.date),items)
            val restocked = items.filter { it.restockInventory }.map { item ->
                checkNotNull(sold.find { it.id == item.saleItemId }).copy(quantity=item.quantity)
            }
            if (restocked.isNotEmpty()) LinkedInventorySaleRecorder.record(restocked,id,request.date,InventoryLocation.MAIN,
                products,inventory,settings.observe().first().allowNegativeStock,restoring=true)
            if (total > 0) {
                val sessionId = checkNotNull(cash.findOpenId()) { "Abre una caja para registrar el reembolso." }
                cash.recordMovement(CashMovementEntity(UUID.randomUUID(),sessionId,CashMovementType.REFUND,total,
                    "Devolución ${request.returnNumber}",request.date))
            }
            Return(id,request.returnNumber,sale.id,sale.customerId,request.date,total,request.reason,request.date)
        }
        if (queued) remote.wake()
        return result
    }
}
