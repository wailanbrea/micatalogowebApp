package com.example.bspos.presentation.main

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch

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
    val accountQuota = accountState.shops.firstOrNull()?.quota
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

    val currentShop = accountState.shops.firstOrNull()
    val canManageShop = connection.isAdmin || currentShop?.canManageSellers == true
    val sellerMode = !canManageShop
    val menuPermissions = currentShop?.menuPermissions.orEmpty()
    val canSeeMenu: (String) -> Boolean = { key ->
        canAccessMiCatalogoMenu(connection.isAdmin, canManageShop, menuPermissions, key)
    }
    val drawerSections = buildList {
        add("PRINCIPAL" to listOf(Screen.Dashboard))
        add("VENTAS" to listOfNotNull(
            if (canSeeMenu("sales")) Screen.POS else null,
            if (canSeeMenu("returns")) Screen.Returns else null
        ))
        add("NEGOCIO" to listOfNotNull(
            if (canSeeMenu("products")) Screen.Catalog else null,
            if (canSeeMenu("inventory")) Screen.Inventory else null,
            if (canSeeMenu("customers")) Screen.Customers else null,
            if (canSeeMenu("collections")) Screen.Collections else null
        ))
        add("FINANZAS" to listOfNotNull(
            if (canSeeMenu("cash")) Screen.Cash else null,
            if (canSeeMenu("finance") || canManageShop) Screen.Finance else null
        ))
        add("HERRAMIENTAS" to listOfNotNull(
            if (canSeeMenu("printers")) Screen.Printers else null,
            if (routesEnabled && canSeeMenu("routes")) Screen.Routes else null,
            if (canSeeMenu("more")) Screen.More else null
        ))
        add("ADMINISTRACIÓN" to listOfNotNull(
            if (canSeeMenu("settings")) Screen.Settings else null,
            if (connection.isAdmin) Screen.AdminShops else null
        ))
    }.filter { (_, screens) -> screens.isNotEmpty() }
    val drawerScreens = drawerSections.flatMap { (_, screens) -> screens }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentScreen = (drawerScreens + listOf(
        Screen.Profile, Screen.Suppliers, Screen.Credit, Screen.RouteLoads, Screen.Expenses
    )).firstOrNull { it.route == currentRoute }
    val connectedShop = accountState.shops.firstOrNull()
    val businessName = connectedShop?.name ?: settings?.invoice?.businessName?.ifBlank { null } ?: "MiCatalogo"
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
        360.dp
    } else {
        minOf(320.dp, (screenWidth - 56.dp).coerceAtLeast(280.dp))
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

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp)
                    ) {
                        drawerSections.forEachIndexed { sectionIndex, (sectionTitle, screens) ->
                            Text(
                                text = sectionTitle,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    top = if (sectionIndex == 0) 0.dp else 12.dp,
                                    bottom = 4.dp
                                ),
                                color = BSPOSTheme.colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            screens.forEach { screen ->
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
                                    modifier = Modifier.padding(vertical = 2.dp),
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
                    Text(
                        text = "Versión ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                        color = BSPOSTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = BSPOSTheme.colors.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentScreen?.title ?: "MiCatalogo",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir menu")
                        }
                    },
                    actions = {
                        accountQuota?.let { quota ->
                            Text(
                                text = "Plan ${quota.planLabel}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BSPOSTheme.colors.primary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
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
            BSPOSNavHost(
                navController = navController,
                isTablet = windowWidthSizeClass == WindowWidthSizeClass.Expanded,
                routesEnabled = routesEnabled,
                currency = settings?.currency ?: CurrencyUnit.DOP,
                sellerMode = sellerMode,
                showSettings = canSeeMenu("settings"),
                showAdminShops = connection.isAdmin,
                canSeeMenu = canSeeMenu,
                modifier = Modifier.padding(paddingValues)
            )
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
    canSeeMenu: (String) -> Boolean = { true },
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalCurrency provides currency) {
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = modifier
        ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNewSale = { navController.navigate(Screen.POS.route) },
                onCollections = { navController.navigate(Screen.Collections.route) },
                onInventory = { navController.navigate(Screen.Inventory.route) },
                onProducts = { navController.navigate(Screen.Catalog.route) },
                onCustomers = { navController.navigate(Screen.Customers.route) },
                onRoutes = { if (routesEnabled) navController.navigate(Screen.Routes.route) },
                routesEnabled = routesEnabled && canSeeMenu("routes"),
                sellerMode = sellerMode,
                showSales = canSeeMenu("sales"),
                showCollections = canSeeMenu("collections"),
                showInventory = canSeeMenu("inventory"),
                showProducts = canSeeMenu("products"),
                isExpanded = isTablet
            )
        }
        composable(Screen.POS.route) {
            RestrictedMenuDestination(canSeeMenu("sales"), navController) {
                PosScreen(onOpenCash = { navController.navigate(Screen.Cash.route) })
            }
        }
        composable(Screen.Catalog.route) {
            RestrictedMenuDestination(canSeeMenu("products"), navController) { CatalogHomeScreen(isTablet) }
        }
        composable(Screen.Customers.route) {
            RestrictedMenuDestination(canSeeMenu("customers"), navController) { CustomerScreen() }
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
                    onCollections = { navController.navigate(Screen.Collections.route) },
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
            RestrictedMenuDestination(canSeeMenu("more"), navController) { SupplierScreen() }
        }
        composable(Screen.Inventory.route) {
            RestrictedMenuDestination(canSeeMenu("inventory"), navController) { InventoryScreen() }
        }
        composable(Screen.Collections.route) {
            RestrictedMenuDestination(canSeeMenu("collections"), navController) { CollectionScreen() }
        }
        composable(Screen.Credit.route) {
            RestrictedMenuDestination(canSeeMenu("sales"), navController) { PosScreen(creditOnly = true) }
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
                CashScreen(onCashOpened = onCashOpened)
            }
        }
        composable(Screen.Returns.route) {
            RestrictedMenuDestination(canSeeMenu("returns"), navController) { ReturnScreen() }
        }
        composable(Screen.RouteLoads.route) {
            if (routesEnabled && canSeeMenu("routes")) {
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
                    onNavigateToCash = { navController.navigate(Screen.Cash.route) }
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
