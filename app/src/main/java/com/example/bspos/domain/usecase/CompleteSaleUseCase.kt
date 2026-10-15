package com.example.bspos.domain.usecase

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.entity.TenantMetadata
import com.example.bspos.data.local.dao.CustomerRouteDao
import com.example.bspos.data.local.dao.InventoryDao
import com.example.bspos.data.local.dao.ProductDao
import com.example.bspos.data.local.dao.PosSaleOutboxDao
import com.example.bspos.data.local.dao.SaleDao
import com.example.bspos.data.local.entity.InventoryMovementEntity
import com.example.bspos.data.local.entity.SaleEntity
import com.example.bspos.data.local.entity.SaleItemEntity
import com.example.bspos.data.local.entity.PosSaleOutboxEntity
import com.example.bspos.data.micatalogo.MiCatalogoPosSaleOutboxMapper
import com.example.bspos.data.micatalogo.PosSaleOutboxLine
import com.example.bspos.data.micatalogo.PosSaleSyncScheduler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.bspos.domain.model.*
import com.example.bspos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class SaleLineInput(val productId:UUID,val quantity:Long,val unitPrice:Long,val discount:Long=0,val tax:Long=0)
data class PosPaymentSplitInput(
    val method: String,
    val amountCents: Long,
    val reference: String? = null,
    val notes: String? = null
)
data class CompleteSaleRequest(
    val invoiceNumber: String,
    val customerId: UUID? = null,
    val routeId: UUID? = null,
    val date: Instant,
    val lines: List<SaleLineInput>,
    val paymentType: SalePaymentType,
    val paidAmount: Long,
    val pendingAmount: Long,
    val discount: Long = 0,
    val tax: Long = 0,
    val notes: String? = null,
    val paymentReference: String? = null,
    val cashSessionId: UUID? = null,
    val splitPayments: List<PosPaymentSplitInput>? = null,
    val dueDate: String? = null,
    val saleMode: String = "retail"
)

