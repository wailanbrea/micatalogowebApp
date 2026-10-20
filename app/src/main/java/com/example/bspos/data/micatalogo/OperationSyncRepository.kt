package com.example.bspos.data.micatalogo

import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.data.local.entity.OperationOutboxEntity
import com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject

enum class OperationSubmission { SENT, RETRY, BLOCKED }

/** Caller must preserve ordering relative to the sale outbox for the same tenant. */
class OperationSyncRepository @Inject constructor(private val api: MiCatalogoOperationApi,
    private val queue: OperationOutboxDao, private val json: Json) {
    suspend fun submit(row: OperationOutboxEntity): OperationSubmission {
        if (row.state == "BLOCKED") return OperationSubmission.BLOCKED
        val body = runCatching { json.parseToJsonElement(row.payload).jsonObject }.getOrNull()
        if (body == null || body["client_operation_uuid"]?.jsonPrimitive?.contentOrNull != row.id) {
            queue.mark(row.id, "BLOCKED", "La instantánea local no es válida; requiere revisión.")
            return OperationSubmission.BLOCKED
        }
        return try {
            val response = if (body["scope"]?.jsonPrimitive?.contentOrNull == "account") {
                api.submitAccount(body)
            } else {
                api.submit(row.shopId, body)
            }
            when {
                response.isSuccessful -> {
                    if (response.body()?.get("client_operation_uuid")?.jsonPrimitive?.contentOrNull != row.id) {
                        queue.mark(row.id,"PENDING","La respuesta no confirma esta operación; se reintentará con el mismo identificador.")
                        return OperationSubmission.RETRY
                    }
                    val ack = checkNotNull(response.body())
                    if (!confirmsResource(body, ack)) {
                        queue.mark(row.id, "PENDING", "La respuesta no incluye un comprobante válido; se reintentará con el mismo UUID.")
                        return OperationSubmission.RETRY
                    }
                    val expectedRecord = body["record_id"]?.jsonPrimitive?.contentOrNull
                    if (expectedRecord != null && ack["record_id"]?.jsonPrimitive?.contentOrNull != expectedRecord) {
                        queue.mark(row.id, "PENDING", "La respuesta no confirma el registro solicitado; se reintentará con el mismo UUID.")
                        return OperationSubmission.RETRY
                    }
                    queue.confirmSent(row.id, row.shopId, ack.toString())
                    OperationSubmission.SENT
                }
                response.code() in listOf(401, 426, 429) || response.code() >= 500 -> {
                    queue.mark(row.id,"PENDING",response.apiErrorMessage("La operación espera conexión, sesión válida o actualización de la app."))
                    OperationSubmission.RETRY
                }
                else -> {
                    queue.mark(row.id, "BLOCKED", response.apiErrorMessage("Revisa la operación antes de sincronizar."))
                    OperationSubmission.BLOCKED
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            queue.mark(row.id,"PENDING","No se pudo confirmar el envío. Se conserva la operación para reintentar.")
            OperationSubmission.RETRY
        }
    }

    private fun confirmsResource(body: JsonObject, ack: JsonObject): Boolean {
        fun nestedId(key: String) = (ack[key] as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val resource = body["resource_id"]?.jsonPrimitive?.contentOrNull
        val productId = body["product_id"]?.jsonPrimitive?.contentOrNull
        val imageHash = body["image_sha256"]?.jsonPrimitive?.contentOrNull
        return when (body["type"]?.jsonPrimitive?.contentOrNull) {
            "product_photo_update" -> !productId.isNullOrBlank() && !imageHash.isNullOrBlank() &&
                ack["product_id"]?.jsonPrimitive?.contentOrNull == productId && ack["image_sha256"]?.jsonPrimitive?.contentOrNull == imageHash
            "return" -> (ack["return_id"]?.jsonPrimitive?.longOrNull ?: 0) > 0
            "cash_open" -> nestedId("session") != null
            "cash_close" -> nestedId("session") == resource && resource != null
            "cash_movement" -> nestedId("movement") != null
            "expense_create" -> nestedId("expense") != null
            "expense_payment" -> nestedId("payment") != null && nestedId("expense") == resource && resource != null
            "quote_create" -> nestedId("quote") != null
            "quote_convert", "order_confirm" -> !ack["invoice_number"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
            "purchase_create", "purchase_receive" -> nestedId("document") != null
            "supplier_create" -> nestedId("supplier") != null
            "partner_create" -> nestedId("partner") != null
            "partner_transaction" -> nestedId("transaction") != null && nestedId("partner") == resource && resource != null
            "inventory_import_confirm" -> (ack["imported"]?.jsonPrimitive?.longOrNull ?: -1) >= 0
            "day_close" -> (ack["closure"] as? JsonObject)?.get("id") != null
            "day_close_adjustment" -> {
                val adjustment = ack["adjustment"] as? JsonObject
                val request = body["request"] as? JsonObject
                val expectedCount = request?.get("counted_cash")?.jsonPrimitive?.contentOrNull
                val confirmedCount = adjustment?.get("counted_cash")?.jsonPrimitive?.contentOrNull
                !resource.isNullOrBlank() && adjustment != null && nestedId("adjustment") != null &&
                    adjustment?.get("closure_id")?.jsonPrimitive?.contentOrNull == resource &&
                    adjustment.containsKey("counted_cash") &&
                    adjustment["expected_cash"]?.jsonPrimitive?.contentOrNull?.let { MiCatalogoImportMapper.cents(it); true } == true &&
                    (if (expectedCount == null) confirmedCount == null else confirmedCount != null && MiCatalogoImportMapper.cents(expectedCount) == MiCatalogoImportMapper.cents(confirmedCount)) &&
                    !request?.get("reason")?.jsonPrimitive?.contentOrNull.isNullOrBlank() &&
                    adjustment["reason"]?.jsonPrimitive?.contentOrNull == request?.get("reason")?.jsonPrimitive?.contentOrNull?.trim()
            }
            else -> true
        }
    }
}
