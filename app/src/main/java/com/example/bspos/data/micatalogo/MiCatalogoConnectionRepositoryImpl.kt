package com.example.bspos.data.micatalogo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bspos.core.network.MiCatalogoBaseUrl
import com.example.bspos.data.micatalogo.api.MiCatalogoApi
import com.example.bspos.data.micatalogo.dto.LoginRequestDto
import com.example.bspos.data.micatalogo.dto.InventoryImportAttributeDto
import com.example.bspos.data.micatalogo.dto.InventoryImportRequestDto
import com.example.bspos.data.micatalogo.dto.InventoryImportRowDto
import com.example.bspos.data.micatalogo.dto.MenuPermissionsUpdateDto
import com.example.bspos.data.micatalogo.dto.MeDto
import com.example.bspos.data.micatalogo.dto.ProfileUpdateDto
import com.example.bspos.data.micatalogo.dto.SellerCreateRequestDto
import com.example.bspos.domain.model.MiCatalogoConnectionState
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.MiCatalogoShopQuota
import com.example.bspos.domain.model.MiCatalogoSeller
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import com.example.bspos.domain.repository.MiCatalogoConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import dagger.Lazy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.example.bspos.data.micatalogo.dto.ShopDto
import com.example.bspos.data.micatalogo.dto.ShopQuotaDto
import com.example.bspos.data.micatalogo.dto.AdminShopDto
import com.example.bspos.data.micatalogo.dto.AdminShopUpdateDto
import com.example.bspos.domain.model.MiCatalogoInventoryImportPreview
import com.example.bspos.domain.model.MiCatalogoInventoryImportResult
import com.example.bspos.domain.model.MiCatalogoManagedShop
import com.example.bspos.domain.model.MiCatalogoInventoryImportRow
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MiCatalogoConnectionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val api: Lazy<MiCatalogoApi>,
    @param:MiCatalogoBaseUrl private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : MiCatalogoConnectionRepository {
    private val temporaryAccessToken = MutableStateFlow<String?>(null)

    override fun observeConnection(): Flow<MiCatalogoConnectionState> = combine(dataStore.data, temporaryAccessToken) { values, temporaryToken ->
        val savedToken = values[ACCESS_TOKEN]?.takeIf { it.isNotBlank() }
        MiCatalogoConnectionState(
            baseUrl = baseUrl,
            hasAccessToken = temporaryToken != null || savedToken != null,
            isRemembered = savedToken != null && (values[REMEMBER_SESSION] == true || !values[REMEMBERED_EMAIL].isNullOrBlank()),
            rememberedEmail = values[REMEMBERED_EMAIL].orEmpty().ifBlank {
                if (values[REMEMBER_SESSION] == true) values[ACCOUNT_EMAIL].orEmpty() else ""
            },
            accountEmail = values[ACCOUNT_EMAIL].orEmpty().ifBlank { values[REMEMBERED_EMAIL].orEmpty() },
            accountName = values[ACCOUNT_NAME].orEmpty(),
            role = values[ACCOUNT_ROLE].orEmpty().ifBlank { "seller" },
            activeShopId = values[ACTIVE_SHOP_ID]?.takeIf { it.isNotBlank() }
        )
    }.distinctUntilChanged()

    override suspend fun accessToken(): String? = temporaryAccessToken.value
        ?: dataStore.data.first()[ACCESS_TOKEN]?.takeIf { it.isNotBlank() }

    override suspend fun login(email: String, password: String, remember: Boolean): MiCatalogoResult<Unit> {
        if (email.isBlank() || password.isBlank()) return MiCatalogoResult.Failure("Ingresa correo y contrasena.")

        return runCatching {
            val response = api.get().login(LoginRequestDto(email.trim(), password))
            if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo iniciar sesion."))
            val body = response.body() ?: error("MiCatalogo no devolvio una respuesta de acceso.")
            val token = body.accessToken.orEmpty().ifBlank { body.token.orEmpty() }
            check(token.isNotBlank()) { "MiCatalogo no devolvio un token de acceso." }
            saveAccessToken(token, email, remember, body.user)
        }.fold(
            onSuccess = { MiCatalogoResult.Success(Unit) },
            onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudo iniciar sesion.") }
        )
    }

    override suspend fun refreshAccount(): MiCatalogoResult<Unit> = runCatching {
        val response = api.get().me()
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo validar la cuenta."))
        saveAccount(response.body() ?: error("MiCatalogo no devolvio los datos de la cuenta."))
    }.fold(
        onSuccess = { MiCatalogoResult.Success(Unit) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudo validar la cuenta.") }
    )

    override suspend fun updateProfile(name: String, email: String): MiCatalogoResult<Unit> = runCatching {
        val response = api.get().updateMe(ProfileUpdateDto(name.trim(), email.trim()))
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo actualizar el perfil."))
        saveAccount(response.body() ?: error("MiCatalogo no devolvio el perfil actualizado."))
    }.fold(
        onSuccess = { MiCatalogoResult.Success(Unit) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudo actualizar el perfil.") }
    )

    override suspend fun updateSellerMenus(shopId: String, sellerId: String, permissions: List<String>): MiCatalogoResult<Unit> = runCatching {
        val response = api.get().updateSellerMenus(shopId, sellerId, MenuPermissionsUpdateDto(permissions))
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudieron guardar los menus."))
    }.fold(
        onSuccess = { MiCatalogoResult.Success(Unit) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudieron guardar los menus.") }
    )

    override suspend fun shops(): MiCatalogoResult<List<MiCatalogoShop>> = runCatching {
        val remoteShops = try {
            val response = api.get().shops()
            if (!response.isSuccessful) error(response.apiErrorMessage("No se pudieron cargar las tiendas."))
            response.body().orEmpty().also { shops -> dataStore.edit { it[SHOP_CACHE] = json.encodeToString(shops) } }
        } catch (error: IOException) {
            val cached = dataStore.data.first()[SHOP_CACHE] ?: throw error
            json.decodeFromString<List<ShopDto>>(cached)
        }
        remoteShops.mapNotNull { shop ->
            shop.id?.takeIf { it.isNotBlank() }?.let { id ->
                MiCatalogoShop(
                    id = id,
                    name = shop.name?.ifBlank { id } ?: id,
                    slug = shop.slug,
                    businessType = shop.businessType,
                    businessTypeLabel = shop.businessTypeLabel,
                    capabilities = shop.capabilities,
                    productFields = shop.productFields,
                    presentation = MiCatalogoBusinessPresentation(
                        archetype = shop.presentation.archetype,
                        terminology = shop.presentation.terminology,
                        dashboardWidgets = shop.presentation.dashboard.widgets,
                        dashboardQuickActions = shop.presentation.dashboard.quickActions,
                        dashboardTitle = shop.presentation.dashboard.title,
                        posSearchPlaceholder = shop.presentation.pos.searchPlaceholder,
                        posShowWholesale = shop.presentation.pos.showWholesale,
                        posShowCredit = shop.presentation.pos.showCredit,
                        posShowInventory = shop.presentation.pos.showInventory,
                        catalogSearchPlaceholder = shop.presentation.catalog.searchPlaceholder,
                        catalogEmptyMessage = shop.presentation.catalog.emptyMessage,
                        catalogShowStock = shop.presentation.catalog.showStock,
                        inventoryTitle = shop.presentation.inventory.title,
                        inventoryEnabled = shop.presentation.inventory.enabled,
                        customersShowCredit = shop.presentation.customers.showCredit
                    ),
                    menuPermissions = shop.menuPermissions,
                    canManageSellers = shop.canManageSellers,
                    sellers = shop.sellers.map { seller ->
                        MiCatalogoSeller(
                            id = seller.id,
                            userId = seller.userId,
                            name = seller.name,
                            email = seller.email,
                            isActive = seller.isActive,
                            menuPermissions = seller.menuPermissions
                        )
                    },
                    quota = shop.quota?.let(::toShopQuota)
                )
            }
        }
    }.fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudieron cargar las tiendas.") }
    )

    override suspend fun activeShopId(): String? = dataStore.data.first()[ACTIVE_SHOP_ID]?.takeIf { it.isNotBlank() }

    override suspend fun selectShop(shopId: String): MiCatalogoResult<Unit> = runCatching {
        require(shopId.isNotBlank()) { "La tienda seleccionada no es válida." }
        val available = shops().let { result ->
            when (result) {
                is MiCatalogoResult.Success -> result.value.any { it.id == shopId }
                is MiCatalogoResult.Failure -> throw IllegalStateException(result.message)
            }
        }
        check(available) { "La tienda seleccionada no pertenece a esta cuenta." }
        dataStore.edit { it[ACTIVE_SHOP_ID] = shopId }
    }.fold(
        onSuccess = { MiCatalogoResult.Success(Unit) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudo seleccionar la tienda.") }
    )

    override suspend fun managedShops(): MiCatalogoResult<List<MiCatalogoManagedShop>> = runCatching {
        val response = api.get().adminShops()
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudieron cargar todas las tiendas."))
        response.body().orEmpty().map(::toManagedShop)
    }.fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudieron cargar todas las tiendas.") }
    )

    override suspend fun updateManagedShop(shop: MiCatalogoManagedShop): MiCatalogoResult<MiCatalogoManagedShop> = runCatching {
        val response = api.get().updateAdminShop(
            shop.id,
            AdminShopUpdateDto(shop.name.trim(), shop.whatsappCountryCode.trim(), shop.whatsappNumber.trim(), shop.status)
        )
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo actualizar la tienda."))
        toManagedShop(response.body() ?: error("MiCatalogo no devolvio la tienda actualizada."))
    }.fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudo actualizar la tienda.") }
    )

    override suspend fun previewInventoryImport(
        shopId: String,
        fileName: String,
        mimeType: String?,
        bytes: ByteArray,
        mapping: Map<String, String>
    ): MiCatalogoResult<MiCatalogoInventoryImportPreview> = runCatching {
        require(bytes.size <= 10 * 1024 * 1024) { "El archivo no puede superar 10 MB." }
        val body = bytes.toRequestBody(mimeType?.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        val mappingParts = mapping.mapValues { (_, value) ->
            value.toRequestBody("text/plain".toMediaTypeOrNull())
        }.mapKeys { (key, _) -> "mapping[$key]" }
        val response = api.get().previewInventoryImport(shopId, part, mappingParts)
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo leer el inventario."))
        val preview = response.body() ?: error("MiCatalogo devolvió una vista previa vacía.")
        MiCatalogoInventoryImportPreview(
            quota = toShopQuota(preview.quota),
            headers = preview.headers,
            mapping = preview.mapping,
            fields = preview.fields,
            rows = preview.rows.map { row ->
                MiCatalogoInventoryImportRow(
                    line = row.line,
                    name = row.name,
                    productCode = row.productCode,
                    barcode = row.barcode,
                    brand = row.brand,
                    category = row.category,
                    description = row.description,
                    notes = row.notes,
                    price = row.price,
                    costPrice = row.costPrice,
                    stock = row.stock,
                    attributes = row.attributes.map { attribute ->
                        com.example.bspos.domain.model.MiCatalogoInventoryImportAttribute(attribute.name, attribute.value)
                    },
                    errors = row.errors,
                    valid = row.valid
                )
            },
            validRows = preview.validRows,
            invalidRows = preview.invalidRows
        )
    }.fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudo leer el inventario.") }
    )

    override suspend fun importInventory(
        shopId: String,
        rows: List<MiCatalogoInventoryImportRow>
    ): MiCatalogoResult<MiCatalogoInventoryImportResult> = runCatching {
        val response = api.get().importInventory(
            shopId,
            InventoryImportRequestDto(rows.map { row ->
                InventoryImportRowDto(
                    line = row.line,
                    name = row.name,
                    productCode = row.productCode,
                    barcode = row.barcode,
                    brand = row.brand,
                    category = row.category,
                    description = row.description,
                    notes = row.notes,
                    price = row.price,
                    costPrice = row.costPrice,
                    stock = row.stock,
                    attributes = row.attributes.map { attribute -> InventoryImportAttributeDto(attribute.name, attribute.value) },
                    errors = row.errors,
                    valid = row.valid
                )
            })
        )
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo importar el inventario."))
        val result = response.body() ?: error("MiCatalogo no devolvió el resultado de la importación.")
        MiCatalogoInventoryImportResult(result.message, result.imported, result.quota?.let(::toShopQuota))
    }.fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudo importar el inventario.") }
    )

    private suspend fun saveAccessToken(accessToken: String, email: String, remember: Boolean, user: MeDto?) {
        check(accessToken.isNotBlank()) { "MiCatalogo access token cannot be blank" }
        temporaryAccessToken.value = if (remember) null else accessToken
        val normalizedEmail = user?.email?.takeIf(String::isNotBlank) ?: email.trim()
        dataStore.edit {
            if (!it[ACCOUNT_EMAIL].orEmpty().equals(normalizedEmail, ignoreCase = true)) it.remove(SHOP_CACHE)
            it[ACCOUNT_EMAIL] = normalizedEmail
            user?.name?.takeIf(String::isNotBlank)?.let { name -> it[ACCOUNT_NAME] = name }
            it[ACCOUNT_ROLE] = user?.role?.takeIf(String::isNotBlank) ?: "seller"
            if (remember) {
                it[ACCESS_TOKEN] = accessToken
                it[REMEMBER_SESSION] = true
                it[REMEMBERED_EMAIL] = normalizedEmail
            } else {
                it.remove(ACCESS_TOKEN)
                it.remove(REMEMBER_SESSION)
                it.remove(REMEMBERED_EMAIL)
            }
        }
    }

    override suspend fun createSeller(shopId: String, email: String, commissionType: String, commissionValue: String): MiCatalogoResult<String> = runCatching {
        val response = api.get().createSeller(
            shopId,
            SellerCreateRequestDto(email.trim(), commissionType, commissionValue.trim())
        )
        if (!response.isSuccessful) error(response.apiErrorMessage("No se pudo crear el vendedor."))
        response.body()?.message?.ifBlank { "Vendedor creado." } ?: "Vendedor creado."
    }.fold(
        onSuccess = { MiCatalogoResult.Success(it) },
        onFailure = { MiCatalogoResult.Failure(it.message ?: "No se pudo crear el vendedor.") }
    )

    private suspend fun saveAccount(user: MeDto) {
        dataStore.edit {
            user.email?.takeIf(String::isNotBlank)?.let { email -> it[ACCOUNT_EMAIL] = email }
            user.name?.takeIf(String::isNotBlank)?.let { name -> it[ACCOUNT_NAME] = name }
            it[ACCOUNT_ROLE] = user.role?.takeIf(String::isNotBlank) ?: "seller"
            user.email?.takeIf(String::isNotBlank)?.let { email ->
                if (it[REMEMBER_SESSION] == true) it[REMEMBERED_EMAIL] = email
            }
        }
    }

    private fun toShopQuota(quota: ShopQuotaDto): MiCatalogoShopQuota = MiCatalogoShopQuota(
        plan = quota.plan,
        planLabel = quota.planLabel,
        productCount = quota.productCount,
        productLimit = quota.productLimit,
        productsRemaining = quota.productsRemaining,
        imageLimit = quota.imageLimit,
        canAddProducts = quota.canAddProducts,
        userCount = quota.userCount,
        userLimit = quota.userLimit,
        usersRemaining = quota.usersRemaining,
        sellerCount = quota.sellerCount,
        sellerLimit = quota.sellerLimit,
        sellersRemaining = quota.sellersRemaining,
        canAddUsers = quota.canAddUsers,
        canAddSellers = quota.canAddSellers,
        additionalSeatPriceUsd = quota.additionalSeatPriceUsd,
        features = quota.features
    )

    private fun toManagedShop(shop: AdminShopDto) = MiCatalogoManagedShop(
        id = shop.id,
        name = shop.name,
        slug = shop.slug,
        ownerName = shop.ownerName,
        ownerEmail = shop.ownerEmail,
        whatsappCountryCode = shop.whatsappCountryCode,
        whatsappNumber = shop.whatsappNumber,
        status = shop.status,
        productCount = shop.productCount
    )

    override suspend fun clearConnection() {
        temporaryAccessToken.value = null
        dataStore.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(REMEMBER_SESSION)
            it.remove(REMEMBERED_EMAIL)
            it.remove(ACCOUNT_EMAIL)
            it.remove(ACCOUNT_NAME)
            it.remove(ACCOUNT_ROLE)
            it.remove(ACTIVE_SHOP_ID)
            it.remove(SHOP_CACHE)
        }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("micatalogo_access_token")
        val REMEMBER_SESSION = androidx.datastore.preferences.core.booleanPreferencesKey("micatalogo_remember_session")
        val REMEMBERED_EMAIL = stringPreferencesKey("micatalogo_remembered_email")
        val ACCOUNT_EMAIL = stringPreferencesKey("micatalogo_account_email")
        val ACCOUNT_NAME = stringPreferencesKey("micatalogo_account_name")
        val ACCOUNT_ROLE = stringPreferencesKey("micatalogo_account_role")
        val ACTIVE_SHOP_ID = stringPreferencesKey("micatalogo_active_shop_id")
        val SHOP_CACHE = stringPreferencesKey("micatalogo_shop_cache")
    }
}
