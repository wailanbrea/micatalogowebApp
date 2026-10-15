package com.example.bspos.data.micatalogo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.example.bspos.domain.model.MiCatalogoShop
import com.example.bspos.domain.model.canManageCatalogType
import com.example.bspos.domain.model.canManageCatalogPhotos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CatalogMutationAccessTest {
    @Test fun legacySellerDefaultsNeverBecomePhysicalMutationPermission() {
        val shop = MiCatalogoShop("a", "A", null, menuPermissions = listOf("sales", "products", "printers"))
        assertFalse(shop.canManageCatalogType("unit"))
        assertFalse(shop.canManageCatalogType("service"))
        assertTrue(shop.copy(canManageSellers = true).canManageCatalogType("unit"))
    }

    @Test fun explicitServiceActionDoesNotAuthorizePhysicalOrDisabledServiceActions() {
        val shop = MiCatalogoShop("a", "A", null, menuPermissions = listOf("services"),
            catalogPermissions = mapOf("manage_services" to true, "manage_products" to true))
        assertTrue(shop.canManageCatalogType("service"))
        assertFalse(shop.canManageCatalogType("unit"))
        assertFalse(shop.copy(enabledMenuKeys = emptyList()).canManageCatalogType("service"))
        assertFalse(shop.copy(capabilities = mapOf("services" to "disabled")).canManageCatalogType("service"))
    }

    @Test fun actionMetadataCanRestrictEvenAKnownOwner() {
        val shop = MiCatalogoShop("a", "A", null, menuPermissions = listOf("products"), canManageSellers = true,
            catalogPermissions = mapOf("manage_products" to false))
        assertFalse(shop.canManageCatalogType("unit"))
    }

    @Test fun unknownServiceCostIsOmittedButExplicitKnownValuesAreNotSilentlyDiscarded() {
        assertFalse(includeInitialCost("service", 0, false))
        assertTrue(includeInitialCost("service", 0, true))
        assertTrue(includeInitialCost("service", 100, false))
        assertTrue(includeInitialCost("bottle", 100, false))
    }

    @Test fun costPolicyReadsOnlyTheMatchingShopSnapshot() = runTest {
        val snapshot = """[{"id":"a","catalog_permissions":{"read_costs":false}},
            {"id":"b","catalog_permissions":{"read_costs":true}}]"""
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(preferencesOf(SHOP_SNAPSHOT_CACHE to snapshot))
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Read-only policy must not write")
        }
        val policy = CachedCatalogPolicy(store, Json { ignoreUnknownKeys = true })
        assertFalse(policy.canReadCosts("a"))
        assertTrue(policy.canReadCosts("b"))
        assertFalse(policy.canReadCosts("unknown"))
    }

    @Test fun photoDelegationDoesNotGrantCatalogCrudAndRespectsEffectiveRestrictions() {
        val shop = MiCatalogoShop("a", "A", null, menuPermissions = listOf("photos"),
            catalogPermissions = mapOf("manage_photos" to true))
        assertTrue(shop.canManageCatalogPhotos())
        assertFalse(shop.canManageCatalogType("unit"))
        assertFalse(shop.canManageCatalogType("service"))
        assertFalse(shop.copy(enabledMenuKeys = emptyList()).canManageCatalogPhotos())
        assertFalse(shop.copy(menuPermissions = listOf("products")).canManageCatalogPhotos())
        assertFalse(shop.copy(catalogPermissions = mapOf("manage_photos" to false)).canManageCatalogPhotos())
    }

    @Test fun photoPolicyRejectsRevokedUnknownOrMalformedCachedShopSnapshots() = runTest {
        var snapshot = """[{"id":"a","menu_permissions":["photos"],"catalog_permissions":{"manage_photos":true}},
            {"id":"b","menu_permissions":["products"],"catalog_permissions":{"manage_photos":true}}]"""
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> get() = flowOf(preferencesOf(SHOP_SNAPSHOT_CACHE to snapshot))
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Read-only")
        }
        val policy = CachedCatalogPolicy(store, Json { ignoreUnknownKeys = true })
        assertTrue(policy.canManagePhotos("a"))
        assertFalse(policy.canManagePhotos("b"))
        assertFalse(policy.canManagePhotos("unknown"))
        snapshot = "[]"
        assertFalse(policy.canManagePhotos("a"))
        snapshot = "malformed"
        assertFalse(policy.canManagePhotos("a"))
    }
}
