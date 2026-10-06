package com.example.bspos.presentation.main

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.bspos.core.ui.theme.BSPOSTheme
import com.example.bspos.BuildConfig
import com.example.bspos.core.money.LocalCurrency
import com.example.bspos.domain.model.CurrencyUnit
import com.example.bspos.domain.model.SellerMenuOptions
import com.example.bspos.domain.model.MiCatalogoBusinessPresentation
import com.example.bspos.domain.model.canAccessMiCatalogoMenu
import com.example.bspos.presentation.navigation.Screen
import com.example.bspos.presentation.catalog.CatalogSettingsScreen
import com.example.bspos.presentation.catalog.CatalogHomeScreen
import com.example.bspos.presentation.supplier.SupplierScreen
import com.example.bspos.presentation.inventory.InventoryScreen
import com.example.bspos.presentation.more.MoreScreen
import com.example.bspos.presentation.customer.CustomerScreen
import com.example.bspos.presentation.route.RouteScreen
import com.example.bspos.presentation.pos.PosScreen
import com.example.bspos.presentation.sales.SalesHistoryScreen
import com.example.bspos.presentation.collection.CollectionScreen
import com.example.bspos.presentation.dashboard.DashboardScreen
import com.example.bspos.presentation.cash.CashScreen
import com.example.bspos.presentation.returning.ReturnScreen
import com.example.bspos.presentation.settings.SettingsScreen
import com.example.bspos.presentation.routeload.RouteLoadScreen
import com.example.bspos.presentation.settings.SettingsViewModel
import com.example.bspos.presentation.profile.ProfileScreen
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.printer.BluetoothPrinterScreen
import com.example.bspos.presentation.adminshops.AdminShopsScreen
import com.example.bspos.presentation.finance.FinanceScreen
import com.example.bspos.presentation.feature.FeatureModuleScreen
import com.example.bspos.presentation.quote.QuoteScreen
import com.example.bspos.presentation.orders.OrdersScreen
import com.example.bspos.presentation.dayclose.DayCloseScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch

