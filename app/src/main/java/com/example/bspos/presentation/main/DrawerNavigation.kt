package com.example.bspos.presentation.main

import com.example.bspos.domain.model.MiCatalogoNavigation
import com.example.bspos.domain.model.MiCatalogoNavigationItem
import com.example.bspos.domain.model.MiCatalogoNavigationSection
import com.example.bspos.domain.model.visibleSections
import com.example.bspos.presentation.navigation.Screen

internal data class DrawerGroup(
    val id: String,
    val title: String,
    val screens: List<Screen>,
    val labels: Map<String, String> = emptyMap(),
    val administrative: Boolean = false
) {
    fun label(screen: Screen): String = labels[screen.route] ?: screen.title
}

private val publicScreens = listOf(
    Screen.Dashboard, Screen.POS, Screen.SalesHistory, Screen.Quotes, Screen.Returns, Screen.Cash, Screen.DayClose,
    Screen.Orders, Screen.Encargos, Screen.Shipments, Screen.Catalog, Screen.Services, Screen.Categories,
    Screen.Photos, Screen.Attributes, Screen.PriceHealth, Screen.AutomaticPrices, Screen.Decants,
    Screen.Inventory, Screen.InventoryAdjustments, Screen.Import, Screen.Suppliers, Screen.PurchaseInvoices,
    Screen.Loads, Screen.Containers, Screen.Customers, Screen.CreditLedger, Screen.Collections, Screen.Finance,
    Screen.Expenses, Screen.Partners, Screen.Reports, Screen.Storefront, Screen.PublicCatalog, Screen.Metrics,
    Screen.ShopSettings, Screen.Team, Screen.Commissions, Screen.Authorizations, Screen.Accountant,
    Screen.Settings, Screen.Printers, Screen.Profile, Screen.Help, Screen.Support, Screen.Updates, Screen.Practice,
    Screen.Routes, Screen.RouteLoads
).associateBy { it.route }

internal fun isNavigationRouteAllowed(
    route: String,
    canSeeMenu: (String) -> Boolean,
    isAdmin: Boolean,
    routesEnabled: Boolean
): Boolean {
    val base = route.substringBefore('?')
    if (base in setOf("dashboard", "profile", "help")) return true
    if (base == "admin_shops") return isAdmin
    if (base in setOf("routes", "route_loads")) return routesEnabled && canSeeMenu("routes")
    if (base == "credit") return canSeeMenu("sales") && canSeeMenu("credit") // legacy POS credit mode
    if (base == "credit_ledger") return canSeeMenu("credit") || canSeeMenu("collections")
    if (base == "catalog") return canSeeMenu("products") || canSeeMenu("services")
    val key = when (base) {
        "pos", "sales_history" -> "sales"
        "catalog_create" -> "products"
        "categories" -> return canSeeMenu("products") || canSeeMenu("services") || canSeeMenu("decants")
        "decant_create" -> "decants"
        "download_app" -> "updates"
        "team" -> "sellers"
        else -> base.takeIf { it in publicScreens } ?: return false
    }
    return canSeeMenu(key)
}

