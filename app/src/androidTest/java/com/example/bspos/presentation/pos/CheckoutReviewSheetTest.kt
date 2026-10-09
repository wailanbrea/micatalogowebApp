package com.example.bspos.presentation.pos

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Product
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckoutReviewSheetTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.now()
    private fun product(name: String, price: Long, cost: Long) = Product(
        UUID.randomUUID(), name, name, categoryId = UUID.randomUUID(), unitId = UUID.randomUUID(),
        salePrice = price, averageCost = cost, createdAt = now, updatedAt = now
    )
    private val initial = listOf(
        PosCartLine(product("Perfume Demo Puntto 100ml", 180000, 90000), 4, 180000),
        PosCartLine(product("Spray Demo Puntto", 95000, 50000), 6, 95000),
        PosCartLine(product("Crema Demo Puntto", 60000, 30000), 8, 60000)
    )
    private var confirmations = 0
    private fun show(lines: List<PosCartLine> = initial) {
        compose.setContent {
            var cart by remember { mutableStateOf(lines) }
            var method by remember { mutableStateOf(CheckoutReviewMethod.CASH) }
            var discount by remember { mutableLongStateOf(0) }
            BSPOSTheme {
                CheckoutReviewSheet(cart, cart.sumOf { it.quantity * it.unitPrice }, discount, null,
                    method, false, LocalDate.now(), lines.associate { it.product.id to it.quantity }, true, false,
                    onDismiss = {}, onCustomer = {}, onNewCustomer = {}, onMethodChange = { method = it }, onMixed = {},
                    onQuantity = { id, quantity -> cart = cart.map { if (it.product.id == id) it.copy(quantity = quantity) else it }.filter { it.quantity > 0 } },
                    onUnitPrice = { id, price -> cart = cart.map { if (it.product.id == id) it.copy(unitPrice = price) else it } },
                    onClear = { cart = emptyList() }, onDiscount = { discount = it }, onDate = {},
                    onConfirm = { _, _, _ -> confirmations++ }, showCosts = true)
            }
        }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun compactCheckoutKeepsHeaderAndConfirmVisibleWhileBodyScrolls() {
        show()
        compose.onNodeWithTag("checkout-total").assertTextEquals("RD$ 17,700.00")
        compose.onNodeWithContentDescription("Aumentar Perfume Demo Puntto 100ml").assertIsNotEnabled()
        screenshot("checkout-top")
        repeat(3) { compose.onNodeWithTag("checkout-body").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("checkout-confirm").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cobrar").assertIsDisplayed()
        compose.onNodeWithText("Transferencia").assertIsDisplayed().performClick()
        compose.onNodeWithText("Referencia bancaria (opcional)").assertIsDisplayed()
        compose.onNodeWithText("Efectivo").performClick()
        compose.waitForIdle()
        screenshot("checkout-payment")
        compose.onNodeWithTag("checkout-received").performTextReplacement("1")
        compose.onNodeWithTag("checkout-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("checkout-received").performTextReplacement("18000")
        compose.onNodeWithTag("checkout-confirm").assertIsEnabled()
        compose.onNodeWithTag("checkout-confirm").performClick()
        assertEquals(1, confirmations)
    }
    @Test fun longCartAndDiscountKeepConfirmationAccessible() {
        show(initial + (1..20).map { PosCartLine(product("Artículo $it", 10000, 5000), 1, 10000) })
        compose.onNodeWithTag("checkout-confirm").assertIsDisplayed()
        compose.onNodeWithText("Descuento o nota").performScrollTo().performClick()
        compose.onNodeWithText("Descuento (RD$)").performTextReplacement("100")
        compose.onNodeWithText("Aplicar").performClick()
        compose.onNodeWithTag("checkout-total").assertTextEquals("RD$ 19,600.00")
        compose.onNodeWithTag("checkout-confirm").assertIsDisplayed()
    }
    @Test fun creditAsksForTheInitialPaymentAndComputesTheDebt() {
        var abono = -1L
        val product = product("Producto de crédito de prueba", 950000, 100000)
        val customer = com.example.bspos.domain.model.Customer(UUID.randomUUID(), "Cliente local",
            creditLimit = 1000000, createdAt = now, updatedAt = now)
        compose.setContent {
            BSPOSTheme {
                CheckoutReviewSheet(cart = listOf(PosCartLine(product, 1)), subtotal = 950000, discount = 0,
                    customer = customer, method = CheckoutReviewMethod.CREDIT, wholesaleMode = false,
                    saleDate = LocalDate.now(), quantities = mapOf(product.id to 1L), creditEnabled = true, isProcessing = false,
                    onDismiss = {}, onCustomer = {}, onNewCustomer = {}, onMethodChange = {}, onMixed = {},
                    onQuantity = { _, _ -> }, onUnitPrice = { _, _ -> }, onClear = {}, onDiscount = {}, onDate = {},
                    onConfirm = { _, _, _ -> }, onCreditConfirm = { amount, _, _, _, _ -> abono = amount })
            }
        }
        compose.onNodeWithTag("credit-down-payment").performScrollTo().performTextReplacement("1000")
        compose.onNodeWithText("Saldo pendiente calculado").assertExists()
        compose.onNodeWithText("RD$ 8,500.00").assertExists()
        compose.onNodeWithTag("checkout-confirm").assertIsEnabled().performClick()
        assertEquals(100000L, abono)
    }
}
