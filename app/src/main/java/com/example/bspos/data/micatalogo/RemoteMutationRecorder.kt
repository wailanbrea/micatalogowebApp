package com.example.bspos.data.micatalogo

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.data.local.dao.CategoryDao
import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.data.local.entity.OperationOutboxEntity
import com.example.bspos.data.local.entity.CustomerEntity
import com.example.bspos.data.local.entity.ProductEntity
import com.example.bspos.data.local.entity.CategoryEntity
import com.example.bspos.data.local.entity.UnitOfMeasureEntity
import com.example.bspos.data.local.entity.InventoryAdjustmentReasonEntity
import kotlinx.serialization.json.*
import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Mutation and outbox insert must share the transaction; never perform HTTP here. */
class RemoteMutationRecorder @Inject constructor(private val transactor: AppDatabaseTransactor,
    private val categories: CategoryDao, private val queue: OperationOutboxDao,
    private val scheduler: PosSaleSyncScheduler,
    private val images: ProductImageSnapshot? = null) {
    suspend fun <T> transaction(block: suspend () -> T): T = transactor.runInTransaction { block() }
    fun wake() = scheduler.enqueue()

    suspend fun category(row: CategoryEntity) = record("category_upsert", row.id, row.tenant, row.name,
        row.isActive, row.deletedAt, row.createdAt, row.updatedAt, buildJsonObject {
            put("description", row.description?.let(::JsonPrimitive) ?: JsonNull)
            put("icon", row.icon); put("sort_order", row.sortOrder)
        })

    suspend fun unit(row: UnitOfMeasureEntity) = record("unit_upsert", row.id, row.tenant, row.name,
        row.isActive, row.deletedAt, row.createdAt, row.updatedAt, buildJsonObject { put("abbreviation", row.abbreviation) })

    suspend fun reason(row: InventoryAdjustmentReasonEntity) = record("adjustment_reason_upsert", row.id, row.tenant, row.name,
        row.isActive, row.deletedAt, row.createdAt, row.updatedAt, buildJsonObject { put("direction", row.direction.name) })

    suspend fun route(row: com.example.bspos.data.local.entity.RouteEntity) {
        val shop = checkNotNull(row.tenant.shopId)
        enqueue(shop, emptyList(), buildJsonObject {
            put("type", "route_upsert"); put("record_id", row.id.toString()); put("name", row.name); put("code", row.code)
            put("description", row.description?.let(::JsonPrimitive) ?: JsonNull)
            put("is_active", row.isActive); put("created_at", row.createdAt.toString())
        }, row.updatedAt)
    }

    private suspend fun record(type: String, id: UUID, tenant: com.example.bspos.data.local.entity.TenantMetadata,
        name: String, active: Boolean, deleted: Instant?, created: Instant, updated: Instant, fields: JsonObject) {
        val shop = checkNotNull(tenant.shopId)
        require(tenant.status == com.example.bspos.data.local.entity.TenantMetadata.ASSIGNED)
        enqueue(shop, emptyList(), JsonObject(fields + buildJsonObject {
            put("type", type); put("record_id", id.toString()); put("name", name)
            put("is_active", active); put("is_deleted", deleted != null); put("created_at", created.toString())
        }), updated)
    }

    suspend fun product(product: ProductEntity, previous: ProductEntity? = null) {
        val shop = product.miCatalogoShopId ?: return
        val remote = checkNotNull(product.miCatalogoProductId)
        require(product.tenant.shopId==shop && product.tenant.status==com.example.bspos.data.local.entity.TenantMetadata.ASSIGNED)
        val category = checkNotNull(categories.findById(product.categoryId, shop)) { "La categoría no pertenece a la tienda" }.name
        val photo = product.imagePath?.takeIf { previous?.imagePath != it }?.let {
            checkNotNull(images) { "No está disponible el procesamiento de fotos." }.capture(it)
        }
        enqueue(shop, listOf(remote), buildJsonObject {
            put("type", "product_upsert"); put("product_id", remote)
            if (previous == null || previous.name != product.name) put("name", product.name)
            val code = product.miCatalogoInternalCode ?: product.internalCode
            if (previous == null || code != (previous.miCatalogoInternalCode ?: previous.internalCode)) put("internal_code", code)
            if (previous == null || previous.description != product.description) put("description", product.description?.let(::JsonPrimitive) ?: JsonNull)
            if (previous == null || previous.barcode != product.barcode) put("barcode", product.barcode?.let(::JsonPrimitive) ?: JsonNull)
            photo?.let { put("image_base64", it.base64); put("image_sha256", it.sha256) }
            if (previous == null || previous.categoryId != product.categoryId) {
                val assigned = !category.startsWith("Sin categoria") && !category.startsWith("Sin categoría")
                put("category_uuid", if (assigned) JsonPrimitive(product.categoryId.toString()) else JsonNull)
            }
            if (previous == null || previous.unitId != product.unitId) put("unit_uuid", product.unitId.toString())
            if (previous == null || previous.minimumStock != product.minimumStock) put("minimum_stock", product.minimumStock)
            if (previous == null || previous.isActive != product.isActive) put("published", product.isActive)
            if (previous == null || previous.miCatalogoSaleUnit != product.miCatalogoSaleUnit) {
                put("sale_unit", product.miCatalogoSaleUnit ?: "unit")
            }
            if (previous == null || previous.miCatalogoIsCombo != product.miCatalogoIsCombo) {
                put("is_combo", product.miCatalogoIsCombo)
            }
            if (previous == null || previous.miCatalogoComboItemsJson != product.miCatalogoComboItemsJson) {
                put("combo_items", buildJsonArray {
                    product.remoteComboItems().forEach { item ->
                        add(buildJsonObject {
                            put("product_id", item.productId)
                            put("quantity", item.quantity)
                        })
                    }
                })
            }
            if (previous == null || previous.miCatalogoVolumeMl != product.miCatalogoVolumeMl) {
                put("volume_ml", product.miCatalogoVolumeMl?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (previous == null || previous.miCatalogoSourceProductId != product.miCatalogoSourceProductId) {
                put("inventory_source_product_id", product.miCatalogoSourceProductId?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (previous == null || previous.salePrice != product.salePrice) {
                put("price", MiCatalogoPosSaleOutboxMapper.decimalPrice(product.salePrice))
                previous?.let { put("expected_price", MiCatalogoPosSaleOutboxMapper.decimalPrice(it.salePrice)) }
            }
            if (previous == null) put("cost_price", MiCatalogoPosSaleOutboxMapper.decimalPrice(product.lastPurchaseCost))
        }, product.updatedAt)
    }

    suspend fun archive(product: ProductEntity, at: Instant) {
        val shop = product.miCatalogoShopId ?: return
        require(product.tenant.shopId==shop && product.tenant.status==com.example.bspos.data.local.entity.TenantMetadata.ASSIGNED)
        val remote = checkNotNull(product.miCatalogoProductId)
        enqueue(shop, listOf(remote), buildJsonObject { put("type", "product_archive"); put("product_id", remote) }, at)
    }

    /**
     * Customers are business records too. Keep the local projection for
     * offline POS work, but always enqueue the authoritative customer change
     * when the account has an active MiCatalogo shop.
     */
    suspend fun customer(customer: CustomerEntity, at: Instant = customer.updatedAt) {
        val shop = customer.miCatalogoCustomerShopId ?: return
        require(customer.tenant.shopId==shop && customer.tenant.status==com.example.bspos.data.local.entity.TenantMetadata.ASSIGNED)
        val clientUuid = customer.id.toString()
        enqueue(shop, emptyList(), buildJsonObject {
            put("type", "customer_upsert")
            put("client_customer_uuid", clientUuid)
            customer.miCatalogoCustomerId?.let { put("customer_id",it) }
            put("name", customer.businessName)
            customer.firstName?.let { put("first_name", it) }
            customer.lastName?.let { put("last_name", it) }
            customer.documentType?.let { put("document_type", it) }
            customer.documentNumber?.let { put("document_number", it) }
            customer.phone?.let { put("phone", it) }
            customer.email?.let { put("email", it) }
            customer.address?.let { put("address", it) }
            customer.whatsapp?.let { put("whatsapp", it) }
            customer.reference?.let { put("reference", it) }
            customer.notes?.let { put("notes", it) }
            put("credit_limit", MiCatalogoPosSaleOutboxMapper.decimalPrice(customer.creditLimit))
            put("is_active", customer.isActive && customer.deletedAt == null)
            put("is_deleted",customer.deletedAt!=null)
        }, at, UUID.randomUUID().toString())
    }

    suspend fun enqueue(shop: String, products: List<String>, payload: JsonObject, at: Instant,
        id: String = UUID.randomUUID().toString()) {
        val body = JsonObject(payload + ("client_operation_uuid" to JsonPrimitive(id)))
        queue.insert(OperationOutboxEntity(id, shop, products.distinct().joinToString(","), body.toString(), at))
    }

    companion object {
        /** Stable 128-bit client identifier, encoded as a valid Crockford ULID. */
        fun productId(id: UUID): String {
            var value = BigInteger(id.toString().replace("-", ""), 16)
            val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
            val output = CharArray(26)
            for (index in 25 downTo 0) { output[index] = alphabet[value.and(BigInteger.valueOf(31)).toInt()]; value = value.shiftRight(5) }
            return String(output)
        }
    }

    private fun ProductEntity.remoteComboItems(): List<com.example.bspos.domain.model.ProductComboComponent> = runCatching {
        kotlinx.serialization.json.Json.decodeFromString<List<com.example.bspos.domain.model.ProductComboComponent>>(
            this@remoteComboItems.miCatalogoComboItemsJson
        )
    }.getOrDefault(emptyList<com.example.bspos.domain.model.ProductComboComponent>())
}
