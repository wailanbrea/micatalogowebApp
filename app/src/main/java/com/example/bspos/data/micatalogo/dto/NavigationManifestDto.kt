package com.example.bspos.data.micatalogo.dto

import com.example.bspos.domain.model.MiCatalogoNavigation
import com.example.bspos.domain.model.MiCatalogoNavigationItem
import com.example.bspos.domain.model.MiCatalogoNavigationSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
private data class NavigationManifestDto(
    @SerialName("schema_version") val schemaVersion: Int = 0,
    val sections: List<NavigationSectionDto> = emptyList(),
    val secondary: List<NavigationItemDto> = emptyList(),
    @SerialName("contextual_sections") val contextualSections: List<NavigationSectionDto> = emptyList()
)

@Serializable
private data class NavigationSectionDto(
    val id: String = "",
    val label: String = "",
    val items: List<NavigationItemDto> = emptyList(),
    @SerialName("requires_local_setting") val requiresLocalSetting: String? = null
)

@Serializable
private data class NavigationItemDto(
    val id: String = "",
    val key: String = "",
    val label: String = "",
    val destination: String = ""
)

// Exact destinations and keys, not arbitrary server routes, deep links or URLs.
private val destinationKeys = mapOf(
    "dashboard" to setOf(""), "pos" to setOf("sales"), "sales_history" to setOf("sales"),
    "quotes" to setOf("quotes"), "returns" to setOf("returns"), "cash" to setOf("cash"),
    "day_close" to setOf("day_close"), "orders" to setOf("orders"), "encargos" to setOf("encargos"),
    "shipments" to setOf("shipments"), "catalog" to setOf("products"), "services" to setOf("services"),
    "categories" to setOf("products"), "photos" to setOf("photos"), "attributes" to setOf("attributes"),
    "price_health" to setOf("price_health"), "pricing" to setOf("pricing"), "decants" to setOf("decants"),
    "inventory" to setOf("inventory"), "inventory_adjustments" to setOf("inventory_adjustments"),
    "import" to setOf("import"), "suppliers" to setOf("suppliers"), "purchase_invoices" to setOf("purchase_invoices"),
    "loads" to setOf("loads"), "containers" to setOf("containers"), "customers" to setOf("customers"),
    "credit_ledger" to setOf("credit", "collections"), "collections" to setOf("collections"),
    "finance" to setOf("finance"), "expenses" to setOf("expenses"), "partners" to setOf("partners"),
    "reports" to setOf("reports"), "storefront" to setOf("storefront"), "public_catalog" to setOf("public_catalog"),
    "metrics" to setOf("metrics"), "shop_settings" to setOf("shop_settings"),
    "shop_settings?section=menus" to setOf("shop_settings"), "team" to setOf("sellers"),
    "commissions" to setOf("commissions"), "authorizations" to setOf("authorizations"),
    "accountant" to setOf("accountant"), "settings" to setOf("settings"), "printers" to setOf("printers"),
    "profile" to setOf("account"), "help" to setOf("help"), "support" to setOf("support"),
    "updates" to setOf("updates"), "practice" to setOf("practice"), "routes" to setOf("routes"),
    "route_loads" to setOf("routes")
)

private val sectionLabels = linkedMapOf(
    "home" to "Inicio", "sales" to "Ventas", "orders" to "Pedidos", "catalog" to "Catálogo",
    "inventory" to "Inventario", "purchases" to "Compras", "customers" to "Clientes y cobros",
    "finance" to "Finanzas", "reports" to "Reportes", "storefront" to "Mi tienda", "settings" to "Configuración"
)

/** An absent/unknown/malformed manifest falls back without making the shop cache unreadable. */
fun decodeNavigationManifest(element: JsonElement?, json: Json): MiCatalogoNavigation? = runCatching {
    if (element == null) return null
    val manifest = json.decodeFromJsonElement<NavigationManifestDto>(element)
    if (manifest.schemaVersion != 1) return null
    fun items(input: List<NavigationItemDto>) = input.mapNotNull { item ->
        if (item.key !in destinationKeys[item.destination].orEmpty() || item.id.isBlank() || item.label.isBlank()) null
        else MiCatalogoNavigationItem(item.id.take(80), item.key, item.label.take(80), item.destination)
    }.distinctBy { it.id }
    val sections = sectionLabels.mapNotNull { (id, label) ->
        manifest.sections.firstOrNull { it.id == id }?.let { section ->
            items(section.items).takeIf { it.isNotEmpty() }?.let { MiCatalogoNavigationSection(id, label, it) }
        }
    }
    val secondary = items(manifest.secondary).filter { it.key in setOf("account", "help", "support", "updates", "practice") }
    val contextual = manifest.contextualSections.filter { it.id == "distribution" && it.requiresLocalSetting == "routesEnabled" }
        .map { section -> MiCatalogoNavigationSection("distribution", "Distribución",
            items(section.items).filter { it.key == "routes" }, "routesEnabled") }
        .filter { it.items.isNotEmpty() }
    MiCatalogoNavigation(sections, secondary, contextual)
}.getOrNull()
