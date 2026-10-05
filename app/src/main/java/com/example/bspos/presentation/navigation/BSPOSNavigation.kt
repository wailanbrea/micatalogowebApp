package com.example.bspos.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Inicio",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object POS : Screen(
        route = "pos",
        title = "Ventas",
        selectedIcon = Icons.Filled.ShoppingCart,
        unselectedIcon = Icons.Outlined.ShoppingCart
    )

    data object Catalog : Screen(
        route = "catalog",
        title = "Productos",
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2
    )

    data object Customers : Screen(
        route = "customers",
        title = "Clientes",
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People
    )

    data object Routes : Screen(
        route = "routes",
        title = "Rutas",
        selectedIcon = Icons.Filled.Route,
        unselectedIcon = Icons.Outlined.Route
    )

    data object More : Screen(
        route = "more",
        title = "Más",
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz
    )

    data object Suppliers : Screen("suppliers", "Proveedores", Icons.Filled.People, Icons.Outlined.People)
    data object Inventory : Screen("inventory", "Inventario", Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
    data object Collections : Screen("collections", "Cobros", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong)
    data object Credit : Screen("credit", "Credito", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet)
    data object Cash : Screen("cash", "Caja", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet)
    data object Returns : Screen("returns", "Devoluciones", Icons.Filled.Replay, Icons.Outlined.Replay)
    data object RouteLoads : Screen("route_loads", "Cargas", Icons.Filled.LocalShipping, Icons.Outlined.LocalShipping)
    data object Printers : Screen("printers", "Impresoras", Icons.Filled.Print, Icons.Filled.Print)
    data object Finance : Screen("finance", "Finanzas", Icons.Filled.BarChart, Icons.Filled.BarChart)
    data object Expenses : Screen("expenses", "Gastos", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong)

    data object Settings : Screen(
        route = "settings",
        title = "Ajustes",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    data object AdminShops : Screen(
        route = "admin_shops",
        title = "Todas las tiendas",
        selectedIcon = Icons.Filled.Store,
        unselectedIcon = Icons.Filled.Store
    )

    data object Profile : Screen(
        route = "profile",
        title = "Mi perfil",
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle
    )
}
