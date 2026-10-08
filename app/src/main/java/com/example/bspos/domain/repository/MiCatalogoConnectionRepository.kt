package com.example.bspos.domain.repository

import com.example.bspos.domain.model.MiCatalogoConnectionState
import com.example.bspos.domain.model.MiCatalogoResult
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.MiCatalogoManagedShop
import com.example.bspos.domain.model.MiCatalogoInventoryImportPreview
import com.example.bspos.domain.model.MiCatalogoInventoryImportResult
import com.example.bspos.domain.model.MiCatalogoInventoryImportRow
import kotlinx.coroutines.flow.Flow

interface MiCatalogoConnectionRepository {
    fun observeConnection(): Flow<MiCatalogoConnectionState>
    suspend fun accessToken(): String?
    suspend fun login(email: String, password: String, remember: Boolean): MiCatalogoResult<Unit>
    suspend fun refreshAccount(): MiCatalogoResult<Unit>
    suspend fun updateProfile(name: String, email: String): MiCatalogoResult<Unit>
    suspend fun updateSellerMenus(shopId: String, sellerId: String, permissions: List<String>): MiCatalogoResult<Unit>
    suspend fun createSeller(shopId: String, email: String, commissionType: String, commissionValue: String, permissions: List<String>): MiCatalogoResult<String>
    suspend fun shops(): MiCatalogoResult<List<MiCatalogoShop>>
    suspend fun activeShopId(): String?
    suspend fun selectShop(shopId: String): MiCatalogoResult<Unit>
    suspend fun managedShops(): MiCatalogoResult<List<MiCatalogoManagedShop>>
    suspend fun updateManagedShop(shop: MiCatalogoManagedShop): MiCatalogoResult<MiCatalogoManagedShop>
    suspend fun previewInventoryImport(shopId: String, fileName: String, mimeType: String?, bytes: ByteArray, mapping: Map<String, String> = emptyMap()): MiCatalogoResult<MiCatalogoInventoryImportPreview>
    suspend fun importInventory(
        shopId: String,
        rows: List<MiCatalogoInventoryImportRow>,
        sessionId: String? = null,
    ): MiCatalogoResult<MiCatalogoInventoryImportResult>
    suspend fun logout() {
        clearConnection()
    }
    suspend fun clearConnection()
}
