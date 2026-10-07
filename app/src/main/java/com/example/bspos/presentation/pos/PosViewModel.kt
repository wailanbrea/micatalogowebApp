package com.example.bspos.presentation.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.InventoryLocation
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.SalePaymentType
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SaleStatus
import com.example.bspos.domain.repository.SaleRepository
import com.example.bspos.data.printer.BluetoothPrinterClient
import com.example.bspos.data.printer.BluetoothPrinterRepository
import com.example.bspos.domain.repository.CustomerRepository
import com.example.bspos.domain.repository.CategoryRepository
import com.example.bspos.domain.repository.InventoryRepository
import com.example.bspos.domain.repository.ProductRepository
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import com.example.bspos.domain.repository.SettingsRepository
import com.example.bspos.domain.model.AppSettings
import com.example.bspos.domain.usecase.CompleteSaleRequest
import com.example.bspos.domain.usecase.CompleteSaleUseCase
import com.example.bspos.domain.usecase.CashSessionUseCases
import com.example.bspos.domain.usecase.SaleLineInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class PosCartLine(val product: Product, val quantity: Long, val unitPrice: Long = product.salePrice)

data class CheckoutResult(
    val success: Boolean,
    val message: String,
    val sale: Sale? = null,
    val lines: List<PosCartLine> = emptyList()
)

