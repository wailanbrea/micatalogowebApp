package com.example.bspos.presentation.decants

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.data.micatalogo.dto.*
import com.example.bspos.domain.model.Product
import androidx.compose.runtime.*
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecantsWorkspaceTest {
    @get:Rule val compose = createComposeRule()
    private val opening = DecantOpeningDto("opening", "2026-10-06", 100, 15, costCents = 100000, remainingCostCents = 15000)
    private val size = DecantSizeDto("size", "Perfume 5 ml", 5, priceCents = 30000, prepared = 5, capacity = 3, emptyVials = 2, vialCostCents = 5000)
    private val group = DecantFragranceDto("source", "Perfume Demo 100ml", volumeMl = 100, sealed = 10, tracked = true, sizes = listOf(size), openings = listOf(opening), sourceSaleUnit = "bottle")
    private val workspace = DecantWorkspaceDto(enabled = true, canManage = true, finance = true, groups = listOf(group), prepared = 5, preparedValueCents = 150000,
        vials = listOf(DecantVialDto("vial", "5ml", 5, empty = 2, nextCostCents = 5000, lots = listOf(DecantVialLotDto(1, "2026-10-06", 2, 2, 5000)))))

    @Test fun preparationLimitsQuantityByPhysicalEmptyVialsAndPreservesTheRemainingPerfume() {
        var result: List<String> = emptyList()
        compose.setContent { BSPOSTheme {
            DecantPrepareSheet(workspace, null, true, {}, {}) { source, bottle, presentation, quantity, price ->
                result = listOf(source.id, bottle?.id.orEmpty(), presentation.id, quantity.toString(), price)
            }
        } }
        compose.onNodeWithText("quedan 15 de 100 ml").performClick()
        compose.onNodeWithText("5 ml").performClick()
        compose.onNodeWithText("Máximo (2)").performScrollTo().performClick()
        compose.onNodeWithText("Máximo limitado por ml y envases. Sobrante: 5 ml.").assertExists()
        capture("style-decant-preparation")
        compose.onNodeWithText("Preparar 2 frasco(s)").assertIsEnabled().performClick()
        assertEquals(listOf("source", "opening", "size", "2", "300.00"), result)
    }

    @Test fun noVialsPreventsPreparationEvenWhenThereIsPerfume() {
        val emptyGroup = group.copy(sizes = listOf(size.copy(emptyVials = 0)))
        compose.setContent { BSPOSTheme { DecantPrepareSheet(workspace.copy(groups = listOf(emptyGroup)), null, true, {}, {}) { _, _, _, _, _ -> } } }
        compose.onNodeWithText("quedan 15 de 100 ml").performClick()
        compose.onNodeWithText("5 ml").performClick()
        compose.onNodeWithText("Preparar 1 frasco(s)").assertIsNotEnabled()
    }

    @Test fun advancedEmptyVialsActionKeepsItsDestinationAfterOutgoingSheetDismisses() {
        var sheet by mutableStateOf<String?>("advanced")
        var lateDismissFromAdvanced: (() -> Unit)? = null
        compose.setContent { BSPOSTheme {
            sheet?.let { destination -> DecantOperationsSheet(
                destination = destination,
                workspace = workspace,
                enabled = true,
                busy = false,
                error = null,
                message = null,
                onDismiss = { sheet = dismissDecantSheetIfCurrent(sheet, destination) },
                onOpen = { next ->
                    lateDismissFromAdvanced = { sheet = dismissDecantSheetIfCurrent(sheet, "advanced") }
                    sheet = next
                },
                onReceive = { _, _, _, _ -> },
                onActive = {}
            ) }
        } }

        compose.onNodeWithText("Frascos vacíos").performClick()
        compose.runOnIdle { assertEquals("vials", sheet) }
        compose.onNodeWithText("Frascos vacíos").assertExists()
        compose.runOnIdle { lateDismissFromAdvanced?.invoke() }
        compose.runOnIdle { assertEquals("vials", sheet) }
        compose.onNodeWithText("Frascos vacíos").assertExists()
    }

    @Test fun availableVialWithoutCatalogPresentationIsShownAsNewPreparationOption() {
        val groupWithoutPresentation = group.copy(sizes = emptyList())
        val workspaceWithoutPresentation = workspace.copy(
            groups = listOf(groupWithoutPresentation),
            vials = listOf(
                DecantVialDto("vial-5", "5 ml", 5, empty = 20, salePriceCents = 12500),
                DecantVialDto("vial-10", "10 ml", 10, empty = 50)
            )
        )
        compose.setContent { BSPOSTheme {
            DecantPrepareSheet(workspaceWithoutPresentation, null, true, {}, {}) { _, _, _, _, _ -> }
        } }
        compose.onNodeWithText("quedan 15 de 100 ml").performClick()
        compose.onNodeWithText("5 ml").assertExists()
        compose.onNodeWithText("20 envases disponibles · nueva presentación").assertExists()
        compose.onNodeWithText("10 ml").assertExists()
    }

    @Test fun cardsShowPreparedUnitsRatherThanSummingCompetingCapacities() {
        compose.setContent { BSPOSTheme { DecantsContent(workspace, null, null, false,
            onRefresh = {}, onAction = {}, onPrepare = {}, onDiscard = { _, _ -> },
            onUndo = {}, onMode = { _, _ -> }, onNewSize = {}) } }
        compose.onNodeWithText("Perfume Demo 100ml").performScrollTo().assertExists()
        compose.onNodeWithText("1 tamaño · 5 listos").assertExists()
        capture("style-decant-workspace")
    }

    @Test fun sourcePickerShowsOnlyCompleteBottlesFromInventory() {
        val mlSource = group.copy(id = "ml-source", name = "Fuente medida histórica", sourceSaleUnit = "ml", sealed = 4)
        compose.setContent { BSPOSTheme {
            DecantPrepareSheet(workspace.copy(groups = listOf(group, mlSource)), null, true, {}, {}) { _, _, _, _, _ -> }
        } }
        compose.onNode(hasText("Perfume Demo 100ml") and hasText("10 en inventario · 100 ml")).assertExists()
        compose.onNodeWithText("Fuente medida histórica").assertDoesNotExist()
    }

    @Test fun sourcePickerDoesNotHideSealedBottlesWhenThereIsAnUnreconciledRemainder() {
        val mixedSource = group.copy(untrackedOpenMl = 15)
        compose.setContent { BSPOSTheme {
            DecantPrepareSheet(workspace.copy(groups = listOf(mixedSource)), null, true, {}, {}) { _, _, _, _, _ -> }
        } }
        compose.onNode(hasText("Perfume Demo 100ml") and hasText("10 en inventario · 100 ml")).assertExists()
    }

    @Test fun unconfiguredTabIsBetweenReportAndOptionsAndOpensTheFullSearchableList() {
        val products = (1..12).map { index -> Product(UUID.randomUUID(), "Perfume pendiente $index", "PEND-$index",
            categoryId = UUID.randomUUID(), unitId = UUID.randomUUID(), salePrice = 10000,
            description = "Botella de prueba $index",
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH) }
        var selected: Product? = null
        compose.setContent { BSPOSTheme {
            var action by remember { mutableStateOf<String?>(null) }
            DecantsContent(workspace, null, null, false, onRefresh = {}, onAction = { action = it },
                onPrepare = {}, onDiscard = { _, _ -> }, onUndo = {}, onMode = { _, _ -> }, onNewSize = {},
                unconfiguredSources = products)
            if (action == "unconfigured") DecantUnconfiguredSourcesSheet(products, products.associate { it.id to 4L }, true,
                { action = null }, { selected = it; action = null })
        } }
        val tree = compose.onRoot(useUnmergedTree = true).printToString()
        check(tree.indexOf("Reporte") < tree.indexOf("Sin volumen (12)"))
        check(tree.indexOf("Sin volumen (12)") < tree.indexOf("Más opciones"))
        val reportBounds = compose.onNodeWithText("Reporte").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val volumeBounds = compose.onNodeWithText("Sin volumen (12)").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val moreBounds = compose.onNodeWithText("Más opciones").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(reportBounds.width, volumeBounds.width, 2f)
        assertEquals(volumeBounds.width, moreBounds.width, 2f)
        compose.onNodeWithText("Perfume pendiente 1").assertDoesNotExist()
        compose.onNodeWithText("Sin volumen (12)").performScrollTo().performClick()
        compose.onNodeWithText("Productos sin volumen").assertExists()
        compose.onAllNodesWithText("Existencia: 4 unidad(es)", substring = true).assertCountEquals(12)
        compose.onNodeWithText("Buscar producto").performTextReplacement("PEND-12")
        compose.onNodeWithText("Perfume pendiente 12").assertExists()
        compose.onNodeWithText("Botella de prueba 12").assertExists()
        compose.onNodeWithText("Perfume pendiente 1").assertDoesNotExist()
        capture("style-decant-unconfigured-products")
        compose.onNodeWithText("Configurar ml").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(products.last().id, selected?.id) }
        compose.onNodeWithText("Productos sin volumen").assertDoesNotExist()
    }

    @Test fun unconfiguredProductsSheetExplainsTheEmptyStateAndCanClose() {
        var closed = false
        compose.setContent { BSPOSTheme {
            DecantUnconfiguredSourcesSheet(emptyList(), emptyMap(), false, { closed = true }, {})
        } }
        compose.onNodeWithText("No hay productos pendientes de configurar.").assertExists()
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.runOnIdle { assertEquals(true, closed) }
    }

    @Test fun unconfiguredProductsRemainReadOnlyWithoutManagementPermission() {
        val product = Product(UUID.randomUUID(), "Perfume sin volumen", "PEND-1", categoryId = UUID.randomUUID(),
            unitId = UUID.randomUUID(), salePrice = 10000, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        var selected: Product? = null
        compose.setContent { BSPOSTheme {
            DecantUnconfiguredSourcesSheet(listOf(product), mapOf(product.id to 3L), false, {}, { selected = it })
        } }
        compose.onNodeWithText("Existencia: 3 unidad(es)", substring = true).assertExists()
        compose.onNodeWithText("Configurar ml").assertIsNotEnabled()
        compose.runOnIdle { assertNull(selected) }
    }

    @Test fun configuringBottleVolumeShowsStockAndReturnsMlWithoutClosingOnInvalidInput() {
        val product = Product(UUID.randomUUID(), "Perfume unidad", "SKU-1", description = "Eau de parfum",
            categoryId = UUID.randomUUID(), unitId = UUID.randomUUID(), salePrice = 1000, lastPurchaseCost = 25000,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        var configured: Int? = null
        compose.setContent { BSPOSTheme {
            ConfigureBottleDialog(product, 3, true, {}, { configured = it })
        } }
        compose.onNodeWithText("3 botella(s) · costo", substring = true).assertExists()
        compose.onNodeWithText("Guardar y usar en Decants").assertIsNotEnabled()
        compose.onNodeWithText("Contenido de la botella (ml)").performTextInput("100")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Guardar y usar en Decants").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(100, configured) }
    }

    @Test fun savingBottleVolumeClosesDialogAndRefreshesAuthoritativeDecantWorkspace() {
        val product = Product(UUID.randomUUID(), "Perfume unidad persistente", "SKU-PERSIST", categoryId = UUID.randomUUID(),
            unitId = UUID.randomUUID(), salePrice = 1000, lastPurchaseCost = 25000,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        var dialogVisible by mutableStateOf(true)
        var refreshRequested = false
        compose.setContent { BSPOSTheme {
            if (dialogVisible) ConfigureBottleDialog(product, 2, true, {}, { _ ->
                finishBottleConfiguration(
                    onDismiss = { dialogVisible = false },
                    onRefreshWorkspace = { refreshRequested = true }
                )
            })
        } }

        compose.onNodeWithText("Contenido de la botella (ml)").performTextInput("100")
        compose.onNodeWithText("Guardar y usar en Decants").performClick()
        compose.onNodeWithText("Configurar botella fuente").assertDoesNotExist()
        compose.runOnIdle { assertEquals(true, refreshRequested) }
    }

    @Test fun selectingUnconfiguredBottleOpensVolumeDialogAndCancelDoesNotSave() {
        val product = Product(UUID.randomUUID(), "Perfume unidad QA", "SKU-QA", description = "Botella de 100 ml",
            categoryId = UUID.randomUUID(), unitId = UUID.randomUUID(), salePrice = 1000, lastPurchaseCost = 25000,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        var showSources by mutableStateOf(true)
        var selected: Product? by mutableStateOf(null)
        var savedVolume: Int? = null
        compose.setContent { BSPOSTheme {
            if (showSources) DecantUnconfiguredSourcesSheet(listOf(product), mapOf(product.id to 3L), true,
                onDismiss = { showSources = false }, onConfigure = { selected = it; showSources = false })
            selected?.let { chosen -> ConfigureBottleDialog(chosen, 3, true,
                onDismiss = { selected = null }, onConfirm = { savedVolume = it }) }
        } }

        compose.onNodeWithText("Configurar ml").performScrollTo().performClick()
        compose.onNodeWithText("Configurar botella fuente").assertExists()
        compose.onNodeWithText("Perfume unidad QA").assertExists()
        compose.onNodeWithText("Productos sin volumen").assertDoesNotExist()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithText("Configurar botella fuente").assertDoesNotExist()
        compose.runOnIdle { assertNull(savedVolume) }
    }

    private fun capture(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
