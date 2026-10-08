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

data class PurchaseLineDraft(
    val productId: String = "",
    val quantity: String = "1",
    val unitCost: String = ""
)

data class PurchaseModuleUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val workspace: PurchaseWorkspaceDto? = null,
    val preview: PurchaseInvoicePreviewDto? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class PurchaseModuleViewModel @Inject constructor(
    private val api: Lazy<MiCatalogoApi>,
    private val connection: MiCatalogoConnectionRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(PurchaseModuleUiState())
    val state: StateFlow<PurchaseModuleUiState> = _state.asStateFlow()

    fun load(feature: String = "containers") {
        viewModelScope.launch {
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
                _state.value = _state.value.copy(loading = false, workspace = workspace, error = null)
            }.onFailure { failure ->
                _state.value = _state.value.copy(loading = false, error = failure.message ?: "No se pudo cargar Compras.")
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
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(saving = true, error = null, message = null)
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
            runCatching {
                val response = api.get().createPurchase(shopId, request)
                if (!response.isSuccessful) error(response.errorBody()?.string().orEmpty().ifBlank { "No se pudo guardar la compra." })
                response.body()?.message ?: "Compra guardada."
            }.onSuccess { message ->
                _state.value = _state.value.copy(saving = false, message = message)
                load(featureForType(type))
            }.onFailure { failure ->
                _state.value = _state.value.copy(saving = false, error = failure.message ?: "No se pudo guardar la compra.")
            }
        }
    }

    private fun featureForType(type: String): String = when (type) {
        "load" -> "loads"
        "purchase_invoice", "supplier_debt" -> "purchase_invoices"
        else -> "containers"
    }

    fun createSupplier(name: String, invoiceCurrency: String, phone: String, email: String, address: String, notes: String) {
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(saving = true, error = null, message = null)
            runCatching {
                val response = api.get().createSupplier(
                    shopId,
                    SupplierCreateRequestDto(
                        name = name.trim(),
                        invoiceCurrency = invoiceCurrency.trim().uppercase().takeIf { it.isNotBlank() },
                        phone = phone.trim().takeIf { it.isNotBlank() },
                        email = email.trim().takeIf { it.isNotBlank() },
                        address = address.trim().takeIf { it.isNotBlank() },
                        notes = notes.trim().takeIf { it.isNotBlank() }
                    )
                )
                if (!response.isSuccessful) error(response.errorBody()?.string().orEmpty().ifBlank { "No se pudo guardar el suplidor." })
                response.body()?.message ?: "Suplidor guardado."
            }.onSuccess { message ->
                _state.value = _state.value.copy(saving = false, message = message)
                load()
            }.onFailure { failure ->
                _state.value = _state.value.copy(saving = false, error = failure.message ?: "No se pudo guardar el suplidor.")
            }
        }
    }

    fun previewInvoice(uri: Uri) {
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(saving = true, error = null, message = null)
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
                _state.value = _state.value.copy(saving = false, preview = preview)
            }.onFailure { failure ->
                _state.value = _state.value.copy(saving = false, error = failure.message ?: "No se pudo leer la factura.")
            }
        }
    }

    fun receive(documentId: String, feature: String = "containers") {
        viewModelScope.launch {
            val shopId = connection.activeShopId() ?: return@launch
            _state.value = _state.value.copy(saving = true, error = null, message = null)
            runCatching {
                val response = api.get().receivePurchase(shopId, documentId)
                if (!response.isSuccessful) error(response.errorBody()?.string().orEmpty().ifBlank { "No se pudo recibir la compra." })
                response.body()?.message ?: "Compra recibida."
            }.onSuccess { message ->
                _state.value = _state.value.copy(saving = false, message = message)
                load(feature)
            }.onFailure { failure ->
                _state.value = _state.value.copy(saving = false, error = failure.message ?: "No se pudo recibir la compra.")
            }
        }
    }
}
