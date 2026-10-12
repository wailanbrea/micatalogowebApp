package com.example.bspos.data.micatalogo

import com.example.bspos.data.local.dao.OperationOutboxDao
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Durable account-scoped intent for support, profile and other cross-shop actions. */
class DurableAccountMutation @Inject constructor(
    private val recorder: RemoteMutationRecorder,
    private val queue: OperationOutboxDao,
    private val sync: OperationSyncRepository,
    private val connection: MiCatalogoConnectionRepository,
    private val json: Json
) {
    suspend fun submit(
        type: String,
        request: JsonObject,
        resourceId: String? = null,
        shopId: String? = null
    ): JsonObject {
        val actorId = checkNotNull(connection.accountId()) { "La sesión no tiene identificador de cuenta." }
        val id = request["client_operation_uuid"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("scope", JsonPrimitive("account"))
            put("actor_id", JsonPrimitive(actorId))
            put("client_operation_uuid", JsonPrimitive(id))
            put("type", JsonPrimitive(type))
            put("request", JsonObject(request - "client_operation_uuid"))
            resourceId?.let { put("resource_id", JsonPrimitive(it)) }
            if (type == "support_create") put("shop_id", JsonPrimitive(checkNotNull(shopId) { "La conversación necesita una tienda." }))
        }

        val row = recorder.transaction {
            val existing = queue.find(id)
            if (existing != null) {
                val old = json.parseToJsonElement(existing.payload).jsonObject
                check(old == payload) { "El identificador ya fue utilizado con otro contenido." }
                existing
            } else {
                recorder.enqueue(ACCOUNT_SCOPE, emptyList(), payload, Instant.now(), id)
                checkNotNull(queue.find(id))
            }
        }
        recorder.wake()

        return when (if (row.state == "SENT") OperationSubmission.SENT else sync.submit(row)) {
            OperationSubmission.SENT -> {
                val receipt = json.parseToJsonElement(checkNotNull(queue.find(row.id)?.serverResponse)).jsonObject
                (receipt["result"] as? JsonObject) ?: receipt
            }
            OperationSubmission.RETRY -> error("Pendiente de sincronización. Se conserva la operación para reintentar con el mismo identificador.")
            OperationSubmission.BLOCKED -> error(queue.find(row.id)?.error ?: "La operación requiere revisión.")
        }
    }

    companion object {
        const val ACCOUNT_SCOPE = "__account__"
    }
}
