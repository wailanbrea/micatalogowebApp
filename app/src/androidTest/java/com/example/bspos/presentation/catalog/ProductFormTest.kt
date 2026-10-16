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
import com.example.bspos.domain.model.MiCatalogoProductFormPresentation
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
        val shopId = "profile-shop"
        val bottleUnitId = UUID.nameUUIDFromBytes("micatalogo:unit:$shopId:bottle".toByteArray())
        val formPresentation = MiCatalogoProductFormPresentation(
            schemaVersion = 1,
            defaultSaleUnit = "bottle",
            availableSaleUnits = listOf("unit", "bottle", "ml"),
            saleUnitLabels = mapOf("unit" to "Producto completo", "bottle" to "Botella completa", "ml" to "Por mililitro (ml)"),
            defaultInventoryUnitKey = "bottle",
            saleUnitToInventoryUnit = mapOf("unit" to "unit", "bottle" to "bottle", "ml" to "ml"),
            inventoryUnitLabels = mapOf("unit" to "Unidades", "bottle" to "Botellas", "ml" to "Mililitros (ml)"),
            inventoryUnitUuids = mapOf(
                "unit" to UUID.nameUUIDFromBytes("micatalogo:unit:$shopId:unit".toByteArray()).toString(),
                "bottle" to bottleUnitId.toString(),
                "ml" to UUID.nameUUIDFromBytes("micatalogo:unit:$shopId:ml".toByteArray()).toString()
            ),
            supportedFields = setOf("name", "category", "price", "stock", "volume_ml")
        )
        var saved: Triple<ProductInput, Long, Boolean>? = null

        compose.setContent {
            BSPOSTheme {
                ProductForm(
                    current = null,
                    currentQuantity = 0,
                    categories = categories,
                    units = units,
                    presentation = MiCatalogoBusinessPresentation(archetype = "fragrance", productForm = formPresentation),
                    showCost = true,
                    showInventoryFields = true,
                    showProductCode = true,
                    onSave = { input, quantity, prepare -> saved = Triple(input, quantity, prepare) },
                    onDismiss = {},
                    shops = listOf(MiCatalogoShop(shopId, "Perfumería QA", null)),
                    activeShopId = shopId,
                    enableDecantPrompt = true
                )
            }
        }

        compose.onNodeWithTag("product-name-field").assertExists()
        compose.onNodeWithText("Nombre").assertExists()
        compose.onNodeWithTag("product-category-selector").assertExists()
        compose.onNodeWithTag("product-auto-inventory-unit").assertExists()
        compose.onNodeWithText("Botellas").assertExists()
        compose.onNodeWithText("Código interno").assertDoesNotExist()

        compose.onNodeWithTag("sale-unit-bottle").performClick()
        compose.onNodeWithTag("product-prepare-decants").assertIsEnabled()
        compose.onNodeWithTag("product-prepare-decants").performScrollTo().performClick()
        compose.onNodeWithText("Preparación seleccionada").assertExists()
        compose.onNodeWithTag("product-name-field").performTextInput("Perfume de prueba")
        compose.onNodeWithTag("product-volume-ml").performTextInput("100")
        compose.onNodeWithTag("product-purchase-price").performTextInput("1000")
        compose.onNodeWithTag("product-sale-price").performTextInput("2500")
        compose.onNodeWithTag("product-initial-stock").performTextClearance()
        compose.onNodeWithTag("product-initial-stock").performTextInput("1")
        compose.onNodeWithTag("product-initial-stock").performImeAction()
        compose.onNodeWithText("Preparar decants al guardar").assertExists()
        compose.onNodeWithTag("product-save-button").performClick()

        compose.runOnIdle {
            val result = saved ?: error("El formulario no guardó el producto")
            assertEquals("bottle", result.first.remoteSaleUnit)
            assertEquals(100, result.first.remoteVolumeMl)
            assertEquals(bottleUnitId, result.first.unitId)
            assertEquals("Botella", result.first.inventoryUnitName)
            assertEquals("Perfume de prueba", result.first.name)
            assertEquals(true, result.third)
        }
    }

    @Test fun applianceProfileDoesNotOfferFragranceModesOrVolume() {
        val now = Instant.now()
        val categories = listOf(Category(UUID.randomUUID(), "Neveras", createdAt = now, updatedAt = now))
        val unitId = UUID.randomUUID()
        val shopId = "appliance-qa"
        compose.setContent {
            BSPOSTheme {
                ProductForm(
                    current = null,
                    currentQuantity = 0,
                    categories = categories,
                    units = listOf(UnitOfMeasure(unitId, "Unidad", "UND", createdAt = now, updatedAt = now)),
                    presentation = MiCatalogoBusinessPresentation(
                        archetype = "appliances",
                        terminology = mapOf("new_product" to "Nuevo electrodoméstico"),
                        productForm = MiCatalogoProductFormPresentation(
                            schemaVersion = 1,
                            defaultSaleUnit = "unit",
                            availableSaleUnits = listOf("unit"),
                            defaultInventoryUnitKey = "unit",
                            saleUnitToInventoryUnit = mapOf("unit" to "unit"),
                            inventoryUnitUuids = mapOf("unit" to UUID.nameUUIDFromBytes("micatalogo:unit:$shopId:unit".toByteArray()).toString())
                        )
                    ),
                    showCost = true,
                    showInventoryFields = true,
                    showProductCode = true,
                    onSave = { _, _, _ -> },
                    onDismiss = {},
                    shops = listOf(MiCatalogoShop(shopId, "Electrodomésticos QA", null)),
                    activeShopId = shopId
                )
            }
        }

        compose.onNodeWithText("Nuevo electrodoméstico").assertExists()
        compose.onNodeWithTag("sale-unit-selector").assertDoesNotExist()
        compose.onNodeWithText("Cómo se vende").assertDoesNotExist()
        compose.onNodeWithTag("sale-unit-bottle").assertDoesNotExist()
        compose.onNodeWithTag("sale-unit-service").assertDoesNotExist()
        compose.onNodeWithTag("product-volume-ml").assertDoesNotExist()
        compose.onNodeWithTag("product-auto-inventory-unit").assertExists()
    }

    @Test fun emptyCategorySnapshotDoesNotCrashOrSaveProduct() {
        val now = Instant.now()
        val shopId = "empty-taxonomy-qa"
        var saved = false
        compose.setContent {
            BSPOSTheme {
                ProductForm(
                    current = null,
                    currentQuantity = 0,
                    categories = emptyList(),
                    units = emptyList(),
                    presentation = MiCatalogoBusinessPresentation(archetype = "appliances"),
                    showCost = true,
                    showInventoryFields = true,
                    showProductCode = true,
                    onSave = { _, _, _ -> saved = true },
                    onDismiss = {},
                    shops = listOf(MiCatalogoShop(shopId, "Tienda sin categorías", null)),
                    activeShopId = shopId
                )
            }
        }

        compose.onNodeWithTag("product-category-empty-state").assertExists()
        compose.onNodeWithTag("product-category-selector").assertIsNotEnabled()
        compose.onNodeWithTag("product-save-button").performClick()
        compose.runOnIdle { assertEquals(false, saved) }
    }

    @Test fun serviceProductKeepsCategorySelectorVisibleWithoutPhysicalInventoryControls() {
        val now = Instant.now()
        val category = Category(UUID.randomUUID(), "Servicios", createdAt = now, updatedAt = now)
        val shopId = "service-taxonomy-qa"
        compose.setContent {
            BSPOSTheme {
                ProductForm(
                    current = null,
                    currentQuantity = 0,
                    categories = listOf(category),
                    units = emptyList(),
                    presentation = MiCatalogoBusinessPresentation(archetype = "services"),
                    showCost = true,
                    showInventoryFields = true,
                    showProductCode = true,
                    onSave = { _, _, _ -> },
                    onDismiss = {},
                    shops = listOf(MiCatalogoShop(shopId, "Barbería QA", null)),
                    activeShopId = shopId,
                    serviceMode = true,
                    initialSaleUnit = "service"
                )
            }
        }

        compose.onNodeWithTag("product-category-selector").assertExists()
        compose.onNodeWithText("Servicios").assertExists()
        compose.onNodeWithTag("product-initial-stock").assertDoesNotExist()
        compose.onNodeWithTag("product-auto-inventory-unit").assertDoesNotExist()
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
