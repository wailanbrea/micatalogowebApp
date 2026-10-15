package com.example.bspos.presentation.catalog

import androidx.compose.ui.test.*
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.Product
import com.example.bspos.domain.model.UnitOfMeasure
import com.example.bspos.domain.usecase.ProductInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProductFormTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newPerfumeUsesBottleFlowAndOffersGuidedDecantPreparation() {
        val now = Instant.now()
        val categories = listOf(Category(UUID.randomUUID(), "Perfumes", createdAt = now, updatedAt = now))
        val units = listOf(UnitOfMeasure(UUID.randomUUID(), "Unidad", "und", createdAt = now, updatedAt = now))
        var saved: Triple<ProductInput, Long, Boolean>? = null

        compose.setContent {
            BSPOSTheme {
                ProductForm(
                    current = null,
                    currentQuantity = 0,
                    categories = categories,
                    units = units,
                    presentation = MiCatalogoBusinessPresentation(archetype = "fragrance"),
                    showCost = true,
                    showInventoryFields = true,
                    showProductCode = true,
                    onSave = { input, quantity, prepare -> saved = Triple(input, quantity, prepare) },
                    onDismiss = {},
                    shops = emptyList(),
                    activeShopId = null,
                    initialSaleUnit = "unit",
                    enableDecantPrompt = true
                )
            }
        }

        compose.onNodeWithTag("product-name-field").assertExists()
        compose.onNodeWithText("Nombre").assertExists()
        compose.onNodeWithTag("product-category-selector").assertExists()
        compose.onNodeWithTag("product-unit-selector").assertExists()
        compose.onNodeWithText("Código interno").assertDoesNotExist()

        compose.onNodeWithTag("sale-unit-bottle").performClick()
        compose.onNodeWithTag("product-name-field").performTextInput("Perfume de prueba")
        compose.onNodeWithTag("product-volume-ml").performTextInput("100")
        compose.onNodeWithTag("product-purchase-price").performTextInput("1000")
        compose.onNodeWithTag("product-sale-price").performTextInput("2500")
        compose.onNodeWithTag("product-initial-stock").performTextInput("1")
        compose.onNodeWithTag("product-save-button").performClick()

        compose.onNodeWithText("¿Preparar decants ahora?").assertExists()
        compose.onNodeWithText("Guardar y preparar").performClick()
        compose.runOnIdle {
            val result = saved ?: error("El formulario no guardó el producto")
            assertEquals("bottle", result.first.remoteSaleUnit)
            assertEquals(100, result.first.remoteVolumeMl)
            assertEquals("Perfume de prueba", result.first.name)
            assertEquals(true, result.third)
        }
    }

    @Test fun comboCanSelectMultipleProductsAndSavesTheirComponentQuantities() {
        val now = Instant.now()
        val categoryId = UUID.randomUUID()
        val unitId = UUID.randomUUID()
        val categories = listOf(Category(categoryId, "Perfumes", createdAt = now, updatedAt = now))
        val units = listOf(UnitOfMeasure(unitId, "Unidad", "und", createdAt = now, updatedAt = now))
        val shopId = "shop-qa"
        val components = listOf(
            Product(UUID.randomUUID(), "Perfume componente A", "SKU-A", categoryId = categoryId, unitId = unitId, salePrice = 10000, createdAt = now, updatedAt = now, remoteShopId = shopId, remoteProductId = "remote-a"),
            Product(UUID.randomUUID(), "Perfume componente B", "SKU-B", categoryId = categoryId, unitId = unitId, salePrice = 20000, createdAt = now, updatedAt = now, remoteShopId = shopId, remoteProductId = "remote-b")
        )
        val currentCombo = Product(
            UUID.randomUUID(), "Combo de prueba", "COMBO-1", categoryId = categoryId, unitId = unitId,
            salePrice = 25000, createdAt = now, updatedAt = now, remoteShopId = shopId,
            remoteProductId = "remote-combo", remoteIsCombo = true
        )
        var saved: Triple<ProductInput, Long, Boolean>? = null

        compose.setContent {
            BSPOSTheme {
                ProductForm(
                    current = currentCombo,
                    currentQuantity = 0,
                    categories = categories,
                    units = units,
                    presentation = MiCatalogoBusinessPresentation(),
                    showCost = true,
                    showInventoryFields = true,
                    showProductCode = true,
                    onSave = { input, quantity, prepare -> saved = Triple(input, quantity, prepare) },
                    onDismiss = {},
                    shops = listOf(MiCatalogoShop(shopId, "Tienda QA", null)),
                    activeShopId = shopId,
                    comboComponentProducts = components
                )
            }
        }

        compose.onNodeWithText("Sin productos sincronizados de esta tienda para armar el combo. Agrega primero sus productos componentes.").assertDoesNotExist()
        compose.onNodeWithText("Agregar componente").performClick()
        compose.onNodeWithTag("combo-component-0").performClick()
        compose.onNodeWithText("Perfume componente A").performClick()
        compose.onNodeWithTag("product-save-button").performClick()

        compose.runOnIdle {
            val result = saved ?: error("El formulario no guardó el combo")
            assertEquals(true, result.first.isCombo)
            assertEquals(1, result.first.comboItems.size)
            assertEquals("remote-a", result.first.comboItems.single().productId)
            assertEquals(1, result.first.comboItems.single().quantity)
            assertEquals(false, result.third)
        }
    }
}