private data class DrawerGroup(
    val title: String,
    val screens: List<Screen>,
    val administrative: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BSPOSMainScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()
    val connection by settingsViewModel.miCatalogoConnection.collectAsState()
    val accountState by settingsViewModel.miCatalogoUi.collectAsState()
    LaunchedEffect(connection.isConfigured) {
        if (connection.isConfigured) settingsViewModel.loadShops()
    }
    val activeShop = accountState.shops.firstOrNull { it.id == connection.activeShopId }
        ?: accountState.shops.firstOrNull()
    val accountQuota = activeShop?.quota
    val routesEnabled = settings?.routesEnabled == true
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.POS.route
    val globalMessage by UiErrorBus.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(globalMessage) {
        globalMessage?.let {
            snackbarHostState.showSnackbar(it)
            UiErrorBus.clear()
        }
    }

    val currentShop = activeShop
    val canManageShop = connection.isAdmin || currentShop?.canManageSellers == true
    val sellerMode = !canManageShop
    val menuPermissions = currentShop?.menuPermissions.orEmpty()
    val canSeeMenu: (String) -> Boolean = { key ->
        canAccessMiCatalogoMenu(connection.isAdmin, canManageShop, menuPermissions, key, currentShop?.capabilities.orEmpty())
    }
    val drawerGroups = buildList {
        add(
            DrawerGroup(
                title = "Operación",
                screens = listOfNotNull(
                    Screen.Dashboard,
                    Screen.POS.takeIf { canSeeMenu("sales") },
                    Screen.SalesHistory.takeIf { canSeeMenu("sales") },
                    Screen.Quotes.takeIf { canSeeMenu("quotes") },
                    Screen.Orders.takeIf { canSeeMenu("orders") },
                    Screen.Encargos.takeIf { canSeeMenu("encargos") },
                    Screen.Shipments.takeIf { canSeeMenu("shipments") },
                    Screen.DayClose.takeIf { canSeeMenu("day_close") },
                    // Puntto exposes the app download/update entry inside Operación;
                    // reuse MiCatalogo's existing update module instead of inventing
                    // a hard-coded APK URL.
                    Screen.DownloadApp.takeIf { canSeeMenu("updates") },
                    Screen.Cash.takeIf { canSeeMenu("cash") },
                    Screen.Returns.takeIf { canSeeMenu("returns") },
                    Screen.Routes.takeIf { routesEnabled && canSeeMenu("routes") }
                )
            )
        )
        add(
            DrawerGroup(
                title = "Compras",
                screens = listOfNotNull(
                    Screen.Containers.takeIf { canSeeMenu("containers") },
                    Screen.RouteLoads.takeIf { canSeeMenu("loads") || (routesEnabled && canSeeMenu("routes")) },
                    Screen.Suppliers.takeIf { canSeeMenu("suppliers") || canSeeMenu("more") },
                    Screen.PurchaseInvoices.takeIf { canSeeMenu("purchase_invoices") }
                )
            )
        )
        add(
            DrawerGroup(
                title = "Catálogo",
                screens = listOfNotNull(
                    Screen.Catalog.takeIf { canSeeMenu("products") },
                    Screen.Inventory.takeIf { canSeeMenu("inventory") },
                    Screen.Printers.takeIf { canSeeMenu("printers") },
                    Screen.Photos.takeIf { canSeeMenu("photos") },
                    Screen.Storefront.takeIf { canSeeMenu("storefront") },
                    Screen.Services.takeIf { canSeeMenu("services") },
                    Screen.PriceHealth.takeIf { canSeeMenu("price_health") },
                    Screen.AutomaticPrices.takeIf { canSeeMenu("pricing") },
                    Screen.Decants.takeIf { canSeeMenu("decants") },
                    Screen.Attributes.takeIf { canSeeMenu("attributes") },
                    Screen.Import.takeIf { canSeeMenu("import") },
                    Screen.Metrics.takeIf { canSeeMenu("metrics") },
                    Screen.PublicCatalog.takeIf { canSeeMenu("public_catalog") }
                )
            )
        )
        add(
            DrawerGroup(
                title = "Cobros",
                screens = listOfNotNull(
                    Screen.CreditLedger.takeIf { canSeeMenu("collections") || canSeeMenu("credit") },
                    Screen.Customers.takeIf { canSeeMenu("customers") }
                )
            )
        )
        add(
            DrawerGroup(
                title = "Finanzas",
                screens = listOfNotNull(
                    Screen.Finance.takeIf { canSeeMenu("finance") || canManageShop },
                    Screen.InventoryAdjustments.takeIf { canSeeMenu("inventory_adjustments") },
                    Screen.Expenses.takeIf { canSeeMenu("finance") || canManageShop },
                    Screen.Partners.takeIf { canSeeMenu("partners") }
                )
            )
        )
        add(
            DrawerGroup(
                title = "Análisis",
                screens = listOfNotNull(Screen.Reports.takeIf { canSeeMenu("reports") })
            )
        )
        add(
            DrawerGroup(
                title = "Equipo",
                administrative = true,
                screens = listOfNotNull(
                    Screen.Commissions.takeIf { canSeeMenu("commissions") },
                    Screen.Authorizations.takeIf { canSeeMenu("authorizations") }
                )
            )
        )
        add(
            DrawerGroup(
                title = "Ajustes",
                administrative = true,
                screens = listOfNotNull(
                    Screen.Settings.takeIf { canSeeMenu("settings") },
                    Screen.ShopSettings.takeIf { canSeeMenu("shop_settings") },
                    Screen.Team.takeIf { canSeeMenu("sellers") },
                    Screen.Accountant.takeIf { canSeeMenu("accountant") },
                    Screen.Profile,
                    Screen.Updates.takeIf { canSeeMenu("updates") },
                    Screen.Help.takeIf { canSeeMenu("help") },
                    Screen.Practice.takeIf { canSeeMenu("practice") },
                    Screen.Support.takeIf { canSeeMenu("support") },
                    Screen.AdminShops.takeIf { connection.isAdmin }
                )
            )
        )
    }.filter { it.screens.isNotEmpty() }
    val drawerScreens = drawerGroups.flatMap { it.screens }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var shopMenuOpen by remember { mutableStateOf(false) }
    var menuQuery by remember { mutableStateOf("") }
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
    val currentScreen = drawerScreens.firstOrNull { it.route == currentRoute }
    val currentGroup = drawerGroups.firstOrNull { group -> group.screens.any { it.route == currentRoute } }?.title ?: "Inicio"
    val connectedShop = activeShop
    val businessName = connectedShop?.name ?: settings?.invoice?.businessName?.ifBlank { null } ?: "MiCatalogo"
    val drawerWidth = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
        360.dp
    } else {
        LocalConfiguration.current.screenWidthDp.dp * 0.62f
    }
    val navigateTo: (Screen) -> Unit = { screen ->
        if (currentRoute != screen.route) {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
                drawerContainerColor = BSPOSTheme.colors.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(BSPOSTheme.colors.secondaryNavy),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = businessName.firstOrNull()?.uppercase() ?: "M",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = businessName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                color = BSPOSTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Tu negocio, siempre contigo", fontSize = 12.sp, color = BSPOSTheme.colors.textSecondary, maxLines = 1)
                        }
                    }

                    if (accountState.shops.size > 1) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                            TextButton(
                                onClick = { shopMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                    Text("Tienda activa", fontSize = 11.sp, color = BSPOSTheme.colors.textSecondary)
                                    Text(
                                        connectedShop?.name ?: "Seleccionar tienda",
                                        fontWeight = FontWeight.Bold,
                                        color = BSPOSTheme.colors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text("Cambiar", color = BSPOSTheme.colors.primary, fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = shopMenuOpen,
                                onDismissRequest = { shopMenuOpen = false }
                            ) {
                                accountState.shops.forEach { shop ->
                                    DropdownMenuItem(
                                        text = { Text(shop.name, fontWeight = if (shop.id == connectedShop?.id) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            shopMenuOpen = false
                                            settingsViewModel.selectShop(shop)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = menuQuery,
                        onValueChange = { menuQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        singleLine = true,
                        placeholder = { Text("Buscar en el menú…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp)
                    ) {
                        drawerGroups.forEach { group ->
                            val visibleScreens = group.screens.filter { screen ->
                                menuQuery.isBlank() || screen.title.contains(menuQuery.trim(), ignoreCase = true)
                            }
                            if (visibleScreens.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        collapsedGroups = if (group.title in collapsedGroups) {
                                            collapsedGroups - group.title
                                        } else {
                                            collapsedGroups + group.title
                                        }
                                    }.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = group.title,
                                        modifier = Modifier.weight(1f),
                                        color = if (group.administrative) BSPOSTheme.colors.primary else BSPOSTheme.colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (group.title in collapsedGroups) "›" else "⌄",
                                        color = BSPOSTheme.colors.textSecondary,
                                        fontSize = 18.sp,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                if (group.administrative) {
                                    androidx.compose.material3.HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = BSPOSTheme.colors.primary,
                                        thickness = 2.dp
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = visibleScreens.isNotEmpty() && group.title !in collapsedGroups,
                                enter = fadeIn() + androidx.compose.animation.expandVertically(),
                                exit = fadeOut() + androidx.compose.animation.shrinkVertically()
                            ) {
                                Column(Modifier.animateContentSize()) {
                                    visibleScreens.forEach { screen ->
                                        val selected = currentRoute == screen.route
                                        NavigationDrawerItem(
                                            label = { Text(screen.title, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold) },
                                            selected = selected,
                                            onClick = { navigateTo(screen) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                                    contentDescription = null
                                                )
                                            },
                                            modifier = Modifier.padding(vertical = 3.dp),
                                            colors = NavigationDrawerItemDefaults.colors(
                                                selectedContainerColor = BSPOSTheme.colors.primaryLight,
                                                selectedIconColor = BSPOSTheme.colors.primary,
                                                selectedTextColor = BSPOSTheme.colors.primary,
                                                unselectedContainerColor = Color.Transparent,
                                                unselectedIconColor = BSPOSTheme.colors.textPrimary,
                                                unselectedTextColor = BSPOSTheme.colors.textPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }

                    accountQuota?.let { quota ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(BSPOSTheme.shapes.large)
                                .background(BSPOSTheme.colors.primaryLight)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ShoppingCart, null, tint = BSPOSTheme.colors.primary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("MiCatalogo ${quota.planLabel}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                Text("${quota.productCount} de ${quota.productLimit} productos", color = BSPOSTheme.colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp).clickable {
                            navController.navigate(Screen.Profile.route)
                            scope.launch { drawerState.close() }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BSPOSTheme.colors.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, null, tint = BSPOSTheme.colors.textPrimary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mi cuenta", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = connection.accountEmail.ifBlank { connection.rememberedEmail }.ifBlank { "Cuenta conectada" },
                                color = BSPOSTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(BSPOSTheme.shapes.medium)
                            .clickable {
                                val slug = connectedShop?.slug?.trim().orEmpty()
                                if (slug.isBlank()) {
                                    UiErrorBus.show("Esta tienda todavía no tiene enlace público.")
                                } else {
                                    val publicUrl = "${BuildConfig.MICATALOGO_API_BASE_URL.trimEnd('/')}/tienda/$slug"
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(publicUrl)))
                                    }.onFailure {
                                        UiErrorBus.show("No se pudo abrir la tienda pública.")
                                    }
                                }
                                scope.launch { drawerState.close() }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = BSPOSTheme.colors.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Ver tienda", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BSPOSTheme.colors.primary)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(BSPOSTheme.shapes.medium)
                            .clickable {
                                settingsViewModel.logout()
                                scope.launch { drawerState.close() }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = BSPOSTheme.colors.textSecondary)
                        Spacer(Modifier.width(10.dp))
                        Text("Cerrar sesión", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BSPOSTheme.colors.textPrimary)
                    }
                    Text(
                        text = "Versión ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                        color = BSPOSTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = BSPOSTheme.colors.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (windowWidthSizeClass != WindowWidthSizeClass.Expanded) {
                    val bottomItems = listOfNotNull(
                        Triple("Terminal", Screen.POS, canSeeMenu("sales")),
                        Triple("Pedidos", Screen.Orders, canSeeMenu("orders")),
                        Triple("Inventario", Screen.Inventory, canSeeMenu("inventory")),
                        Triple("Más", Screen.More, canSeeMenu("more"))
                    ).filter { it.third }
                    if (bottomItems.isNotEmpty()) {
                        NavigationBar(
                            containerColor = BSPOSTheme.colors.surface,
                            tonalElevation = 8.dp
                        ) {
                            bottomItems.forEach { (label, screen, _) ->
                                NavigationBarItem(
                                    selected = currentRoute == screen.route,
                                    onClick = { navigateTo(screen) },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == screen.route) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BSPOSTheme.colors.primary,
                                        selectedTextColor = BSPOSTheme.colors.primary,
                                        indicatorColor = BSPOSTheme.colors.primaryLight,
                                        unselectedIconColor = BSPOSTheme.colors.textSecondary,
                                        unselectedTextColor = BSPOSTheme.colors.textSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "$currentGroup /",
                                color = BSPOSTheme.colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = currentScreen?.title ?: if (currentRoute == Screen.Profile.route) Screen.Profile.title else "MiCatalogo",
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir menu")
                        }
                    },
                    actions = {
                        accountQuota?.let { quota ->
                            AssistChip(
                                onClick = { },
                                label = { Text("${quota.planLabel} · 30 d", maxLines = 1) },
                                border = androidx.compose.material3.AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = BSPOSTheme.colors.outline
                                ),
                                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                    containerColor = BSPOSTheme.colors.surface,
                                    labelColor = BSPOSTheme.colors.textPrimary
                                )
                            )
                        }
                        TextButton(onClick = { navigateTo(Screen.Help) }) {
                            Text("Ayuda", color = BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BSPOSTheme.colors.surface,
                        titleContentColor = BSPOSTheme.colors.textPrimary,
                        navigationIconContentColor = BSPOSTheme.colors.textPrimary
                    )
                )
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 18 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 18 })
                },
                label = "screen-transition",
                modifier = Modifier.fillMaxSize()
            ) {
                BSPOSNavHost(
                    navController = navController,
                    isTablet = windowWidthSizeClass == WindowWidthSizeClass.Expanded,
                    routesEnabled = routesEnabled,
                    currency = settings?.currency ?: CurrencyUnit.DOP,
                    sellerMode = sellerMode,
                    showSettings = canSeeMenu("settings"),
                    showAdminShops = connection.isAdmin,
                    remoteShopAvailable = connectedShop != null,
                    presentation = connectedShop?.presentation ?: MiCatalogoBusinessPresentation(),
                    businessName = businessName,
                    productFields = connectedShop?.productFields?.toSet().orEmpty(),
                    shopName = connectedShop?.name,
                    shopId = connectedShop?.id,
                    shopSlug = connectedShop?.slug,
                    canSeeMenu = canSeeMenu,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun BSPOSNavHost(
    navController: androidx.navigation.NavHostController,
    isTablet: Boolean,
    routesEnabled: Boolean,
    currency: CurrencyUnit,
    sellerMode: Boolean = false,
    showSettings: Boolean = false,
    showAdminShops: Boolean = false,
    remoteShopAvailable: Boolean = false,
    presentation: MiCatalogoBusinessPresentation = MiCatalogoBusinessPresentation(),
    businessName: String = "tu negocio",
    productFields: Set<String> = emptySet(),
    shopName: String? = null,
    shopId: String? = null,
    shopSlug: String? = null,
    canSeeMenu: (String) -> Boolean = { true },
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalCurrency provides currency) {
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = modifier,
            enterTransition = { fadeIn(animationSpec = tween(180)) + slideInHorizontally(animationSpec = tween(220)) { it / 12 } },
            exitTransition = { fadeOut(animationSpec = tween(130)) + slideOutHorizontally(animationSpec = tween(180)) { -it / 12 } },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) + slideInHorizontally(animationSpec = tween(220)) { -it / 12 } },
            popExitTransition = { fadeOut(animationSpec = tween(130)) + slideOutHorizontally(animationSpec = tween(180)) { it / 12 } }
        ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNewSale = { navController.navigate(Screen.POS.route) },
                onCollections = { navController.navigate(Screen.CreditLedger.route) },
                onInventory = { navController.navigate(Screen.Inventory.route) },
                onProducts = { navController.navigate(Screen.Catalog.route) },
                onCustomers = { navController.navigate(Screen.Customers.route) },
                onRoutes = { if (routesEnabled) navController.navigate(Screen.Routes.route) },
                onReturns = { if (canSeeMenu("returns")) navController.navigate(Screen.Returns.route) },
                routesEnabled = routesEnabled && canSeeMenu("routes"),
                sellerMode = sellerMode,
                showSales = canSeeMenu("sales"),
                showCollections = canSeeMenu("collections"),
                showInventory = canSeeMenu("inventory"),
                showProducts = canSeeMenu("products"),
                presentation = presentation,
                businessName = businessName,
                isExpanded = isTablet
            )
        }
        composable(Screen.POS.route) {
            RestrictedMenuDestination(canSeeMenu("sales"), navController) {
                PosScreen(
                    presentation = presentation.copy(posShowCredit = presentation.posShowCredit && canSeeMenu("collections")),
                    onOpenCash = { navController.navigate(Screen.Cash.route) },
                    onOpenQuotes = { if (canSeeMenu("quotes")) navController.navigate(Screen.Quotes.route) },
                    onOpenDayClose = { if (canSeeMenu("day_close")) navController.navigate(Screen.DayClose.route) }
                )
            }
        }
        composable(Screen.SalesHistory.route) {
            RestrictedMenuDestination(canSeeMenu("sales"), navController) {
                SalesHistoryScreen(
                    onReturns = { if (canSeeMenu("returns")) navController.navigate(Screen.Returns.route) }
                )
            }
        }
        composable(Screen.Quotes.route) {
            RestrictedMenuDestination(canSeeMenu("quotes"), navController) { QuoteScreen() }
        }
        composable(Screen.Orders.route) {
            RestrictedMenuDestination(canSeeMenu("orders"), navController) { OrdersScreen() }
        }
        composable(Screen.Encargos.route) {
            RestrictedMenuDestination(canSeeMenu("encargos"), navController) { OrdersScreen(feature = "encargos") }
        }
        composable(Screen.Shipments.route) {
            RestrictedMenuDestination(canSeeMenu("shipments"), navController) { OrdersScreen(feature = "shipments") }
        }
        composable(Screen.DayClose.route) {
            RestrictedMenuDestination(canSeeMenu("day_close"), navController) { DayCloseScreen() }
        }
        composable(Screen.DownloadApp.route) {
            FeatureDestination("updates", canSeeMenu("updates"), navController)
        }
        composable(Screen.Containers.route) { FeatureDestination("containers", canSeeMenu("containers"), navController) }
        composable(Screen.PurchaseInvoices.route) { FeatureDestination("purchase_invoices", canSeeMenu("purchase_invoices"), navController) }
        composable(Screen.Photos.route) {
            RestrictedMenuDestination(canSeeMenu("photos"), navController) {
                CatalogHomeScreen(
                    isTablet = isTablet,
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("inventory") || canSeeMenu("finance"),
                    showProductCode = productFields.isEmpty() || "sku" in productFields || "barcode" in productFields,
                    photosOnly = true
                )
            }
        }
        composable(Screen.Storefront.route) {
            RestrictedMenuDestination(canSeeMenu("storefront"), navController) {
                StorefrontWorkspaceScreen(
                    shopName = shopName,
                    shopId = shopId,
                    shopSlug = shopSlug
                )
            }
        }
        composable(Screen.Services.route) { FeatureDestination("services", canSeeMenu("services"), navController) }
        composable(Screen.PriceHealth.route) { FeatureDestination("price_health", canSeeMenu("price_health"), navController) }
        composable(Screen.AutomaticPrices.route) { FeatureDestination("pricing", canSeeMenu("pricing"), navController) }
        composable(Screen.Decants.route) { FeatureDestination("decants", canSeeMenu("decants"), navController) }
        composable(Screen.Attributes.route) { FeatureDestination("attributes", canSeeMenu("attributes"), navController) }
        composable(Screen.Import.route) {
            RestrictedMenuDestination(canSeeMenu("import"), navController) {
                InventoryScreen(
                    onOpenProducts = if (canSeeMenu("products")) {{ navController.navigate(Screen.Catalog.route) }} else null,
                    onOpenPrices = if (canSeeMenu("price_health")) {{ navController.navigate(Screen.PriceHealth.route) }} else null,
                    initialImport = true
                )
            }
        }
        composable(Screen.InventoryAdjustments.route) { FeatureDestination("inventory_adjustments", canSeeMenu("inventory_adjustments"), navController) }
        composable(Screen.Partners.route) { FeatureDestination("partners", canSeeMenu("partners"), navController) }
        composable(Screen.Reports.route) { FeatureDestination("reports", canSeeMenu("reports"), navController) }
        composable(Screen.Commissions.route) { FeatureDestination("commissions", canSeeMenu("commissions"), navController) }
        composable(Screen.Authorizations.route) { FeatureDestination("authorizations", canSeeMenu("authorizations"), navController) }
        composable(Screen.Accountant.route) { FeatureDestination("accountant", canSeeMenu("accountant"), navController) }
        composable(Screen.Updates.route) { FeatureDestination("updates", canSeeMenu("updates"), navController) }
        composable(Screen.Help.route) { FeatureDestination("help", canSeeMenu("help"), navController) }
        composable(Screen.Practice.route) { FeatureDestination("practice", canSeeMenu("practice"), navController) }
        composable(Screen.Support.route) { FeatureDestination("support", canSeeMenu("support"), navController) }
        composable(Screen.Metrics.route) { FeatureDestination("metrics", canSeeMenu("metrics"), navController) }
        composable(Screen.PublicCatalog.route) { FeatureDestination("public_catalog", canSeeMenu("public_catalog"), navController) }
        composable(Screen.ShopSettings.route) { FeatureDestination("shop_settings", canSeeMenu("shop_settings"), navController) }
        composable(Screen.Team.route) { FeatureDestination("sellers", canSeeMenu("sellers"), navController) }
        composable(Screen.Catalog.route) {
            RestrictedMenuDestination(canSeeMenu("products"), navController) {
                CatalogHomeScreen(
                    isTablet = isTablet,
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("inventory") || canSeeMenu("finance"),
                    showProductCode = productFields.isEmpty() || "sku" in productFields || "barcode" in productFields
                )
            }
        }
        composable(Screen.DecantCreate.route) {
            RestrictedMenuDestination(canSeeMenu("decants"), navController) {
                CatalogHomeScreen(
                    isTablet = isTablet,
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("inventory") || canSeeMenu("finance"),
                    showProductCode = productFields.isEmpty() || "sku" in productFields || "barcode" in productFields,
                    initialDecantMode = true
                )
            }
        }
        composable(Screen.Customers.route) {
            RestrictedMenuDestination(canSeeMenu("customers"), navController) {
                CustomerScreen(presentation = presentation.copy(customersShowCredit = presentation.customersShowCredit && canSeeMenu("collections")))
            }
        }
        composable(Screen.Routes.route) {
            if (routesEnabled && canSeeMenu("routes")) {
                RouteScreen()
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            }
        }
        composable(Screen.More.route) {
            RestrictedMenuDestination(canSeeMenu("more"), navController) {
                MoreScreen(
                    onSuppliers = { navController.navigate(Screen.Suppliers.route) },
                    onInventory = { navController.navigate(Screen.Inventory.route) },
                    onCollections = { navController.navigate(Screen.CreditLedger.route) },
                    onCredit = { navController.navigate(Screen.Credit.route) },
                    onCash = { navController.navigate(Screen.Cash.route) },
                    onReturns = { navController.navigate(Screen.Returns.route) },
                    onRouteLoads = { navController.navigate(Screen.RouteLoads.route) },
                    onPrinters = { navController.navigate(Screen.Printers.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                    onFinance = { navController.navigate(Screen.Finance.route) },
                    showSuppliers = canSeeMenu("more"),
                    showInventory = canSeeMenu("inventory"),
                    showCollections = canSeeMenu("collections"),
                    showCredit = canSeeMenu("sales"),
                    showCash = canSeeMenu("cash"),
                    showReturns = canSeeMenu("returns"),
                    showRouteLoads = routesEnabled && canSeeMenu("routes"),
                    showPrinters = canSeeMenu("printers"),
                    showSettings = showSettings,
                    showFinance = canSeeMenu("finance") || !sellerMode
                )
            }
        }
        composable(Screen.Suppliers.route) {
            RestrictedMenuDestination(canSeeMenu("suppliers") || canSeeMenu("more"), navController) { SupplierScreen() }
        }
        composable(Screen.Inventory.route) {
            RestrictedMenuDestination(canSeeMenu("inventory"), navController) {
                InventoryScreen(
                    onOpenProducts = if (canSeeMenu("products")) {{ navController.navigate(Screen.Catalog.route) }} else null,
                    onOpenPrices = if (canSeeMenu("price_health")) {{ navController.navigate(Screen.PriceHealth.route) }} else null
                )
            }
        }
        composable(Screen.Collections.route) {
            RestrictedMenuDestination(canSeeMenu("collections"), navController) { CollectionScreen() }
        }
        composable(Screen.CreditLedger.route) {
            RestrictedMenuDestination(canSeeMenu("collections"), navController) { CollectionScreen() }
        }
        composable(Screen.Credit.route) {
            RestrictedMenuDestination(canSeeMenu("sales") && canSeeMenu("collections"), navController) {
                PosScreen(creditOnly = true, presentation = presentation.copy(posShowCredit = true))
            }
        }
        composable(Screen.Cash.route) {
            RestrictedMenuDestination(canSeeMenu("cash"), navController) {
                val onCashOpened: (() -> Unit)? = if (
                    navController.previousBackStackEntry?.destination?.route == Screen.POS.route
                ) {
                    { navController.popBackStack(); Unit }
                } else {
                    null
                }
                var remoteCash by remember { mutableStateOf(onCashOpened == null && remoteShopAvailable) }
                Column {
                    Row {
                        FilterChip(
                            selected = remoteCash,
                            onClick = { remoteCash = true },
                            label = { Text("Caja del servidor") },
                            enabled = remoteShopAvailable
                        )
                        FilterChip(
                            selected = !remoteCash,
                            onClick = { remoteCash = false },
                            label = { Text("Caja local POS") }
                        )
                    }
                    Text(
                        if (remoteCash) "La caja del servidor consolida las ventas sincronizadas."
                        else "La caja local se usa cuando trabajas sin conexión.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(Modifier.weight(1f)) {
                        if (remoteCash) FinanceScreen(initialTab = 4)
                        else CashScreen(onCashOpened = onCashOpened)
                    }
                }
            }
        }
        composable(Screen.Returns.route) {
            RestrictedMenuDestination(canSeeMenu("returns"), navController) { ReturnScreen() }
        }
        composable(Screen.RouteLoads.route) {
            if (canSeeMenu("loads") || (routesEnabled && canSeeMenu("routes"))) {
                RouteLoadScreen()
            } else {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            }
        }
        composable(Screen.Printers.route) {
            RestrictedMenuDestination(canSeeMenu("printers"), navController) { BluetoothPrinterScreen() }
        }
        composable(Screen.Settings.route) {
            RestrictedMenuDestination(showSettings, navController) { SettingsScreen() }
        }
        composable(Screen.AdminShops.route) {
            RestrictedMenuDestination(showAdminShops, navController) { AdminShopsScreen() }
        }
        composable(Screen.Finance.route) {
            RestrictedMenuDestination(canSeeMenu("finance") || !sellerMode, navController) {
                FinanceScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCash = { navController.navigate(Screen.Cash.route) }
                )
            }
        }
        composable(Screen.Expenses.route) {
            RestrictedMenuDestination(canSeeMenu("finance") || !sellerMode, navController) {
                FinanceScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCash = { navController.navigate(Screen.Cash.route) },
                    initialTab = 3
                )
            }
        }
        composable(Screen.Profile.route) {
            ProfileScreen()
        }
        }
    }
}

@Composable
private fun RestrictedMenuDestination(
    isAllowed: Boolean,
    navController: androidx.navigation.NavHostController,
    content: @Composable () -> Unit
) {
    if (isAllowed) {
        content()
    } else {
        LaunchedEffect(Unit) {
            navController.popBackStack(Screen.Dashboard.route, false)
        }
    }
}

@Composable
private fun StorefrontWorkspaceScreen(
    shopName: String?,
    shopId: String?,
    shopSlug: String?
) {
    val context = LocalContext.current
    val baseUrl = BuildConfig.MICATALOGO_API_BASE_URL.trimEnd('/')
    val publicUrl = shopSlug?.trim()?.takeIf { it.isNotBlank() }?.let { "$baseUrl/tienda/$it" }
    val settingsUrl = shopId?.trim()?.takeIf { it.isNotBlank() }?.let { "$baseUrl/panel/tiendas/$it/editar" }

    fun openUrl(url: String?, unavailableMessage: String) {
        if (url.isNullOrBlank()) {
            UiErrorBus.show(unavailableMessage)
            return
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            UiErrorBus.show("No se pudo abrir el enlace de la tienda.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BSPOSTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
    ) {
        Text("CATÁLOGO", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Mi tienda", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("Administra la vitrina pública y revisa la experiencia que ven tus clientes.", color = BSPOSTheme.colors.textSecondary)
        Text(shopName?.takeIf { it.isNotBlank() } ?: "Tienda activa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Los cambios de catálogo se reflejan en el mismo enlace público.", color = BSPOSTheme.colors.textSecondary)
        TextButton(onClick = { openUrl(publicUrl, "Esta tienda todavía no tiene un enlace público.") }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Ver mi tienda")
        }
        TextButton(onClick = { openUrl(settingsUrl, "No se encontró la configuración de esta tienda.") }) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Personalizar apariencia")
        }
        Text(
            "La edición avanzada continúa en el panel web; esta entrada ya conserva el contexto de la tienda activa y evita dejar una pantalla sin acción.",
            color = BSPOSTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FeatureDestination(
    feature: String,
    isAllowed: Boolean,
    navController: androidx.navigation.NavHostController
) {
    RestrictedMenuDestination(isAllowed, navController) {
        FeatureModuleScreen(
            feature = feature,
            onAction = { label -> navigateFeatureAction(label, navController) }
        )
    }
}

private fun navigateFeatureAction(
    label: String,
    navController: androidx.navigation.NavHostController
): Boolean {
    val normalized = label.trim().lowercase()
    val target = when (normalized) {
        "ir al punto de venta", "ir a terminal", "explorar terminal", "ver terminal", "ir a punto de venta" -> Screen.POS
        "ver pedidos" -> Screen.Orders
        "ver inventario", "abrir inventario", "explorar inventario", "ver lotes y costos fifo" -> Screen.Inventory
        "ver caja", "abrir caja" -> Screen.Cash
        "ver ganancias", "ver ganancias y resumen" -> Screen.Finance
        "ver reportes" -> Screen.Reports
        "ver clientes y cobros", "gestionar clientes" -> Screen.Customers
        "ver productos", "crear producto o servicio" -> Screen.Catalog
        "crear presentación decant" -> Screen.DecantCreate
        "precios automáticos", "precios y costos" -> Screen.PriceHealth
        "administrar reglas" -> Screen.AutomaticPrices
        "abrir importador" -> Screen.Import
        "abrir métricas completas", "ver estadísticas", "ver qr y métricas" -> Screen.Metrics
        "administrar equipo", "administrar vendedores" -> Screen.Team
        "ver comisiones" -> Screen.Commissions
        "configuración de tienda", "configurar apariencia", "editar configuración" -> Screen.ShopSettings
        "ver mi tienda" -> Screen.Storefront
        "registrar gasto" -> Screen.Expenses
        "contactar soporte", "nueva solicitud" -> Screen.Support
        "volver al resumen" -> Screen.Dashboard
        else -> null
    } ?: return false
    navController.navigate(target.route) {
        launchSingleTop = true
        restoreState = true
    }
    return true
}

@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BSPOSTheme.colors.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = BSPOSTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = BSPOSTheme.colors.textSecondary
            )
        }
    }
}
