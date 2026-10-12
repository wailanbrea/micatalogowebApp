package com.example.bspos.data.micatalogo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.InventoryImportRequestDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.bspos.BuildConfig
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.domain.model.*
import com.example.bspos.presentation.inventory.InventoryImportMappingDialog
import org.junit.Rule
import com.example.bspos.data.local.tenant.LiveQaFixture

/** Requires the isolated Laravel fixture server and adb reverse tcp:8893 tcp:8893. */
@RunWith(AndroidJUnit4::class)
class InventoryImportLiveIntegrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun anonymousRowEightWorkbookTravelsFromAndroidToRealLaravelAndConfirmsBySession() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val fixture = LiveQaFixture.optional("anonymousRowEightWorkbookTravelsFromAndroidToRealLaravelAndConfirmsBySession")
        val shop = fixture?.getString("shop_a") ?: arguments.getString("inventoryImportShop").orEmpty()
        org.junit.Assume.assumeTrue("Requires an isolated import fixture", !shop.isNullOrBlank())
        val context = InstrumentationRegistry.getInstrumentation().context
        val bytes = context.assets.open("inventory-import-row8.xlsx").use { it.readBytes() }
        val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${fixture?.getString("token") ?: arguments.getString("inventoryImportToken").orEmpty()}").header("Accept", "application/json").header("X-MiCatalogo-Version-Code", BuildConfig.VERSION_CODE.toString()).build())
        }.build()
        val api = Retrofit.Builder().baseUrl(BuildConfig.MICATALOGO_API_BASE_URL.also { check(java.net.URI(it).host in setOf("127.0.0.1", "localhost", "10.0.2.2")) }).client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType())).build().create(MiCatalogoApi::class.java)
        val part = MultipartBody.Part.createFormData("file", "inventario-anonimo.xlsx", bytes.toRequestBody("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaType()))
        val response = api.previewInventoryImport(shop!!, part, emptyMap())
        assertTrue("Preview HTTP ${response.code()}", response.isSuccessful)
        val preview = response.body()!!
        assertEquals(8, preview.headerRow)
        assertEquals("Existencias", preview.sheet!!.name)
        assertEquals(1, preview.sheet!!.index)
        assertEquals("0850050062035", preview.rows[0].barcode)
        assertEquals("812256024194", preview.rows[1].barcode)
        assertEquals("000045", preview.rows[0].productCode)
        assertEquals(2, preview.validRows)
        assertEquals("costo unitario", preview.mapping["cost_price"])
        assertEquals("precio unitario", preview.mapping["price"])
        assertTrue(preview.ignoredColumns.any { it.source == "costo inventario" })
        val quota = preview.quota
        val ui = MiCatalogoInventoryImportPreview(
            quota = MiCatalogoShopQuota(quota.plan, quota.planLabel, quota.productCount, quota.productLimit, quota.productsRemaining, quota.imageLimit, quota.canAddProducts),
            headers = preview.headers, originalHeaders = preview.originalHeaders, mapping = preview.mapping, fields = preview.fields,
            rows = emptyList(), validRows = preview.validRows, invalidRows = preview.invalidRows,
            sessionId = preview.sessionId, headerRow = preview.headerRow, sheetName = preview.sheet!!.name, sheetIndex = preview.sheet!!.index,
            mappingDetails = preview.mappingConfidence.mapValues { (_, d) -> ImportMappingDetail(d.source, d.header, d.confidence, d.reason, d.examples) }
        )
        compose.setContent { BSPOSTheme { InventoryImportMappingDialog(ui, false, { _ -> }, {}) } }
        compose.onNodeWithText("Hoja: Existencias · Encabezados: fila 8").assertIsDisplayed()
        compose.onNodeWithText("Código de barras: Código del producto").assertExists()
        compose.onNodeWithText("Costo: Costo unitario").assertExists()
        compose.onNodeWithText("Precio de venta: Precio unitario").assertExists()
        compose.onNodeWithText("Ver vista previa").assertIsEnabled()
        val confirmation = api.importInventory(shop, InventoryImportRequestDto(sessionId = preview.sessionId, duplicateStrategy = "update", createMissingCategories = true))
        assertTrue("Confirm HTTP ${confirmation.code()}", confirmation.isSuccessful)
        assertEquals(2, confirmation.body()!!.imported)
        val repeated = api.importInventory(shop, InventoryImportRequestDto(sessionId = preview.sessionId))
        assertTrue(repeated.isSuccessful)
        assertEquals(2, repeated.body()!!.imported)
    }
}
