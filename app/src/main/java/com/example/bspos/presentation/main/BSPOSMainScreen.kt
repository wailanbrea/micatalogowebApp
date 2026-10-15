package com.example.bspos.presentation.main

import androidx.compose.foundation.layout.navigationBarsPadding

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.Normalizer
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.example.bspos.presentation.customer.CustomerScreen
import com.example.bspos.presentation.route.RouteScreen
import com.example.bspos.presentation.pos.PosScreen
import com.example.bspos.presentation.sales.SalesHistoryScreen
import com.example.bspos.presentation.collection.CollectionScreen
import com.example.bspos.presentation.dashboard.DashboardScreen
import com.example.bspos.presentation.cash.CashScreen
import com.example.bspos.presentation.returning.ReturnScreen
import com.example.bspos.presentation.settings.SettingsScreen
import com.example.bspos.presentation.settings.ShopSettingsScreen
import com.example.bspos.presentation.settings.ShopSettingsViewModel
import com.example.bspos.data.micatalogo.dto.ShopSettingsUpdateDto
import com.example.bspos.presentation.routeload.RouteLoadScreen
import com.example.bspos.presentation.settings.SettingsViewModel
import com.example.bspos.presentation.profile.ProfileScreen
import com.example.bspos.presentation.common.UiErrorBus
import com.example.bspos.presentation.printer.BluetoothPrinterScreen
import com.example.bspos.presentation.adminshops.AdminShopsScreen
import com.example.bspos.presentation.finance.FinanceScreen
import com.example.bspos.presentation.feature.FeatureModuleScreen
import com.example.bspos.presentation.feature.AttributesScreen
import com.example.bspos.presentation.feature.AutomaticPricesScreen
import com.example.bspos.presentation.feature.PartnersScreen
import com.example.bspos.presentation.feature.HelpScreen
import com.example.bspos.presentation.feature.AccountantScreen
import com.example.bspos.presentation.purchase.PurchaseModuleScreen
import com.example.bspos.presentation.quote.QuoteScreen
import com.example.bspos.presentation.orders.OrdersScreen
import com.example.bspos.presentation.support.SupportChatScreen
import com.example.bspos.presentation.support.SupportFloatingActionButton
import com.example.bspos.presentation.dayclose.DayCloseScreen
import com.example.bspos.presentation.startup.StartupSnapshot
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BSPOSMainScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    startupSnapshot: StartupSnapshot,
    onRetryStartup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sessionSettings: SettingsViewModel = hiltViewModel()
    val sessionConnection by sessionSettings.miCatalogoConnection.collectAsState()
    key(sessionConnection.accountEmail, sessionConnection.activeShopId) {
        ShopScopedMainScreen(windowWidthSizeClass,startupSnapshot,onRetryStartup,modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopScopedMainScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    startupSnapshot: StartupSnapshot,
    onRetryStartup: () -> Unit,
    modifier: Modifier
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()
    val connection by settingsViewModel.miCatalogoConnection.collectAsState()
    val accountState by settingsViewModel.miCatalogoUi.collectAsState()
    val syncViewModel: com.example.bspos.presentation.sync.SyncStatusViewModel = hiltViewModel()
    val syncStatus by syncViewModel.status.collectAsState()
    LaunchedEffect(startupSnapshot.shops) {
        settingsViewModel.hydrateShops(startupSnapshot.shops)
    }
    val availableShops = accountState.displayedShops(startupSnapshot.shops)
    val activeShopId = connection.activeShopId
    val activeShop = availableShops.firstOrNull { it.id == activeShopId }
    val accountQuota = activeShop?.quota
    val routesEnabled = settings?.routesEnabled == true
    val navController = rememberNavController()
    DisposableEffect(navController) {
        onDispose {
            if (navController.currentBackStackEntry!=null) navController.popBackStack(navController.graph.id,true)
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
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
        currentShop != null && canAccessMiCatalogoMenu(
            connection.isAdmin,
            canManageShop,
            menuPermissions,
            key,
            currentShop?.capabilities.orEmpty(),
            currentShop?.enabledMenuKeys,
            currentShop?.canManageMenuVisibility == true
        )
    }
    val drawerGroups = buildDrawerNavigation(currentShop?.navigation, canSeeMenu, connection.isAdmin, routesEnabled)
    val drawerScreens = drawerGroups.flatMap { it.screens }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var shopMenuOpen by remember { mutableStateOf(false) }
    var storePreviewUrl by remember { mutableStateOf<String?>(null) }
    var menuQuery by remember { mutableStateOf("") }
    var collapsedGroups by remember { mutableStateOf(drawerGroups.filter { it.id !in setOf("home", "sales", "account_help", "account_help_fallback") }.map { it.id }.toSet()) }
    val currentBaseRoute = currentRoute.substringBefore('?')
    val currentScreen = drawerScreens.firstOrNull { it.route == currentBaseRoute } ?: when (currentBaseRoute) {
        Screen.CatalogCreate.route -> Screen.CatalogCreate
        Screen.DecantCreate.route -> Screen.DecantCreate
        Screen.DownloadApp.route -> Screen.DownloadApp
        Screen.Credit.route -> Screen.Credit
        Screen.Catalog.route -> Screen.Services.takeIf { canSeeMenu("services") }
        else -> null
    }
    val selectedGroup = drawerGroups.firstOrNull { group -> group.screens.any { it.route == currentBaseRoute } }
        ?: drawerGroups.firstOrNull { it.id == navigationAreaId(currentBaseRoute) }
    val selectedDrawerRoute = when (currentBaseRoute) {
        Screen.CatalogCreate.route -> Screen.Catalog.route
        Screen.DecantCreate.route -> Screen.Decants.route
        Screen.DownloadApp.route -> Screen.Updates.route
        Screen.Credit.route -> Screen.POS.route
        Screen.Catalog.route -> if (canSeeMenu("products")) Screen.Catalog.route else Screen.Services.route
        else -> currentBaseRoute
    }
    val currentGroup = selectedGroup?.title ?: "Inicio"
    val currentTitle = when (currentBaseRoute) {
        Screen.Quotes.route -> Screen.Quotes.title
        else -> currentScreen?.let { selectedGroup?.label(it) ?: it.title } ?: if (currentBaseRoute == Screen.Profile.route) Screen.Profile.title else "MiCatalogo"
    }
    val routeAllowed = isNavigationRouteAllowed(currentBaseRoute, canSeeMenu, connection.isAdmin, routesEnabled)
    LaunchedEffect(currentBaseRoute, routeAllowed) {
        if (!routeAllowed) navController.navigate(Screen.Dashboard.route) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    val connectedShop = activeShop
    val businessName = connectedShop?.name ?: settings?.invoice?.businessName?.ifBlank { null } ?: "MiCatalogo"
    val drawerWidth = if (windowWidthSizeClass == WindowWidthSizeClass.Expanded) {
        360.dp
    } else {
        // Puntto keeps the drawer close to 70% of the viewport on phones.
        // Matching the proportion prevents the menu search and labels from
        // wrapping prematurely while preserving MiCatalogo's light palette.
        LocalConfiguration.current.screenWidthDp.dp * 0.72f
    }
    val menuFocus = androidx.compose.ui.platform.LocalFocusManager.current
    val menuKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val navigateTo: (Screen) -> Unit = { screen ->
        if (currentBaseRoute != screen.route) {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        menuQuery = ""
        menuFocus.clearFocus()
        menuKeyboard?.hide()
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

                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        TextButton(
                            onClick = {
                                if (availableShops.size > 1) {
                                    shopMenuOpen = true
                                } else {
                                    UiErrorBus.show("Esta es tu única tienda activa.")
                                }
                            },
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
                            availableShops.forEach { shop ->
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
                                menuQuery.isBlank() || group.title.contains(menuQuery.trim(), ignoreCase = true) || group.label(screen).contains(menuQuery.trim(), ignoreCase = true) || screen.title.contains(menuQuery.trim(), ignoreCase = true)
                            }
                            if (visibleScreens.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        collapsedGroups = if (group.id in collapsedGroups) {
                                            collapsedGroups - group.id
                                        } else {
                                            collapsedGroups + group.id
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
                                        text = if (group.id in collapsedGroups) "›" else "⌄",
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
                                visible = visibleScreens.isNotEmpty() && (group.id !in collapsedGroups || menuQuery.isNotBlank() || group == selectedGroup),
                                enter = fadeIn() + androidx.compose.animation.expandVertically(),
                                exit = fadeOut() + androidx.compose.animation.shrinkVertically()
                            ) {
                                Column(Modifier.animateContentSize()) {
                                    visibleScreens.forEach { screen ->
                                        val selected = selectedDrawerRoute == screen.route
                                        NavigationDrawerItem(
                                            label = { Text(group.label(screen), fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold) },
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
                                    storePreviewUrl = publicUrl
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
                    val bottomItems = phoneNavigation(canSeeMenu)
                    if (bottomItems.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().background(BSPOSTheme.colors.secondaryNavy).navigationBarsPadding().padding(vertical = 4.dp)) {
                                bottomItems.forEach { (label, screen) ->
                                    val selected = currentBaseRoute == screen.route
                                    val tint = if (selected) Color.White else BSPOSTheme.colors.textOnNavy.copy(alpha = .7f)
                                     Column(Modifier.weight(1f).clickable { navigateTo(screen) }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                         Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                                             Icon(screen.unselectedIcon, label, tint = tint, modifier = Modifier.size(22.dp))
                                        }
                                        Text(label, color = tint, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                                 Column(Modifier.weight(1f).clickable { scope.launch { drawerState.open() } }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                     Box(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) { Icon(Icons.Default.Menu, "Abrir menú", tint = BSPOSTheme.colors.textOnNavy.copy(alpha = .7f), modifier = Modifier.size(22.dp)) }
                                    Text("Más", color = BSPOSTheme.colors.textOnNavy.copy(alpha = .7f), fontSize = 10.sp)
                                }
                            }
                    }
                }
            },
            topBar = {
                Column {
                // The application shell owns the context header for every regular
                // module. Terminal and Cotizaciones intentionally keep their compact
                // Puntto-style header; Support is a nested conversation screen.
                 val usesCompactHeader = currentBaseRoute == Screen.POS.route
                if (currentBaseRoute != Screen.Support.route &&
                    (!usesCompactHeader || windowWidthSizeClass == WindowWidthSizeClass.Expanded)
                 ) TopAppBar(
                    expandedHeight = 56.dp,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$currentGroup / ",
                                color = BSPOSTheme.colors.textSecondary,
                                 fontSize = 11.sp,
                                 fontFamily = com.example.bspos.core.ui.theme.BSPOSFonts.Monospace,
                                 fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                            Text(
                                text = currentTitle,
                                color = BSPOSTheme.colors.textPrimary,
                                 fontSize = 11.sp,
                                 fontFamily = com.example.bspos.core.ui.theme.BSPOSFonts.Monospace,
                                 fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
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
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = BSPOSTheme.colors.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BSPOSTheme.colors.outline)
                            ) {
                                Text(
                                    quota.planLabel,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    maxLines = 1,
                                    color = BSPOSTheme.colors.textPrimary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        if (canSeeMenu("help")) TextButton(
                            onClick = {
                                if (canSeeMenu("help")) {
                                    navigateTo(Screen.Help)
                                } else {
                                    UiErrorBus.show("Tu rol no tiene permiso para abrir Ayuda.")
                                }
                            },
                            enabled = canSeeMenu("help")
                        ) {
                            Text("Ayuda", color = BSPOSTheme.colors.textSecondary, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BSPOSTheme.colors.surface,
                        titleContentColor = BSPOSTheme.colors.textPrimary,
                        navigationIconContentColor = BSPOSTheme.colors.textPrimary
                    )
                )
                com.example.bspos.presentation.sync.SyncStatusBanner(
                    status = syncStatus,
                    onRetry = syncViewModel::retry,
                    onResolve = {
                        when (syncStatus.resolutionTarget) {
                            com.example.bspos.presentation.sync.SyncResolutionTarget.DECANTS -> {
                                if (canSeeMenu("decants")) {
                                    navController.navigate(Screen.Decants.route) { launchSingleTop = true }
                                } else if (canSeeMenu("settings")) {
                                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                                } else {
                                    UiErrorBus.show("La operación de Decants requiere revisión del administrador.")
                                }
                            }
                            com.example.bspos.presentation.sync.SyncResolutionTarget.SYNC_SETTINGS -> {
                                if (canSeeMenu("settings")) {
                                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                                } else {
                                    UiErrorBus.show("La operación requiere revisión. Solicita al administrador abrir Ajustes > Sincronización.")
                                }
                            }
                            com.example.bspos.presentation.sync.SyncResolutionTarget.NONE -> {
                                if (canSeeMenu("settings")) {
                                    navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                                } else {
                                    UiErrorBus.show("La operación requiere revisión del administrador.")
                                }
                            }
                        }
                    }
                )
                }
            }
        ) { paddingValues ->
            // Keep one NavHost alive for the whole session. Recreating it when
            // currentRoute changes resets its start destination and makes drawer
            // and bottom-bar navigation appear to do nothing.
            BSPOSNavHost(
                navController = navController,
                isTablet = windowWidthSizeClass == WindowWidthSizeClass.Expanded,
                routesEnabled = routesEnabled,
                currency = settings?.currency ?: CurrencyUnit.DOP,
                showSupportOnDashboard = settings?.showSupportOnDashboard != false,
                onHideSupport = { settingsViewModel.setShowSupportOnDashboard(false) },
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
                startupSnapshot = startupSnapshot,
                onRetryStartup = onRetryStartup,
                canSeeMenu = canSeeMenu,
                onOpenMenu = { scope.launch { drawerState.open() } },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    storePreviewUrl?.let { url ->
        PublicStorePreviewDialog(
            url = url,
            onDismiss = { storePreviewUrl = null }
        )
    }
}

@Composable
private fun PublicStorePreviewDialog(
    url: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f),
            shape = BSPOSTheme.shapes.extraLarge,
            color = BSPOSTheme.colors.surface,
            tonalElevation = 6.dp
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        loadUrl(url)
                    }
                },
                update = { webView ->
                    if (webView.url != url) webView.loadUrl(url)
                }
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
    showSupportOnDashboard: Boolean = true,
    onHideSupport: () -> Unit = {},
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
    startupSnapshot: StartupSnapshot? = null,
    onRetryStartup: () -> Unit = {},
    canSeeMenu: (String) -> Boolean = { true },
    onOpenMenu: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canOpenSupport = !sellerMode || canSeeMenu("support")
    val financialMenus = setOf("finance", "expenses", "cash", "day_close").filter(canSeeMenu).toSet()
    val publicStoreUrl = shopSlug?.trim()?.takeIf { it.isNotBlank() }?.let {
        "${BuildConfig.MICATALOGO_API_BASE_URL.trimEnd('/')}/tienda/$it"
    }

    fun sharePublicStore() {
        val url = publicStoreUrl
        if (url.isNullOrBlank()) {
            UiErrorBus.show("Esta tienda todavía no tiene un enlace público.")
            return
        }
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/?text=${Uri.encode("Mira mi catálogo: $url")}")
                )
            )
        }.onFailure {
            UiErrorBus.show("No se pudo abrir WhatsApp.")
        }
    }
    fun openPublicProduct(product: com.example.bspos.domain.model.Product) {
        val slug = shopSlug?.trim().orEmpty()
        val productSlug = product.remoteProductSlug?.trim().takeUnless { it.isNullOrBlank() }
            ?: Normalizer.normalize(product.name.trim().lowercase(), Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .replace("[^a-z0-9]+".toRegex(), "-")
                .trim('-')
        if (slug.isBlank() || productSlug.isBlank()) {
            UiErrorBus.show("Este producto todavía no tiene un enlace público disponible.")
            return
        }
        val url = "${BuildConfig.MICATALOGO_API_BASE_URL.trimEnd('/')}/tienda/$slug/producto/$productSlug"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { UiErrorBus.show("No se pudo abrir el producto en la tienda.") }
    }
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
            Box(Modifier.fillMaxSize()) {
                DashboardScreen(
                onNewSale = { if (canSeeMenu("sales")) navController.navigate(Screen.POS.route) },
                onCollections = { if (canSeeMenu("collections")) navController.navigate(Screen.CreditLedger.route) },
                onInventory = { if (canSeeMenu("inventory")) navController.navigate(Screen.Inventory.route) },
                onProducts = { if (canSeeMenu("products")) navController.navigate(Screen.Catalog.route) },
                onStorefront = { if (canSeeMenu("storefront")) navController.navigate(Screen.Storefront.route) },
                onCustomers = { if (canSeeMenu("customers")) navController.navigate(Screen.Customers.route) },
                onRoutes = { if (routesEnabled) navController.navigate(Screen.Routes.route) },
                onReturns = { if (canSeeMenu("returns")) navController.navigate(Screen.Returns.route) },
                onEncargos = { if (canSeeMenu("encargos")) navController.navigate(Screen.Encargos.route) },
                onDayClose = { if (canSeeMenu("day_close")) navController.navigate(Screen.DayClose.route) },
                onPhotos = { if (canSeeMenu("photos")) navController.navigate(Screen.Photos.route) },
                onProfile = { navController.navigate(Screen.Profile.route) },
                onSupport = { if (canOpenSupport) navController.navigate(Screen.Support.route) },
                onHideSupport = onHideSupport,
                onStockFilter = { filter ->
                    if (canSeeMenu("inventory")) {
                        navController.navigate(Screen.Inventory.route)
                        navController.currentBackStackEntry?.savedStateHandle?.set("summary_stock_filter", filter)
                    }
                },
                onMovements = {
                    if (canSeeMenu("inventory")) {
                        navController.navigate(Screen.Inventory.route)
                        navController.currentBackStackEntry?.savedStateHandle?.set("summary_stock_filter", "movements")
                    }
                },
                showEncargos = canSeeMenu("encargos"),
                routesEnabled = routesEnabled && canSeeMenu("routes"),
                sellerMode = sellerMode,
                showSales = canSeeMenu("sales"),
                showCollections = canSeeMenu("collections"),
                showInventory = canSeeMenu("inventory"),
                showProducts = canSeeMenu("products"),
                shopId = shopId,
                showCustomers = canSeeMenu("customers"),
                showSupport = false,
                onAllSales = { if (canSeeMenu("sales")) navController.navigate(Screen.SalesHistory.route) },
                presentation = presentation,
                businessName = businessName,
                isExpanded = isTablet,
                startupSnapshot = startupSnapshot,
                onRetryStartup = onRetryStartup
                )
                if (canOpenSupport) {
                    SupportFloatingActionButton(
                        onClick = { navController.navigate(Screen.Support.route) },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
        composable(Screen.POS.route) {
            RestrictedMenuDestination(canSeeMenu("sales"), navController) {
                PosScreen(
                    presentation = presentation.copy(posShowCredit = presentation.posShowCredit && canSeeMenu("credit")),
                    onOpenQuotes = { if (canSeeMenu("quotes")) navController.navigate(Screen.Quotes.route) },
                    onOpenDayClose = { if (canSeeMenu("day_close")) navController.navigate(Screen.DayClose.route) },
                    onOpenCustomers = { if (canSeeMenu("customers")) navController.navigate(Screen.Customers.route) },
                    onOpenServices = { if (canSeeMenu("services")) navController.navigate(Screen.Services.route) },
                    showServicesAction = canSeeMenu("services"),
                    showQuoteAction = canSeeMenu("quotes"),
                    showDayCloseAction = canSeeMenu("day_close"),
                    showCosts = !sellerMode || canSeeMenu("finance"),
                    onOpenMenu = onOpenMenu,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.SalesHistory.route) {
            if (remoteShopAvailable) {
                // Remote sales are the source of truth for quotes converted
                // from the server. This also keeps the Android flow aligned
                // with Puntto when a quote is converted outside the local POS.
                FeatureDestination("sales", canSeeMenu("sales"), navController, canSeeMenu)
            } else {
                RestrictedMenuDestination(canSeeMenu("sales"), navController) {
                    SalesHistoryScreen(
                        onReturns = { if (canSeeMenu("returns")) navController.navigate(Screen.Returns.route) }
                    )
                }
            }
        }
        composable(Screen.Quotes.route) {
            RestrictedMenuDestination(canSeeMenu("quotes"), navController) {
                QuoteScreen(
                    onOpenSales = { if (canSeeMenu("sales")) navController.navigate(Screen.SalesHistory.route) },
                    onOpenTerminal = { if (canSeeMenu("sales")) navController.navigate(Screen.POS.route) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.Orders.route) {
            RestrictedMenuDestination(canSeeMenu("orders"), navController) {
                OrdersScreen(
                    publicStoreUrl = publicStoreUrl,
                    onShareStore = ::sharePublicStore,
                    onOpenChat = { navController.navigate(Screen.Support.route) }
                )
            }
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
            FeatureDestination("updates", canSeeMenu("updates"), navController, canSeeMenu)
        }
        composable(Screen.Containers.route) { PurchaseDestination("containers", canSeeMenu("containers"), navController) }
        composable(Screen.PurchaseInvoices.route) { PurchaseDestination("purchase_invoices", canSeeMenu("purchase_invoices"), navController) }
        composable(Screen.Photos.route) {
            RestrictedMenuDestination(canSeeMenu("photos"), navController) {
                CatalogHomeScreen(
                    isTablet = isTablet,
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("finance"),
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
                    shopSlug = shopSlug,
                    navController = navController,
                    canSeeMenu = canSeeMenu
                )
            }
        }
        composable(Screen.Services.route) {
            RestrictedMenuDestination(canSeeMenu("services"), navController) {
                CatalogHomeScreen(
                    isTablet = isTablet,
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("finance"),
                    showProductCode = productFields.isEmpty() || "sku" in productFields || "barcode" in productFields,
                    initialServiceMode = true,
                    onOpenProducts = if (canSeeMenu("products")) ({ navController.navigate(Screen.Catalog.route) { launchSingleTop = true } }) else null,
                    onOpenServices = {}
                )
            }
        }
        composable(Screen.PriceHealth.route) {
            FeatureDestination(
                feature = "price_health",
                isAllowed = canSeeMenu("price_health"),
                navController = navController,
                canSeeMenu = canSeeMenu,
                showSensitiveFinance = !sellerMode || canSeeMenu("finance")
                )
        }
        composable(Screen.AutomaticPrices.route) {
            RestrictedMenuDestination(canSeeMenu("pricing"), navController) {
                AutomaticPricesScreen(showSensitiveFinance = !sellerMode || canSeeMenu("finance"))
            }
        }
        composable(Screen.Decants.route) {
            var startDecantsTutorial by remember { mutableStateOf(false) }
            var prepareDecantsSourceId by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                val previousEntry = navController.previousBackStackEntry
                if (previousEntry?.savedStateHandle?.remove<Boolean>("start_decants_tutorial") == true) {
                    startDecantsTutorial = true
                }
                prepareDecantsSourceId = navController.previousBackStackEntry?.savedStateHandle?.remove("prepare_decants_source_id")
            }
            // Decants is an operational workspace, not a read-only feature card.
            // Keep the entry on the native flow so “Abrir botella”, “Preparar
            // decant”, “Reporte” and “Frascos” all remain actionable from the
            // same screen and the opening operation can be queued to the API.
            RestrictedMenuDestination(canSeeMenu("decants"), navController) {
                com.example.bspos.presentation.decants.DecantsScreen(
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("finance"),
                    startTutorial = startDecantsTutorial,
                    prepareSourceRemoteId = prepareDecantsSourceId
                )
            }
        }
        composable(Screen.Attributes.route) {
            RestrictedMenuDestination(canSeeMenu("attributes"), navController) {
                AttributesScreen()
            }
        }
        composable(Screen.Import.route) {
            RestrictedMenuDestination(canSeeMenu("import"), navController) {
                InventoryScreen(
                    onOpenProducts = if (canSeeMenu("products")) {{ navController.navigate(Screen.Catalog.route) }} else null,
                    onOpenQuickCreate = if (canSeeMenu("products")) {{ navController.navigate(Screen.CatalogCreate.route) }} else null,
                    onOpenPrices = if (canSeeMenu("pricing")) {{ navController.navigate(Screen.AutomaticPrices.route) }} else null,
                    onOpenStore = if (canSeeMenu("storefront")) ::openPublicProduct else null,
                    initialImport = true,
                    showCosts = !sellerMode || canSeeMenu("finance")
                )
            }
        }
        composable(Screen.InventoryAdjustments.route) {
            RestrictedMenuDestination(canSeeMenu("inventory_adjustments"), navController) {
                InventoryScreen(
                    onOpenProducts = if (canSeeMenu("products")) {{ navController.navigate(Screen.Catalog.route) }} else null,
                    onOpenPrices = if (canSeeMenu("pricing")) {{ navController.navigate(Screen.AutomaticPrices.route) }} else null,
                    onOpenStore = if (canSeeMenu("storefront")) ::openPublicProduct else null,
                    initialAdjustment = true,
                    showCosts = !sellerMode || canSeeMenu("finance")
                )
            }
        }
        composable(Screen.Partners.route) {
            RestrictedMenuDestination(canSeeMenu("partners"), navController) { PartnersScreen() }
        }
        composable(Screen.Reports.route) { FeatureDestination("reports", canSeeMenu("reports"), navController, canSeeMenu) }
        composable(Screen.Commissions.route) { FeatureDestination("commissions", canSeeMenu("commissions"), navController, canSeeMenu) }
        composable(Screen.Authorizations.route) { FeatureDestination("authorizations", canSeeMenu("authorizations"), navController, canSeeMenu) }
        composable(Screen.Accountant.route) {
            RestrictedMenuDestination(canSeeMenu("accountant"), navController) { AccountantScreen() }
        }
        composable(Screen.Updates.route) { FeatureDestination("updates", canSeeMenu("updates"), navController, canSeeMenu) }
        composable(Screen.Help.route) {
            RestrictedMenuDestination(true, navController) {
                HelpScreen(
                    onOpen = { destination ->
                        if (canOpenMenuScreen(destination, canSeeMenu)) {
                            navController.navigate(destination.route)
                        } else {
                            UiErrorBus.show("Tu rol no tiene permiso para abrir esta sección.")
                        }
                    },
                    onStartDecantsTutorial = {
                        if (canSeeMenu("decants")) {
                            navController.currentBackStackEntry?.savedStateHandle?.set("start_decants_tutorial", true)
                            navController.navigate(Screen.Decants.route)
                        } else {
                            UiErrorBus.show("Tu rol no tiene permiso para abrir Decants.")
                        }
                    }
                )
            }
        }
        composable(Screen.Practice.route) { FeatureDestination("practice", canSeeMenu("practice"), navController, canSeeMenu) }
        composable(Screen.Support.route) {
            RestrictedMenuDestination(canOpenSupport, navController) {
                SupportChatScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
        composable(Screen.Metrics.route) { FeatureDestination("metrics", canSeeMenu("metrics"), navController, canSeeMenu) }
        composable(Screen.PublicCatalog.route) { FeatureDestination("public_catalog", canSeeMenu("public_catalog"), navController, canSeeMenu) }
        composable(Screen.Categories.route) {
            RestrictedMenuDestination(canSeeMenu("products"), navController) {
                CatalogSettingsScreen()
            }
        }
        composable(
            route = "${Screen.ShopSettings.route}?section={section}",
            arguments = listOf(navArgument("section") {
                type = NavType.StringType
                defaultValue = "General"
            })
        ) { entry ->
            RestrictedMenuDestination(canSeeMenu("shop_settings"), navController) {
                ShopSettingsScreen(initialSection = entry.arguments?.getString("section"))
            }
        }
        composable(Screen.Team.route) {
            RestrictedMenuDestination(canSeeMenu("sellers"), navController) { SettingsScreen(teamOnly = true) }
        }
        composable(Screen.Catalog.route) {
            RestrictedMenuDestination(canSeeMenu("products") || canSeeMenu("services"), navController) {
                CatalogHomeScreen(
                    isTablet = isTablet,
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("finance"),
                    showProductCode = productFields.isEmpty() || "sku" in productFields || "barcode" in productFields,
                    initialServiceMode = !canSeeMenu("products"),
                    onOpenProducts = if (canSeeMenu("products")) ({}) else null,
                    onOpenServices = if (canSeeMenu("services")) ({ navController.navigate(Screen.Services.route) { launchSingleTop = true } }) else null,
                    onOpenDecants = if (canSeeMenu("decants")) ({ sourceId ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("prepare_decants_source_id", sourceId)
                        navController.navigate(Screen.Decants.route) { launchSingleTop = true }
                    }) else null
                )
            }
        }
        composable(Screen.CatalogCreate.route) {
            RestrictedMenuDestination(canSeeMenu("products"), navController) {
                CatalogHomeScreen(
                    isTablet = isTablet,
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("finance"),
                    showProductCode = productFields.isEmpty() || "sku" in productFields || "barcode" in productFields,
                    startWithForm = true,
                    onOpenDecants = if (canSeeMenu("decants")) ({ sourceId ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("prepare_decants_source_id", sourceId)
                        navController.navigate(Screen.Decants.route) { launchSingleTop = true }
                    }) else null
                )
            }
        }
        composable(Screen.DecantCreate.route) {
            RestrictedMenuDestination(canSeeMenu("decants"), navController) {
                CatalogHomeScreen(
                    isTablet = isTablet,
                    presentation = presentation,
                    showCost = !sellerMode || canSeeMenu("finance"),
                    showProductCode = productFields.isEmpty() || "sku" in productFields || "barcode" in productFields,
                    initialDecantMode = true,
                    startWithForm = true
                )
            }
        }
        composable(Screen.Customers.route) {
            RestrictedMenuDestination(canSeeMenu("customers"), navController) {
                CustomerScreen(presentation = presentation.copy(customersShowCredit = presentation.customersShowCredit && (canSeeMenu("credit") || canSeeMenu("collections"))))
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
        composable(Screen.Suppliers.route) {
            PurchaseDestination("suppliers", canSeeMenu("suppliers"), navController)
        }
        composable(Screen.Inventory.route) { entry ->
            RestrictedMenuDestination(canSeeMenu("inventory"), navController) {
                InventoryScreen(
                    onOpenProducts = if (canSeeMenu("products")) {{ navController.navigate(Screen.Catalog.route) }} else null,
                    onOpenQuickCreate = if (canSeeMenu("products")) {{ navController.navigate(Screen.CatalogCreate.route) }} else null,
                    onOpenPrices = if (canSeeMenu("pricing")) {{ navController.navigate(Screen.AutomaticPrices.route) }} else null,
                    onOpenImport = if (canSeeMenu("import")) {{ navController.navigate(Screen.Import.route) }} else null,
                    onOpenStore = if (canSeeMenu("storefront")) ::openPublicProduct else null,
                    initialStockFilter = entry.savedStateHandle.get<String>("summary_stock_filter"),
                    showCosts = !sellerMode || canSeeMenu("finance")
                )
            }
        }
        composable(Screen.Collections.route) {
            RestrictedMenuDestination(canSeeMenu("collections"), navController) { CollectionScreen() }
        }
        composable(Screen.CreditLedger.route) {
            if (canSeeMenu("collections")) {
                RestrictedMenuDestination(true, navController) { CollectionScreen() }
            } else {
                FeatureDestination("credit", canSeeMenu("credit"), navController, canSeeMenu)
            }
        }
        composable(Screen.Credit.route) {
            RestrictedMenuDestination(canSeeMenu("sales") && canSeeMenu("credit"), navController) {
                PosScreen(creditOnly = true, presentation = presentation.copy(posShowCredit = true),
                    showQuoteAction = false, showDayCloseAction = false, showServicesAction = false,
                    showCosts = !sellerMode || canSeeMenu("finance"))
            }
        }
        composable(Screen.Cash.route) {
            RestrictedMenuDestination(canSeeMenu("cash"), navController) {
                // Cash is a server-owned register. POS transactions are queued
                // locally when offline and synchronized when connectivity returns;
                // the local register is intentionally not exposed to users.
                FinanceScreen(initialTab = 4, allowedMenuKeys = financialMenus)
            }
        }
        composable(Screen.Returns.route) {
            RestrictedMenuDestination(canSeeMenu("returns"), navController) { ReturnScreen() }
        }
        composable(Screen.Loads.route) {
            PurchaseDestination("loads", canSeeMenu("loads"), navController)
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
            RestrictedMenuDestination(canSeeMenu("finance"), navController) {
                FinanceScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCash = if (canSeeMenu("cash")) ({ navController.navigate(Screen.Cash.route) }) else null,
                    onNavigateToFiscalReport = if (canSeeMenu("reports")) ({ navController.navigate(Screen.Reports.route) }) else null,
                    onNavigateToDistributions = if (canSeeMenu("partners")) ({ navController.navigate(Screen.Partners.route) }) else null,
                    onNavigateToReceivables = if (canSeeMenu("collections")) ({ navController.navigate(Screen.CreditLedger.route) }) else null,
                    canMutateExpenses = canSeeMenu("expenses"),
                    canMutateCash = canSeeMenu("cash"),
                    allowedMenuKeys = financialMenus
                )
            }
        }
        composable(Screen.Expenses.route) {
            RestrictedMenuDestination(canSeeMenu("expenses"), navController) {
                FinanceScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCash = if (canSeeMenu("cash")) ({ navController.navigate(Screen.Cash.route) }) else null,
                    onNavigateToFiscalReport = if (canSeeMenu("reports")) ({ navController.navigate(Screen.Reports.route) }) else null,
                    onNavigateToDistributions = if (canSeeMenu("partners")) ({ navController.navigate(Screen.Partners.route) }) else null,
                    onNavigateToReceivables = if (canSeeMenu("collections")) ({ navController.navigate(Screen.CreditLedger.route) }) else null,
                    initialTab = 3,
                    canMutateExpenses = true,
                    canMutateCash = canSeeMenu("cash"),
                    allowedMenuKeys = financialMenus
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
    shopSlug: String?,
    navController: androidx.navigation.NavHostController,
    canSeeMenu: (String) -> Boolean
) {
    val context = LocalContext.current
    val settingsViewModel: ShopSettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.state.collectAsState()
    var previewOpen by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf("Resumen") }
    var editableName by remember(shopName) { mutableStateOf(shopName.orEmpty()) }
    val baseUrl = BuildConfig.MICATALOGO_API_BASE_URL.trimEnd('/')
    val publicUrl = shopSlug?.trim()?.takeIf { it.isNotBlank() }?.let { "$baseUrl/tienda/$it" }
    val sections = listOf("Resumen", "Apariencia", "Contacto y horario", "Catálogo", "Vitrinas", "Anuncios", "Google")

    LaunchedEffect(shopId) {
        if (!shopId.isNullOrBlank()) settingsViewModel.load()
    }

    fun openNative(screen: Screen) {
        val allowed = when (screen) {
            Screen.Catalog -> canSeeMenu("products")
            Screen.Orders -> canSeeMenu("orders")
            Screen.Metrics -> canSeeMenu("metrics")
            Screen.ShopSettings -> canSeeMenu("shop_settings")
            else -> true
        }
        if (!allowed) {
            UiErrorBus.show("Tu rol no tiene permiso para abrir esta sección.")
            return
        }
        navController.navigate(screen.route) {
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openShopSettings(section: String? = null) {
        if (!canSeeMenu("shop_settings")) {
            UiErrorBus.show("Tu rol no tiene permiso para editar la tienda.")
            return
        }
        navController.navigate(Screen.ShopSettings.routeFor(section)) {
            launchSingleTop = true
            restoreState = true
        }
    }

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

    fun copyStoreLink() {
        if (publicUrl.isNullOrBlank()) {
            UiErrorBus.show("Esta tienda todavía no tiene un enlace público.")
            return
        }
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Enlace de Mi tienda", publicUrl))
        UiErrorBus.show("Enlace de la tienda copiado.")
    }

    fun shareStoreLink() {
        if (publicUrl.isNullOrBlank()) {
            UiErrorBus.show("Esta tienda todavía no tiene un enlace público.")
            return
        }
        openUrl(
            "https://wa.me/?text=${Uri.encode("Mira mi catálogo: $publicUrl")}",
            "No se pudo abrir WhatsApp."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BSPOSTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
    ) {
        Text("Todo lo de tu tienda en línea: compártela, decide cómo se ve y mira lo que le falta.", color = BSPOSTheme.colors.textSecondary)
        Card(
            shape = BSPOSTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editableName,
                        onValueChange = { editableName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Nombre de la tienda") },
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            val current = settingsState.settings
                            if (current != null && editableName.trim().isNotBlank()) {
                                settingsViewModel.save(
                                    ShopSettingsUpdateDto(
                                        name = editableName.trim(),
                                        businessType = current.businessType,
                                        description = current.description,
                                        address = current.address,
                                        mapsUrl = current.mapsUrl.ifBlank { null },
                                        instagram = current.instagram.ifBlank { null },
                                        whatsappCountryCode = current.whatsappCountryCode,
                                        whatsappNumber = current.whatsappNumber,
                                        offersShipping = current.offersShipping,
                                        primaryColor = current.primaryColor,
                                        secondaryColor = current.secondaryColor,
                                        businessHours = current.businessHours,
                                        operationalSettings = current.operationalSettings
                                    )
                                )
                            } else {
                                UiErrorBus.show("No se pudo cargar la configuración de la tienda.")
                            }
                        },
                        enabled = !settingsState.saving && editableName.trim().isNotBlank() && editableName.trim() != settingsState.settings?.name?.trim()
                    ) { Text(if (settingsState.saving) "Guardando…" else "Guardar") }
                }
                Text(
                    publicUrl ?: "Configura el enlace público de tu tienda desde el panel web.",
                    color = BSPOSTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = { if (publicUrl.isNullOrBlank()) UiErrorBus.show("Esta tienda todavía no tiene un enlace público.") else previewOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ver mi tienda")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = publicUrl ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Enlace público") }
                    )
                    TextButton(onClick = ::copyStoreLink, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text("Copiar")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::shareStoreLink, modifier = Modifier.weight(1f)) { Text("Enviar por WhatsApp") }
                    OutlinedButton(onClick = { openNative(Screen.Metrics) }, modifier = Modifier.weight(1f)) { Text("QR y métricas") }
                }
                OutlinedButton(onClick = { openNative(Screen.ShopSettings) }, modifier = Modifier.fillMaxWidth()) { Text("Configuración avanzada") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            sections.forEach { section ->
                FilterChip(selected = activeSection == section, onClick = { activeSection = section }, label = { Text(section) })
            }
        }

        when (activeSection) {
            "Resumen" -> {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.primaryLight)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Text("Tu tienda está abierta y recibe pedidos", fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                        Text("Esta es la que compartes. Funciona en cualquier teléfono, sin instalar nada.", color = BSPOSTheme.colors.textSecondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { openNative(Screen.Catalog) }, label = { Text("Productos") })
                            AssistChip(onClick = { openNative(Screen.Orders) }, label = { Text("Pedidos") })
                        }
                    }
                }
                Text("Para que tu tienda se vea bien", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("Cada paso se abre en el lugar correcto para completarlo.", color = BSPOSTheme.colors.textSecondary)
                StorefrontGuideCard("1", "Sube tu logo", "Es lo primero que ve el cliente al abrir.", "Apariencia") { openShopSettings("Apariencia") }
                StorefrontGuideCard("2", "Pon tu WhatsApp", "Sin él, el cliente no tiene cómo preguntarte.", "Contacto y horario") { openShopSettings("Contacto y horario") }
                StorefrontGuideCard("3", "Completa el catálogo", "Publica productos con foto, precio y existencia.", "Catálogo") { openNative(Screen.Catalog) }
                StorefrontGuideCard("4", "Define tu horario", "Para que sepan cuándo pueden pasar o escribirte.", "Contacto y horario") { openShopSettings("Contacto y horario") }
                StorefrontGuideCard("5", "Enlaza tus redes", "Tu tienda y tus redes se apuntan el uno al otro.", "Negocio") { openShopSettings("Negocio") }
                StorefrontGuideCard("6", "Revisa cómo se ve", "Abre la vitrina pública y prueba el carrito como cliente.", "Ver mi tienda") { previewOpen = publicUrl != null }
            }
            "Apariencia" -> StorefrontSectionCard("Apariencia", "Configura logo, portada, colores y la información visual que verán tus clientes.", "Abrir personalización") { openShopSettings("Apariencia") }
            "Contacto y horario" -> StorefrontSectionCard("Contacto y horario", "Agrega WhatsApp, dirección, horario y los datos que ayudan al cliente a visitarte o escribirte.", "Configurar contacto y horario") { openShopSettings("Contacto y horario") }
            "Catálogo" -> StorefrontSectionCard("Catálogo", "Publica productos con foto, precio, existencia y categorías fáciles de explorar.", "Ver productos") { openNative(Screen.Catalog) }
            "Vitrinas" -> StorefrontSectionCard("Vitrinas", "Organiza cómo se presentan tus productos, mayorista y decants.", "Abrir vitrinas") { openShopSettings("Vitrinas") }
            "Anuncios" -> StorefrontSectionCard("Anuncios", "Configura los píxeles de Meta, TikTok y Google Analytics para medir tus campañas.", "Configurar anuncios") { openShopSettings("Anuncios") }
            "Google" -> StorefrontSectionCard("Google", "Completa la presencia pública y la etiqueta de verificación de Search Console.", "Configurar Google") { openShopSettings("Google") }
        }
        Text("Los cambios se guardan en la tienda activa y se reflejan en la vitrina pública, el POS y los pedidos.", color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
    if (previewOpen && publicUrl != null) {
        PublicStorePreviewDialog(url = publicUrl, onDismiss = { previewOpen = false })
    }
}

@Composable
private fun StorefrontSectionCard(title: String, description: String, action: String, onAction: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(description, color = BSPOSTheme.colors.textSecondary)
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
private fun StorefrontGuideCard(
    number: String,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BSPOSTheme.colors.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(BSPOSTheme.colors.primaryLight),
                contentAlignment = Alignment.Center
            ) { Text(number, color = BSPOSTheme.colors.primary, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.ExtraBold, color = BSPOSTheme.colors.textPrimary)
                Text(body, color = BSPOSTheme.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

private fun canOpenMenuScreen(
    screen: Screen,
    canSeeMenu: (String) -> Boolean
): Boolean {
    if (screen == Screen.Help) return true
    if (screen == Screen.Catalog) return canSeeMenu("products") || canSeeMenu("services")
    if (screen == Screen.CreditLedger) return canSeeMenu("credit") || canSeeMenu("collections")
    val requiredMenu = when (screen) {
        Screen.Dashboard, Screen.Profile -> null
        Screen.POS, Screen.SalesHistory -> "sales"
        Screen.Quotes -> "quotes"
        Screen.Orders -> "orders"
        Screen.Encargos -> "encargos"
        Screen.Shipments -> "shipments"
        Screen.DayClose -> "day_close"
        Screen.DownloadApp, Screen.Updates -> "updates"
        Screen.Containers -> "containers"
        Screen.Loads -> "loads"
        Screen.Suppliers -> "suppliers"
        Screen.PurchaseInvoices -> "purchase_invoices"
        Screen.Inventory -> "inventory"
        Screen.Photos -> "photos"
        Screen.Storefront -> "storefront"
        Screen.Services -> "services"
        Screen.PriceHealth -> "price_health"
        Screen.AutomaticPrices -> "pricing"
        Screen.Decants, Screen.DecantCreate -> "decants"
        Screen.Attributes -> "attributes"
        Screen.Import -> "import"
        Screen.CreditLedger, Screen.Collections -> "collections"
        Screen.Customers -> "customers"
        Screen.Finance -> "finance"
        Screen.InventoryAdjustments -> "inventory_adjustments"
        Screen.Expenses -> "expenses"
        Screen.Partners -> "partners"
        Screen.Reports -> "reports"
        Screen.Commissions -> "commissions"
        Screen.Authorizations -> "authorizations"
        Screen.ShopSettings -> "shop_settings"
        Screen.Team -> "sellers"
        Screen.Accountant -> "accountant"
        Screen.Help -> "help"
        Screen.Practice -> "practice"
        Screen.Support -> "support"
        Screen.Metrics -> "metrics"
        Screen.PublicCatalog -> "public_catalog"
        Screen.Categories -> return canSeeMenu("products") || canSeeMenu("services") || canSeeMenu("decants")
        Screen.Catalog, Screen.CatalogCreate -> "products"
        Screen.Settings -> "settings"
        Screen.Cash -> "cash"
        Screen.Returns -> "returns"
        Screen.Routes, Screen.RouteLoads -> "routes"
        Screen.Printers -> "printers"
        Screen.Credit -> "collections"
        Screen.AdminShops -> null
    }
    return requiredMenu == null || canSeeMenu(requiredMenu)
}

@Composable
private fun FeatureDestination(
    feature: String,
    isAllowed: Boolean,
    navController: androidx.navigation.NavHostController,
    canSeeMenu: (String) -> Boolean,
    showSensitiveFinance: Boolean = true
) {
    RestrictedMenuDestination(isAllowed, navController) {
        FeatureModuleScreen(
            feature = feature,
            showSensitiveFinance = showSensitiveFinance,
            onAction = { label ->
                navigateFeatureAction(label, navController, feature, canSeeMenu)
            }
        )
    }
}

@Composable
private fun PurchaseDestination(
    feature: String,
    isAllowed: Boolean,
    navController: androidx.navigation.NavHostController
) {
    RestrictedMenuDestination(isAllowed, navController) {
        PurchaseModuleScreen(feature = feature)
    }
}

private fun navigateFeatureAction(
    label: String,
    navController: androidx.navigation.NavHostController,
    currentFeature: String? = null,
    canSeeMenu: (String) -> Boolean = { true }
): Boolean {
    val normalized = label.trim().lowercase()
    if (currentFeature == "support" && normalized in setOf("nueva solicitud", "contactar soporte")) {
        // The API supplies the canonical web form URL. Do not navigate back
        // to the same read-only support module and appear to do nothing.
        return false
    }
    val target = when (normalized) {
        "ir al punto de venta", "ir a terminal", "explorar terminal", "ver terminal", "ir a punto de venta" -> Screen.POS
        "abrir mi tienda" -> Screen.Storefront
        "ver pedidos" -> Screen.Orders
        "ver inventario", "abrir inventario", "explorar inventario", "ver inventario compartido", "ver lotes y costos fifo" -> Screen.Inventory
        "abrir botella" -> Screen.Decants
        "preparar decant" -> Screen.DecantCreate
        "ver caja", "abrir caja" -> Screen.Cash
        "ver ganancias", "ver ganancias y resumen" -> Screen.Finance
        "ver reportes" -> Screen.Reports
        "ver clientes y cobros", "gestionar clientes" -> Screen.Customers
        "ver productos", "crear producto o servicio" -> Screen.Catalog
        "nuevo servicio" -> Screen.Services
        "crear presentación decant" -> Screen.DecantCreate
        "precios automáticos", "abrir precios automáticos" -> Screen.AutomaticPrices
        "precios y costos" -> Screen.AutomaticPrices
        "administrar reglas" -> Screen.AutomaticPrices
        "abrir importador" -> Screen.Import
        "abrir métricas completas", "ver estadísticas", "ver qr y métricas" -> Screen.Metrics
        "descargar qr" -> Screen.Metrics
        "administrar equipo", "administrar vendedores" -> Screen.Team
        "ver comisiones" -> Screen.Commissions
        "abrir métricas" -> Screen.Metrics
        "configuración de tienda", "configurar apariencia", "editar configuración" -> Screen.ShopSettings
        "abrir catálogo público" -> Screen.PublicCatalog
        "ver mi tienda" -> Screen.Storefront
        "registrar gasto" -> Screen.Expenses
        "atributos" -> Screen.Attributes
        "contactar soporte", "nueva solicitud" -> Screen.Support
        "volver al resumen" -> Screen.Dashboard
        else -> null
    } ?: return false
    if (!canOpenMenuScreen(target, canSeeMenu)) {
        UiErrorBus.show("Tu rol no tiene permiso para abrir esta sección.")
        return false
    }
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