@HiltViewModel
class PosViewModel @Inject constructor(
    productsRepository: ProductRepository,
    categoryRepository: CategoryRepository,
    customers: CustomerRepository,
    inventory: InventoryRepository,
    settingsRepository: SettingsRepository,
    connectionRepository: MiCatalogoConnectionRepository,
    sales: SaleRepository,
    private val completeSale: CompleteSaleUseCase,
    cashSessions: CashSessionUseCases,
    private val printerRepository: BluetoothPrinterRepository,
    private val printerClient: BluetoothPrinterClient
) : ViewModel() {
    val products = connectionRepository.observeConnection()
        .flatMapLatest { state ->
            state.activeShopId?.let(productsRepository::observeForShop) ?: productsRepository.observeAll()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentProductIds = combine(sales.observeAll(), sales.observeAllItems()) { invoices, items ->
        val completedIds = invoices
            .asSequence()
            .filter { it.status == SaleStatus.COMPLETED }
            .sortedByDescending { it.date }
            .map { it.id }
            .toList()

        completedIds
            .flatMap { saleId -> items.filter { it.saleId == saleId }.map { it.productId } }
            .distinct()
            .take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = categoryRepository.observeAll()
        .map { records ->
            records
                .filter { it.isActive && it.deletedAt == null }
                .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customers = customers.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stock = inventory.observeStock(InventoryLocation.MAIN).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = settingsRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val cashSession = cashSessions.observeOpen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _cart = MutableStateFlow<List<PosCartLine>>(emptyList())
    val cart = _cart.asStateFlow()
    private val _customer = MutableStateFlow<Customer?>(null)
    val customer = _customer.asStateFlow()
    private val _wholesaleMode = MutableStateFlow(false)
    val wholesaleMode = _wholesaleMode.asStateFlow()
    val cartTotal = _cart.combine(_wholesaleMode) { lines, _ ->
        lines.fold(0L) { total, line ->
            Math.addExact(total, Math.multiplyExact(line.quantity, line.unitPrice))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()
    private val _checkoutResult = MutableStateFlow<CheckoutResult?>(null)
    val checkoutResult = _checkoutResult.asStateFlow()
    val printers = printerRepository.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _printerMessage = MutableStateFlow<String?>(null)
    val printerMessage = _printerMessage.asStateFlow()

    fun selectCustomer(value: Customer?) {
        _customer.value = value
    }

    fun setWholesaleMode(enabled: Boolean) {
        if (enabled && _cart.value.any { (it.product.wholesalePrice ?: 0L) <= 0L }) {
            _checkoutResult.value = CheckoutResult(false, "Quita del carrito los productos sin precio por mayor antes de cambiar el tipo de venta")
            return
        }
        _wholesaleMode.value = enabled
        _cart.value = _cart.value.map { it.copy(unitPrice = price(it.product)) }
    }

    private fun price(product: Product): Long = if (_wholesaleMode.value) product.wholesalePrice ?: product.salePrice else product.salePrice

    fun add(product: Product) {
        val available = stock.value.firstOrNull { it.productId == product.id }?.quantity ?: 0L
        val maximum = if (product.remoteSaleUnit == "service" || settings.value.allowNegativeStock) Long.MAX_VALUE else available
        _cart.value = _cart.value.toMutableList().also { lines ->
            val index = lines.indexOfFirst { it.product.id == product.id }
            val current = if (index < 0) 0 else lines[index].quantity
            if (current >= maximum) return@also
            if (index < 0) lines += PosCartLine(product, 1, price(product))
            else lines[index] = lines[index].copy(quantity = current + 1)
        }
    }

    fun change(productId: UUID, quantity: Long) {
        val product = products.value.firstOrNull { it.id == productId }
        val available = stock.value.firstOrNull { it.productId == productId }?.quantity ?: 0L
        val maximum = if (product?.remoteSaleUnit == "service" || settings.value.allowNegativeStock) Long.MAX_VALUE else available
        val next = quantity.coerceIn(0, maximum)
        _cart.value = _cart.value.mapNotNull {
            if (it.product.id != productId) it
            else if (next > 0) it.copy(quantity = next)
            else null
        }
    }

    fun clearCart() {
        if (_isProcessing.value) return
        _cart.value = emptyList()
    }

    fun completeCash() = complete(SalePaymentType.CASH, null)
    fun completeCard() = complete(SalePaymentType.CARD, null)
    fun completeTransfer() = complete(SalePaymentType.TRANSFER, null)
    fun completeCredit() = _customer.value?.let { complete(SalePaymentType.CREDIT, it) }

    fun completeSplit(payments: List<com.example.bspos.domain.usecase.PosPaymentSplitInput>, dueDate: String? = null) {
        val lines = _cart.value
        if (lines.isEmpty() || _isProcessing.value) return
        val total = try {
            lines.sumOf { Math.multiplyExact(it.quantity, price(it.product)) }
        } catch (_: ArithmeticException) {
            _checkoutResult.value = CheckoutResult(false, "El total de la venta excede el límite permitido")
            return
        }

        val paidAmount: Long
        val pendingAmount: Long
        try {
            if (payments.any { it.amountCents <= 0L || it.method !in setOf("cash", "card", "bank_transfer", "credit") }) {
                _checkoutResult.value = CheckoutResult(false, "Revisa los métodos e importes de pago")
                return
            }
            paidAmount = payments.filter { it.method != "credit" }.fold(0L) { sum, payment -> Math.addExact(sum, payment.amountCents) }
            pendingAmount = payments.filter { it.method == "credit" }.fold(0L) { sum, payment -> Math.addExact(sum, payment.amountCents) }
        } catch (_: ArithmeticException) {
            _checkoutResult.value = CheckoutResult(false, "Los importes de pago exceden el límite permitido")
            return
        }

        if (try { Math.addExact(paidAmount, pendingAmount) } catch (_: ArithmeticException) { -1L } != total) {
            _checkoutResult.value = CheckoutResult(false, "La suma de los pagos no coincide con el total de la venta")
            return
        }

        val customer = _customer.value
        if (pendingAmount > 0L && customer == null) {
            _checkoutResult.value = CheckoutResult(false, "Debes seleccionar un cliente para la porción a crédito")
            return
        }

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val sale = completeSale(
                    CompleteSaleRequest(
                        invoiceNumber = "POS-${System.currentTimeMillis()}",
                        customerId = customer?.id,
                        date = Instant.now(),
                        lines = lines.map { SaleLineInput(it.product.id, it.quantity, price(it.product)) },
                        paymentType = if (payments.size > 1) SalePaymentType.MIXED else when (payments.firstOrNull()?.method) {
                            "cash" -> SalePaymentType.CASH
                            "card" -> SalePaymentType.CARD
                            "bank_transfer" -> SalePaymentType.TRANSFER
                            "credit" -> SalePaymentType.CREDIT
                            else -> SalePaymentType.MIXED
                        },
                        paidAmount = paidAmount,
                        pendingAmount = pendingAmount,
                        splitPayments = payments,
                        dueDate = dueDate,
                        saleMode = if (_wholesaleMode.value) "wholesale" else "retail"
                    )
                )
                _cart.value = emptyList()
                _customer.value = null
                _checkoutResult.value = CheckoutResult(
                    success = true,
                    message = "Venta dividida cobrada exitosamente",
                    sale = sale,
                    lines = lines
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _checkoutResult.value = CheckoutResult(false, "No se pudo cobrar: ${friendlyMessage(error)}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun consumeCheckoutResult() {
        _checkoutResult.value = null
    }

    fun printToConfiguredPrinter(result: CheckoutResult) {
        val sale = result.sale ?: return
        printSaleToConfiguredPrinter(sale, result.lines)
    }

    fun printSaleToConfiguredPrinter(sale: Sale, lines: List<PosCartLine>) {
        val printer = printers.value.firstOrNull { it.isDefault } ?: printers.value.firstOrNull()
        if (printer == null) {
            _printerMessage.value = "No hay una impresora Bluetooth configurada"
            return
        }
        viewModelScope.launch {
            runCatching {
                printerClient.print(
                    printer,
                    formatInvoice(
                        sale,
                        lines,
                        settings.value.currency,
                        settings.value.invoice,
                        printer.paperWidth.columns
                    )
                )
            }
                .onSuccess { _printerMessage.value = "Factura enviada a ${printer.name}" }
                .onFailure { _printerMessage.value = "No se pudo imprimir: ${it.message ?: "verifica la conexión Bluetooth"}" }
        }
    }

    fun consumePrinterMessage() {
        _printerMessage.value = null
    }

    private fun complete(type: SalePaymentType, customer: Customer?) {
        val lines = _cart.value
        if (lines.isEmpty() || _isProcessing.value) return
        val total = try {
            lines.sumOf { Math.multiplyExact(it.quantity, price(it.product)) }
        } catch (_: ArithmeticException) {
            _checkoutResult.value = CheckoutResult(false, "El total de la venta excede el límite permitido")
            return
        }
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val sale = completeSale(
                    CompleteSaleRequest(
                        invoiceNumber = "POS-${System.currentTimeMillis()}",
                        customerId = customer?.id,
                        date = Instant.now(),
                        lines = lines.map { SaleLineInput(it.product.id, it.quantity, price(it.product)) },
                        paymentType = type,
                        paidAmount = if (type == SalePaymentType.CREDIT) 0 else total,
                        pendingAmount = if (type == SalePaymentType.CREDIT) total else 0,
                        saleMode = if (_wholesaleMode.value) "wholesale" else "retail"
                    )
                )
                _cart.value = emptyList()
                _customer.value = null
                _checkoutResult.value = CheckoutResult(
                    success = true,
                    message = if (type == SalePaymentType.CREDIT) "Venta registrada correctamente a credito" else "Venta cobrada correctamente con ${type.label}",
                    sale = sale,
                    lines = lines
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _checkoutResult.value = CheckoutResult(false, "No se pudo cobrar: ${friendlyMessage(error)}")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun friendlyMessage(error: Throwable): String = when {
        error.message?.contains("Insufficient stock", ignoreCase = true) == true -> "stock insuficiente"
        error.message?.contains("cash session", ignoreCase = true) == true -> "abre una caja antes de cobrar en efectivo"
        !error.message.isNullOrBlank() -> error.message.orEmpty()
        else -> "revisa los datos e inténtalo de nuevo"
    }

    private val SalePaymentType.label: String
        get() = when (this) {
            SalePaymentType.CASH -> "efectivo"
            SalePaymentType.CARD -> "tarjeta"
            SalePaymentType.TRANSFER -> "transferencia"
            SalePaymentType.CREDIT -> "crédito"
            SalePaymentType.MIXED -> "pago mixto"
        }
}
