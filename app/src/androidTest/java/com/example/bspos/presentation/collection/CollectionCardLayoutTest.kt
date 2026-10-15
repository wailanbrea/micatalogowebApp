package com.example.bspos.presentation.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Customer
import com.example.bspos.domain.model.PaymentMethod
import com.example.bspos.domain.model.Sale
import com.example.bspos.domain.model.SalePaymentType
import java.io.File
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated presentation tests: callbacks are captured; no database or API writes. */
class CollectionCardLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val customer = Customer(UUID.randomUUID(), "Distribuidora de Electrodomésticos y Perfumes del Caribe",
        balance = 99_999_999, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test fun remoteCollectionFitsSmallPhoneWithButtonBelowAmount() {
        var paid = 0L
        compose.setContent { BSPOSTheme {
            Box(Modifier.width(320.dp).statusBarsPadding().padding(horizontal = 16.dp)) {
                RemoteCustomerDebtCard(customer, PaymentMethod.TRANSFER, false) { paid = it }
            }
        } }
        screenshot("remote-small-phone")
        assertVerticalForm()
        compose.onNodeWithText("Monto a cobrar").performTextReplacement("200")
        compose.onNodeWithText("Cobrar").performClick()
        compose.runOnIdle { assertEquals(20_000L, paid) }
    }

    @Test fun invoiceCollectionKeepsLargeTextAndValidationInsideMargins() {
        val sale = Sale(UUID.randomUUID(), "FAC-2026-000012345", customer.id, date = Instant.EPOCH,
            subtotal = 99_999_999, total = 99_999_999, paymentType = SalePaymentType.CREDIT,
            pendingAmount = 99_999_999, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        compose.setContent { BSPOSTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                Box(Modifier.width(360.dp).statusBarsPadding().padding(horizontal = 16.dp)) {
                    PendingSaleCard(sale, customer, PaymentMethod.CASH, false) {}
                }
            }
        } }
        screenshot("invoice-large-text")
        assertVerticalForm()
        compose.onNodeWithText("Monto a cobrar").performTextReplacement("1000000")
        compose.onNodeWithText("Cobrar").assertIsNotEnabled()
        compose.onNodeWithText("Monto a cobrar").performTextReplacement("0")
        compose.onNodeWithText("Cobrar").assertIsNotEnabled()
    }

    @Test fun processingDisablesConfirmationWithoutChangingCallback() {
        var calls = 0
        compose.setContent { BSPOSTheme {
            Box(Modifier.width(320.dp).statusBarsPadding().padding(16.dp)) {
                RemoteCustomerDebtCard(customer, PaymentMethod.CASH, true) { calls++ }
            }
        } }
        compose.onNodeWithText("Procesando…").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun allPaymentMethodsFitTwoColumnsOnSmallPhone() {
        var selected: PaymentMethod? = null
        compose.setContent { BSPOSTheme {
            Column(Modifier.width(320.dp).statusBarsPadding().padding(16.dp)) {
                CollectionPaymentMethods(PaymentMethod.CASH) { selected = it }
            }
        } }
        screenshot("payment-methods-small-phone")
        val cash = compose.onNodeWithText("Efectivo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val transfer = compose.onNodeWithText("Transferencia").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val check = compose.onNodeWithText("Cheque").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithText("Tarjeta").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(transfer.left >= cash.right)
        assertTrue(check.top >= cash.bottom)
        assertEquals(cash.left, check.left, 2f)
        assertEquals(transfer.right, card.right, 2f)
        compose.onNodeWithText("Transferencia").performClick()
        compose.runOnIdle { assertEquals(PaymentMethod.TRANSFER, selected) }
    }

    private fun assertVerticalForm() {
        val field = compose.onNodeWithText("Monto a cobrar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Cobrar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Confirm must be below the amount, not squeezed beside it", button.top >= field.bottom)
        assertEquals("Amount and confirm align to the same margins", field.left, button.left, 2f)
        assertEquals("Confirm uses the same full width", field.right, button.right, 2f)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = checkNotNull(instrumentation.targetContext.getExternalFilesDir("collection-layout"))
        File(folder, "$name.png").outputStream().use { stream ->
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
