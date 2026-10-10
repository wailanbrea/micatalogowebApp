package com.example.bspos.presentation.quote

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.FeatureProductDto
import com.example.bspos.data.micatalogo.dto.FeatureRowDto
import com.example.bspos.data.micatalogo.dto.RemoteCustomerDto
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuoteTerminalContentTest {
    @get:Rule val compose = createComposeRule()
    private val products = (1..25).map {
        FeatureProductDto(id = "$it", name = "Perfume de muestra $it", price = "950.00",
            category = if (it % 2 == 0) "Perfumes" else "Decants", stock = 0)
    }
    private var savedItems = 0
    private var savedCustomerId: String? = null
    private fun show(cart: Map<FeatureProductDto, Int> = emptyMap()) {
        compose.setContent {
            var state by remember { mutableStateOf(QuoteUiState(loading = false, products = products, cart = cart,
                rows = listOf(FeatureRowDto(id = "quote-1", primary = "COT-001", value = "RD$ 950.00", canConvert = true)),
                customers = listOf(RemoteCustomerDto(id = "customer-1", name = "María Gómez", phone = "8295550100")))) }
            BSPOSTheme {
                Scaffold { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                QuoteTerminalContent(state,
                    onAdd = { p -> state = state.copy(cart = state.cart + (p to ((state.cart[p] ?: 0) + 1))) },
                    onRemove = { p -> val qty = (state.cart[p] ?: 0) - 1; state = state.copy(cart = if (qty > 0) state.cart + (p to qty) else state.cart - p) },
                    onClear = { state = state.copy(cart = emptyMap()) },
                    onSave = { _, _, _, _, customerId -> savedCustomerId = customerId; savedItems = state.cart.size; state = state.copy(cart = emptyMap(), message = "Cotización guardada.") },
                    onConvert = {}, onOpenSales = {}, onOpenTerminal = {}, onNavigateBack = {})
                }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun compactCatalogFiltersAndAddsOutOfStockProductsWithoutSelling() {
        show()
        compose.onNodeWithText("Presupuestos guardados para convertirlos en venta.").assertDoesNotExist()
        compose.onNodeWithText("Perfume de muestra 1").performClick()
        compose.onNodeWithContentDescription("Aumentar Perfume de muestra 1").assertIsDisplayed().performClick()
        compose.onNodeWithText("Cotizar · 2 artículos").assertIsDisplayed()
        screenshot("quote-catalog")
        compose.onNodeWithText("Perfumes", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Perfume de muestra 1").assertDoesNotExist()
        compose.onNodeWithText("Perfume de muestra 2").assertIsDisplayed()
        compose.onNodeWithText("Guardadas").performClick()
        compose.onNodeWithText("COT-001").assertIsDisplayed()
        compose.onNodeWithText("Vigentes").assertIsDisplayed()
        compose.onNodeWithText("Convertir").performClick()
        compose.onNodeWithText("Convertir cotización en venta").assertIsDisplayed()
        compose.onNodeWithText("Revisar").performClick()
        compose.onNodeWithText("Convertir cotización en venta").assertDoesNotExist()
    }
    @Test fun longQuotationKeepsSaveFixedAndReturnsToSavedQuotes() {
        show(products.associateWith { 1 })
        compose.onNodeWithTag("quote-cart-open").performClick()
        compose.onNodeWithTag("quote-save").assertIsDisplayed().assertIsEnabled()
        repeat(3) { compose.onNodeWithTag("quote-cart-body").performTouchInput { swipeUp() } }
        compose.onNodeWithText("Nombre (opcional)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("quote-save").assertIsDisplayed()
        screenshot("quote-cart")
        compose.onNodeWithTag("quote-save").performClick()
        compose.onNodeWithText("Cotización guardada.").assertIsDisplayed()
        assertEquals(25, savedItems)
    }
    @Test fun selectsExistingCustomerOrGenericWithOptionalName() {
        show(mapOf(products.first() to 1))
        compose.onNodeWithTag("quote-cart-open").performClick()
        compose.onNodeWithTag("quote-customer-selector").performScrollTo().performClick()
        compose.onNodeWithText("María Gómez").performClick()
        compose.onNodeWithText("Nombre (opcional)").assertDoesNotExist()
        compose.onNodeWithText("María Gómez").assertIsDisplayed()
        compose.onNodeWithTag("quote-customer-selector").performClick()
        compose.onNodeWithText("Cliente genérico").performClick()
        compose.onNodeWithText("Nombre (opcional)").assertIsDisplayed().performTextReplacement("Cliente de mostrador")
        compose.onNodeWithTag("quote-customer-selector").performClick()
        compose.onNodeWithText("María Gómez").performClick()
        compose.onNodeWithTag("quote-save").performClick()
        assertEquals("customer-1", savedCustomerId)
    }
}
