package com.example.bspos.presentation.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.PurchaseCreateRequestDto
import com.example.bspos.data.micatalogo.dto.PurchaseItemRequestDto
import com.example.bspos.data.micatalogo.dto.PurchaseWorkspaceDto
import com.example.bspos.data.micatalogo.dto.PurchaseInvoicePreviewDto
import com.example.bspos.data.micatalogo.dto.SupplierCreateRequestDto
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import com.example.bspos.domain.model.MiCatalogoResult
import kotlinx.serialization.json.*

data class PurchaseLineDraft(
    val productId: String = "",
    val quantity: String = "1",
    val unitCost: String = ""
)

data class PurchaseModuleUiState(
    val access: PurchaseAccess = PurchaseAccess(),
    val shopId: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val workspace: PurchaseWorkspaceDto? = null,
    val preview: PurchaseInvoicePreviewDto? = null,
    val photoReview: InvoicePhotoReview? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class PurchaseModuleViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository,
    @param:ApplicationContext private val context: Context,
    private val durable: com.example.bspos.domain.repository.ShopMutationGateway,
    private val shops: com.example.bspos.core.database.ShopScope,
    private val events: com.example.bspos.data.micatalogo.ShopReadModelEvents,
    private val json: Json
) : ViewModel() {
    private val _state = MutableStateFlow(PurchaseModuleUiState())
    val state: StateFlow<PurchaseModuleUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var activeFeature: String? = null
    private var permissionEpoch = 0L
    private var loadVersion = 0L
    private var permissionsResolved = false
    private var actorContext = ""
    private var previewJob: Job? = null
    init {
        viewModelScope.launch {
            combine(connection.observeConnection(), connection.observeShops().onStart { emit(null) }) { state, snapshot -> state to snapshot }
                .collectLatest { (state, snapshot) ->
                    val actor = "${state.accountEmail}:${state.role}:${state.isConfigured}"
                    if (_state.value.shopId != state.activeShopId || actorContext != actor) {
                        actorContext = actor
                        permissionsResolved = false
                        invalidate(state.activeShopId, PurchaseAccess())
                    }
                    val available = if (!state.isConfigured) emptyList() else snapshot ?: when (val result = connection.shops()) {
                        is MiCatalogoResult.Success -> result.value
                        is MiCatalogoResult.Failure -> emptyList()
                    }
                    if (connection.activeShopId() != state.activeShopId) return@collectLatest
                    val access = PurchaseAccess.forShop(available.firstOrNull { it.id == state.activeShopId }, state.role)
                    val changed = !permissionsResolved || _state.value.access != access
                    permissionsResolved = true
                    if (changed) {
                        invalidate(state.activeShopId, access)
                        activeFeature?.let(::load)
                    }
                }
        }
        viewModelScope.launch { events.changes.collect { if (connection.activeShopId() == it) activeFeature?.let(::load) } }
    }

    private fun invalidate(shop: String?, access: PurchaseAccess) {
        permissionEpoch++; loadVersion++
        loadJob?.cancel(); previewJob?.cancel()
        _state.value = PurchaseModuleUiState(shopId = shop, access = access, loading = !permissionsResolved)
    }

    private suspend fun current(shop: String, epoch: Long): Boolean = permissionEpoch == epoch &&
        _state.value.shopId == shop && connection.activeShopId() == shop

    fun load(feature: String = activeFeature ?: "containers") {
        activeFeature = feature; loadJob?.cancel()
        val version = ++loadVersion
        if (!permissionsResolved) return
        if (!_state.value.access.reads(feature)) {
            _state.value = _state.value.copy(loading = false, workspace = null, preview = null, photoReview = null, error = "No tienes permiso para este módulo de Compras.")
            return
        }
        val epoch = permissionEpoch
        loadJob = viewModelScope.launch {
            val shopId = connection.activeShopId()
            if (shopId.isNullOrBlank()) {
                _state.value = PurchaseModuleUiState(loading = false, error = "Selecciona una tienda activa para gestionar Compras.")
                return@launch
            }
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val response = if (feature == "suppliers") api.get().suppliers(shopId) else api.get().purchases(shopId)
                if (!response.isSuccessful) error(response.errorBody()?.string().orEmpty().ifBlank { "No se pudo cargar Compras." })
                response.body() ?: error("MiCatalogo devolvió una respuesta vacía.")
            }.onSuccess { workspace ->
                if (current(shopId, epoch) && version == loadVersion) _state.value = _state.value.copy(shopId = shopId, loading = false,
                    workspace = workspace.copy(documents = workspace.documents.filter(_state.value.access::reads),
                        products = if (_state.value.access.previews) workspace.products else emptyList()), error = null)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                if (current(shopId, epoch) && version == loadVersion) _state.value = _state.value.copy(loading = false, error = failure.message ?: "No se pudo cargar Compras.")
            }
        }
    }

    fun create(
        type: String,
        documentNumber: String,
        supplierId: String?,
        mode: String,
        notes: String?,
        lines: List<PurchaseLineDraft>,
        currency: String = "DOP",
        exchangeRate: String? = null,
        carrier: String? = null,
        trackingNumber: String? = null,
        expectedAt: String? = null,
        shippingPounds: String? = null,
        freightAmount: String? = null,
        customsAmount: String? = null,
        paymentStatus: String = "pending",
        parentDocumentId: String? = null,
        amount: String? = null,
        invoiceDate: String? = null,
        dueAt: String? = null
    ) {
        mutate({ creates(type) }, "No se pudo guardar la compra.") { shopId ->
            val request = PurchaseCreateRequestDto(
                type = type,
                documentNumber = documentNumber.trim().ifBlank { "DEUDA-${UUID.randomUUID().toString().take(8).uppercase()}" },
                supplierId = supplierId,
                invoiceDate = invoiceDate?.trim()?.takeIf { it.isNotBlank() },
                dueAt = dueAt?.trim()?.takeIf { it.isNotBlank() },
                amount = amount?.trim()?.replace(',', '.')?.takeIf { it.isNotBlank() },
                currency = currency.trim().uppercase().ifBlank { "DOP" },
                exchangeRate = exchangeRate?.trim()?.takeIf { it.isNotBlank() },
                carrier = carrier?.trim()?.takeIf { it.isNotBlank() },
                trackingNumber = trackingNumber?.trim()?.takeIf { it.isNotBlank() },
                expectedAt = expectedAt?.trim()?.takeIf { it.isNotBlank() },
                shippingPounds = shippingPounds?.trim()?.takeIf { it.isNotBlank() },
                freightAmount = freightAmount?.trim()?.takeIf { it.isNotBlank() },
                customsAmount = customsAmount?.trim()?.takeIf { it.isNotBlank() },
                paymentStatus = paymentStatus,
                parentDocumentId = parentDocumentId,
                mode = mode,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                items = lines.map { line ->
                    PurchaseItemRequestDto(
                        productId = line.productId,
                        quantity = line.quantity.toIntOrNull() ?: 0,
                        unitCost = line.unitCost.replace(',', '.').ifBlank { "0" }
                    )
                }
            )
            durable.submit(shopId, "purchase_create", json.encodeToJsonElement(PurchaseCreateRequestDto.serializer(), request).jsonObject)["message"]?.jsonPrimitive?.content ?: "Compra sincronizada."
        }
    }

    fun createSupplier(name: String, invoiceCurrency: String, phone: String, email: String, address: String, notes: String) {
        mutate({ reads("suppliers") }, "No se pudo guardar el suplidor.") { shopId ->
                val request = SupplierCreateRequestDto(
                        name = name.trim(),
                        invoiceCurrency = invoiceCurrency.trim().uppercase().takeIf { it.isNotBlank() },
                        phone = phone.trim().takeIf { it.isNotBlank() },
                        email = email.trim().takeIf { it.isNotBlank() },
                        address = address.trim().takeIf { it.isNotBlank() },
                        notes = notes.trim().takeIf { it.isNotBlank() }
                    )
            durable.submit(shopId, "supplier_create", json.encodeToJsonElement(SupplierCreateRequestDto.serializer(), request).jsonObject)["message"]?.jsonPrimitive?.content ?: "Suplidor sincronizado."
        }
    }

    fun previewInvoice(uri: Uri) {
        if (!_state.value.access.previews) { _state.value = _state.value.copy(error = "No tienes permiso para leer facturas de compra."); return }
        previewJob?.cancel()
        val epoch = permissionEpoch
        previewJob = viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(saving = true, error = null, message = null, preview = null, photoReview = null)
            if (context.contentResolver.getType(uri)?.startsWith("image/") == true) {
                try {
                    val text = InvoicePhotoReader.read(context, uri)
                    if (current(shopId, epoch)) _state.value = _state.value.copy(saving = false,
                        photoReview = InvoicePhotoReview(text, photoInvoiceSuggestions(text, _state.value.workspace?.products.orEmpty())))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (current(shopId, epoch)) _state.value = _state.value.copy(saving = false, error = failure.message ?: "No se pudo leer la foto.")
                }
                return@launch
            }
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No se pudo leer el archivo seleccionado.")
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val name = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "factura"
                val body = bytes.toRequestBody(mime.toMediaType())
                val part = MultipartBody.Part.createFormData("file", name, body)
                val response = api.get().previewPurchaseInvoice(shopId, part)
                if (!response.isSuccessful) error(response.errorBody()?.string().orEmpty().ifBlank { "No se pudo leer la factura." })
                response.body() ?: error("El lector no devolvió líneas.")
            }.onSuccess { preview ->
                if (current(shopId, epoch)) _state.value = _state.value.copy(saving = false, preview = preview)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                if (current(shopId, epoch)) _state.value = _state.value.copy(saving = false, error = failure.message ?: "No se pudo leer la factura.")
            }
        }
    }

    fun dismissPhotoReview() { _state.value = _state.value.copy(photoReview = null) }

    fun receive(documentId: String, feature: String = "containers") {
        val document = _state.value.workspace?.documents?.firstOrNull { it.id == documentId }
        mutate({ reads(feature) && document != null && receives(document) }, "No se pudo recibir la compra.") { shop ->
            durable.submit(shop, "purchase_receive", buildJsonObject {}, documentId)["message"]?.jsonPrimitive?.content ?: "Compra recibida y sincronizada."
        }
    }

    private fun mutate(allowed: PurchaseAccess.() -> Boolean, fallback: String, operation: suspend (String) -> String) {
        if (_state.value.saving) return
        if (!permissionsResolved || !_state.value.access.allowed()) {
            _state.value = _state.value.copy(error = "No tienes permiso para esta operación de Compras.")
            return
        }
        val shop = _state.value.shopId ?: return
        val epoch = permissionEpoch
        _state.value = _state.value.copy(saving = true, error = null, message = null)
        viewModelScope.launch {
            runCatching {
                shops.mutate { active ->
                    check(active == shop && current(shop, epoch) && _state.value.access.allowed()) { "El permiso o la tienda cambió; vuelve a abrir el formulario." }
                    operation(shop)
                }
            }.onSuccess { message ->
                if (!current(shop, epoch)) return@onSuccess
                _state.value = _state.value.copy(saving = false, message = message)
                activeFeature?.let(::load)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                if (current(shop, epoch)) _state.value = _state.value.copy(saving = false, error = failure.message ?: fallback)
            }
        }
    }
}
