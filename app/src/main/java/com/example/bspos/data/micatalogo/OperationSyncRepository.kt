package com.example.bspos.data.micatalogo

import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.data.local.entity.OperationOutboxEntity
import com.example.bspos.data.micatalogo.api.MiCatalogoOperationApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
            val response = api.submit(row.shopId, body)
            when {
                response.isSuccessful -> {
                    if (response.body()?.get("client_operation_uuid")?.jsonPrimitive?.contentOrNull != row.id) {
                        queue.mark(row.id,"PENDING","La respuesta no confirma esta operación; se reintentará con el mismo identificador.")
                        return OperationSubmission.RETRY
                    }
                    queue.confirmSent(row.id, row.shopId)
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
}
