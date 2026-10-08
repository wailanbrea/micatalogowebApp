package com.example.bspos.data.micatalogo

import com.example.bspos.core.database.AppDatabaseTransactor
import com.example.bspos.data.local.dao.CategoryDao
import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.data.local.entity.OperationOutboxEntity
import com.example.bspos.data.local.entity.ProductEntity
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

    suspend fun product(product: ProductEntity, previous: ProductEntity? = null) {
        val shop = product.miCatalogoShopId ?: return
        val remote = checkNotNull(product.miCatalogoProductId)
        val category = categories.findById(product.categoryId)?.name
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
            if (previous == null || previous.categoryId != product.categoryId) category?.takeUnless { it.startsWith("Sin categoria") || it.startsWith("Sin categoría") }?.let { put("category_name", it) }
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
        val remote = checkNotNull(product.miCatalogoProductId)
        enqueue(shop, listOf(remote), buildJsonObject { put("type", "product_archive"); put("product_id", remote) }, at)
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