class CompleteSaleUseCase @Inject constructor(private val transactor:AppDatabaseTransactor,private val sales:SaleDao,private val inventory:InventoryDao,private val products:ProductDao,private val customers:CustomerRouteDao,private val outbox:PosSaleOutboxDao,private val settings:SettingsRepository,private val json:Json,private val posSaleSyncScheduler:PosSaleSyncScheduler,private val shops:ShopScope,private val permissions:com.example.bspos.data.micatalogo.CachedShopMenuPolicy) {
 suspend operator fun invoke(request:CompleteSaleRequest):Sale {
  require(request.paymentType != SalePaymentType.MIXED || !request.splitPayments.isNullOrEmpty()) { "Mixed sales require explicit payment methods" }
  require(request.paymentType != SalePaymentType.CREDIT || request.paidAmount == 0L || !request.splitPayments.isNullOrEmpty()) { "Credit down payments require an explicit payment method" }
  var queuedForSync = false
    val sale = shops.mutate { shop -> transactor.runInTransaction {
   require(request.invoiceNumber.isNotBlank()&&request.lines.isNotEmpty()&&request.lines.map{it.productId}.distinct().size==request.lines.size)
   require(request.saleMode in setOf("retail", "wholesale"))
  val saleId=UUID.randomUUID();val location=if(request.routeId==null) InventoryLocation.MAIN else InventoryLocation.route(request.routeId)
   val resolvedLines=request.lines.map { line ->
   require(line.quantity>0&&line.unitPrice>=0&&line.discount>=0&&line.tax>=0)
    val product=checkNotNull(products.findById(line.productId,shop)){"Product not found in shop"};require(product.isActive&&product.deletedAt==null)
   val subtotal=Math.addExact(Math.subtractExact(Math.multiplyExact(line.quantity,line.unitPrice),line.discount),line.tax)
    Pair(SaleItemEntity(UUID.randomUUID(),saleId,line.productId,line.quantity,line.unitPrice,product.averageCost,line.discount,line.tax,subtotal,
         product.miCatalogoSaleUnit,product.miCatalogoVolumeMl,product.miCatalogoSourceProductId,tenant=TenantMetadata.assigned(shop)),PosSaleOutboxLine(product.miCatalogoShopId,product.miCatalogoProductId,line.quantity,line.unitPrice,line.discount,line.tax,
        product.miCatalogoSaleUnit ?: "unit",product.miCatalogoVolumeMl,product.miCatalogoSourceProductId))
   }
   val remoteLines = resolvedLines.map { it.second }
   val remoteShopIds = remoteLines.mapNotNull { it.remoteShopId?.trim()?.takeIf(String::isNotBlank) }.distinct()
   val hasRemoteReference = remoteLines.any { !it.remoteShopId.isNullOrBlank() || !it.remoteProductId.isNullOrBlank() }
    if (hasRemoteReference) require(remoteShopIds.size == 1 && remoteLines.all { !it.remoteShopId.isNullOrBlank() && !it.remoteProductId.isNullOrBlank() }) { "No puedes cobrar juntos productos de tiendas distintas o productos locales." }
    if (hasRemoteReference) {
        check(remoteShopIds.single() == shop && permissions.allows(shop, "sales")) { "No tienes permiso vigente para vender en esta tienda." }
        if (request.pendingAmount > 0) check(permissions.allows(shop, "credit")) { "No tienes permiso vigente para emitir crédito en esta tienda." }
    }
   val items=resolvedLines.map{it.first}
  val subtotal=items.fold(0L){sum,item->Math.addExact(sum,item.subtotal)};val total=Math.addExact(Math.subtractExact(subtotal,request.discount),request.tax)
   require(request.discount>=0&&request.tax>=0&&total>=0&&request.paidAmount>=0&&request.pendingAmount>=0&&Math.addExact(request.paidAmount,request.pendingAmount)==total)
  request.splitPayments?.let { payments ->
      require(payments.isNotEmpty() && payments.all { it.amountCents > 0L && it.method in setOf("cash", "card", "bank_transfer", "credit") })
      val splitPaid = payments.filter { it.method != "credit" }.fold(0L) { sum, payment -> Math.addExact(sum, payment.amountCents) }
      val splitCredit = payments.filter { it.method == "credit" }.fold(0L) { sum, payment -> Math.addExact(sum, payment.amountCents) }
      require(splitPaid == request.paidAmount && splitCredit == request.pendingAmount)
  }
   require(request.paymentType!=SalePaymentType.CREDIT||request.customerId!=null)
    request.customerId?.let { id -> val customer=checkNotNull(customers.findCustomer(id,shop)){"El cliente no pertenece a la tienda"}; require(customer.isActive&&customer.deletedAt==null){"El cliente está inactivo"} }
    if(request.pendingAmount>0){val customer=checkNotNull(request.customerId?.let{customers.findCustomer(it,shop)}){"Customer required for pending balance"};require(customer.deletedAt==null&&customer.isActive);val balance=Math.addExact(customer.balance,request.pendingAmount);require(balance<=customer.creditLimit){"Credit limit exceeded"};check(customers.updateCustomer(customer.copy(balance=balance,updatedAt=request.date))==1)}
   val sale=SaleEntity(saleId,request.invoiceNumber,request.customerId,request.routeId,request.date,subtotal,request.discount,request.tax,total,request.paymentType,request.paidAmount,request.pendingAmount,notes=request.notes,createdAt=request.date,updatedAt=request.date,saleMode=request.saleMode,tenant=TenantMetadata.assigned(shop))
  sales.insertWithItems(sale,items)
   LinkedInventorySaleRecorder.record(items,saleId,request.date,location,products,inventory,settings.observe().first().allowNegativeStock)
   val splitDtos = request.splitPayments?.map {
       com.example.bspos.data.micatalogo.dto.PosPaymentSplitDto(
           method = it.method,
           amount = MiCatalogoPosSaleOutboxMapper.decimalPrice(it.amountCents),
           reference = it.reference,
           notes = it.notes
       )
   } ?: when (request.paymentType) {
       SalePaymentType.CASH -> "cash"
       SalePaymentType.CARD -> "card"
       SalePaymentType.TRANSFER -> "bank_transfer"
       SalePaymentType.CREDIT, SalePaymentType.MIXED -> null
   }?.takeIf { request.paidAmount > 0L }?.let { method ->
       listOf(com.example.bspos.data.micatalogo.dto.PosPaymentSplitDto(
           method = method,
           amount = MiCatalogoPosSaleOutboxMapper.decimalPrice(request.paidAmount),
           reference = request.paymentReference,
           notes = request.notes
       ))
   }
    MiCatalogoPosSaleOutboxMapper.snapshot(saleId,sale.paidAmount,sale.pendingAmount,resolvedLines.map{it.second},splitDtos,request.dueDate,request.saleMode)?.let { snapshot ->
       check(permissions.allows(shop, "sales") && (request.pendingAmount == 0L || permissions.allows(shop, "credit"))) { "El permiso de venta o crédito cambió; conserva el borrador y revisa el acceso." }
      val remoteCustomerId = request.customerId?.let { customerId ->
         customers.findCustomer(customerId,shop)?.takeIf { it.miCatalogoCustomerShopId == snapshot.remoteShopId }?.miCatalogoCustomerId
      }
       val remoteRequest = snapshot.request.copy(customerId = remoteCustomerId, discount = MiCatalogoPosSaleOutboxMapper.decimalPrice(request.discount), tax = MiCatalogoPosSaleOutboxMapper.decimalPrice(request.tax), routeUuid = request.routeId?.toString())
      outbox.insert(PosSaleOutboxEntity(saleId,snapshot.remoteShopId,json.encodeToString(remoteRequest),nextAttemptAt=request.date,createdAt=request.date,updatedAt=request.date))
     queuedForSync = true
    }
    Sale(sale.id,sale.invoiceNumber,sale.customerId,sale.routeId,sale.date,sale.subtotal,sale.discount,sale.tax,sale.total,sale.paymentType,sale.paidAmount,sale.pendingAmount,sale.status,sale.notes,sale.createdAt,sale.updatedAt,sale.saleMode)
   } }
  if (queuedForSync) posSaleSyncScheduler.enqueue()
  return sale
 }
}
