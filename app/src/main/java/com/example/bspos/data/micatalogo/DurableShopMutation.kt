package com.example.bspos.data.micatalogo

import com.example.bspos.core.database.ShopScope
import com.example.bspos.data.local.dao.OperationOutboxDao
import kotlinx.serialization.json.*
import java.time.Instant
import javax.inject.Inject

/** Durable intent before HTTP; a process restart reuses the stored snapshot and UUID. */
class DurableShopMutation @Inject constructor(
    private val recorder: RemoteMutationRecorder,
    private val queue: OperationOutboxDao,
    private val sync: OperationSyncRepository,
    private val shops: ShopScope,
    private val json: Json,
    private val readBack: dagger.Lazy<CatalogRefreshSyncRepository>
) : com.example.bspos.domain.repository.ShopMutationGateway {
    override suspend fun submit(shop: String, type: String, request: JsonObject, resource: String?): JsonObject = shops.mutate { active ->
        require(active == shop) { "La tienda cambió; vuelve a abrir el formulario." }
        val id = request["client_operation_uuid"]?.jsonPrimitive?.contentOrNull ?: java.util.UUID.randomUUID().toString()
        val stableRequest = JsonObject(request + ("client_operation_uuid" to JsonPrimitive(id)))
        val payload = buildJsonObject {
            put("type", type); put("request", stableRequest)
            resource?.let { put("resource_id", it) }
        }
        val row = recorder.transaction {
            val existing = queue.activeForShop(shop).firstOrNull { row ->
                val old = json.parseToJsonElement(row.payload).jsonObject
                old["type"] == payload["type"] && old["resource_id"] == payload["resource_id"]
            }
            if (existing != null) {
                val original = json.parseToJsonElement(existing.payload).jsonObject
                val normalized = JsonObject(original - "client_operation_uuid" + ("request" to JsonObject(original.getValue("request").jsonObject - "client_operation_uuid")))
                val proposed = JsonObject(payload + ("request" to JsonObject(stableRequest - "client_operation_uuid")))
                require(normalized == proposed) { "Hay una operación pendiente para este recurso. Revísala antes de registrar otra distinta." }
                check(existing.state != "BLOCKED") { existing.error ?: "La operación requiere revisión." }
                existing
            } else {
                recorder.enqueue(shop, emptyList(), payload, Instant.now(), id)
                checkNotNull(queue.find(id))
            }
        }
        recorder.wake()
        val outcome = if (queue.find(row.id)?.state == "SENT") OperationSubmission.SENT else sync.submit(row)
        when (outcome) {
            OperationSubmission.SENT -> {
                val receipt = json.parseToJsonElement(checkNotNull(queue.find(row.id)?.serverResponse)).jsonObject
                readBack.get().sync()
                receipt
            }
            OperationSubmission.RETRY -> error("Pendiente de sincronización. Se conserva para reintentar con el mismo identificador.")
            OperationSubmission.BLOCKED -> error(queue.find(row.id)?.error ?: "La operación requiere revisión.")
        }
    }
}