/** Structure fallback for old API/cache versions. All business decisions stay in canSeeMenu. */
private fun fallbackNavigation(): MiCatalogoNavigation {
    fun item(key: String, destination: String, label: String) = MiCatalogoNavigationItem(destination, key, label, destination)
    fun section(id: String, label: String, vararg items: MiCatalogoNavigationItem) = MiCatalogoNavigationSection(id, label, items.toList())
    return MiCatalogoNavigation(listOf(
        section("home", "Inicio", item("", "dashboard", "Inicio")),
        section("sales", "Ventas", item("sales", "pos", "Vender"), item("sales", "sales_history", "Historial de ventas"),
            item("quotes", "quotes", "Cotizaciones"), item("returns", "returns", "Devoluciones"), item("cash", "cash", "Caja"), item("day_close", "day_close", "Cierre de día")),
        section("orders", "Pedidos", item("orders", "orders", "Pedidos"), item("encargos", "encargos", "Encargos"), item("shipments", "shipments", "Envíos")),
        section("catalog", "Catálogo", item("products", "catalog", "Productos"), item("services", "services", "Servicios"),
            item("products", "categories", "Categorías y unidades"), item("photos", "photos", "Fotos"), item("attributes", "attributes", "Marcas y atributos"),
            item("price_health", "price_health", "Diagnóstico de precios"), item("pricing", "pricing", "Precios automáticos"), item("decants", "decants", "Decants")),
        section("inventory", "Inventario", item("inventory", "inventory", "Existencias y movimientos"), item("inventory_adjustments", "inventory_adjustments", "Ajustes y conteos"), item("import", "import", "Importar")),
        section("purchases", "Compras", item("suppliers", "suppliers", "Proveedores"), item("purchase_invoices", "purchase_invoices", "Facturas de compra"), item("loads", "loads", "Cargas"), item("containers", "containers", "Contenedores")),
        section("customers", "Clientes y cobros", item("customers", "customers", "Clientes"), item("credit", "credit_ledger", "Cartera y crédito"), item("collections", "collections", "Cobros y abonos")),
        section("finance", "Finanzas", item("finance", "finance", "Ganancias"), item("expenses", "expenses", "Gastos"), item("partners", "partners", "Socios")),
        section("reports", "Reportes", item("reports", "reports", "Reportes operativos")),
        section("storefront", "Mi tienda", item("storefront", "storefront", "Vitrina y diseño"), item("public_catalog", "public_catalog", "Compartir catálogo"), item("metrics", "metrics", "Métricas y QR")),
        section("settings", "Configuración", item("shop_settings", "shop_settings", "Negocio y menús"), item("sellers", "team", "Equipo"),
            item("commissions", "commissions", "Comisiones"), item("authorizations", "authorizations", "Autorizaciones"), item("accountant", "accountant", "Contador"),
            item("settings", "settings", "Ajustes de la app"), item("printers", "printers", "Impresoras"))
    ), listOf(item("account", "profile", "Mi cuenta"), item("help", "help", "Ayuda"), item("support", "support", "Contactar soporte"),
        item("updates", "updates", "Actualizaciones"), item("practice", "practice", "Guía de práctica")),
        listOf(section("distribution", "Distribución", item("routes", "routes", "Rutas"), item("routes", "route_loads", "Cargas de ruta")).copy(requiresLocalSetting = "routesEnabled")))
}

internal fun buildDrawerNavigation(
    manifest: MiCatalogoNavigation?,
    canSeeMenu: (String) -> Boolean,
    isAdmin: Boolean,
    routesEnabled: Boolean
): List<DrawerGroup> {
    val filtered = (manifest ?: fallbackNavigation()).visibleSections(canSeeMenu, routesEnabled)
    val groups = filtered.mapNotNull { section ->
        val entries = section.items.mapNotNull { item ->
            val screen = publicScreens[item.destination] ?: return@mapNotNull null
            if (!isNavigationRouteAllowed(screen.route, canSeeMenu, isAdmin, routesEnabled)) return@mapNotNull null
            screen to item.label
        }.distinctBy { it.first.route }
        if (entries.isEmpty()) null else DrawerGroup(section.id, section.label, entries.map { it.first }, entries.associate { it.first.route to it.second }, section.id == "settings")
    }.toMutableList()
    // Account/help/home are actor-level destinations even if a cached manifest omits them.
    if (groups.none { Screen.Dashboard in it.screens }) groups.add(0, DrawerGroup("home", "Inicio", listOf(Screen.Dashboard), mapOf("dashboard" to "Inicio")))
    val actorScreens = groups.flatMap { it.screens }.toSet()
    val missingActorScreens = listOf(Screen.Profile, Screen.Help).filter { it !in actorScreens }
    if (missingActorScreens.isNotEmpty()) groups.add(DrawerGroup("account_help_fallback", "Cuenta y ayuda", missingActorScreens))
    if (isAdmin) groups.add(DrawerGroup("platform", "Administración global", listOf(Screen.AdminShops), administrative = true))
    return groups
}

internal fun phoneNavigation(canSeeMenu: (String) -> Boolean): List<Pair<String, Screen>> = listOfNotNull(
    "Inicio" to Screen.Dashboard, ("Vender" to Screen.POS).takeIf { canSeeMenu("sales") },
    ("Pedidos" to Screen.Orders).takeIf { canSeeMenu("orders") }, ("Inventario" to Screen.Inventory).takeIf { canSeeMenu("inventory") }
)

internal fun navigationAreaId(route: String): String? = when (val base = route.substringBefore('?')) {
    "catalog_create", "decant_create" -> "catalog"
    "credit" -> "sales"
    "download_app" -> "account_help"
    "admin_shops" -> "platform"
    else -> fallbackNavigation().let { structure ->
        (structure.sections + structure.contextualSections).firstOrNull { group -> group.items.any { it.destination == base } }?.id
            ?: "account_help".takeIf { structure.secondary.any { it.destination == base } }
    }
}
