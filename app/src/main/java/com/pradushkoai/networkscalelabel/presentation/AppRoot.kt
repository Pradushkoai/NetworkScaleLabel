package com.pradushkoai.networkscalelabel.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pradushkoai.networkscalelabel.R
import com.pradushkoai.networkscalelabel.presentation.screens.connection.ConnectionScreen
import com.pradushkoai.networkscalelabel.presentation.scales.ScalesViewModel
import com.pradushkoai.networkscalelabel.presentation.screens.products.ProductsScreen

/** Маршруты вкладок. */
private const val ROUTE_CONNECTION = "connection"
private const val ROUTE_PRODUCTS = "products"

/**
 * Корень приложения: две вкладки с нижней навигацией —
 * «Подключение» (параметры, подключение и поиск весов) и
 * «Товары» (чтение базы ПЛУ, список и детали).
 *
 * ViewModel одна на обе вкладки (activity-scoped), поэтому подключение
 * и выгруженные товары переживают переключение вкладок.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: ScalesViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    // Одноразовые события показываются в одном месте — здесь.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (currentRoute == ROUTE_PRODUCTS) {
                                R.string.tab_products
                            } else {
                                R.string.tab_connection
                            },
                        ),
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == ROUTE_CONNECTION,
                    onClick = { navController.navigateTab(ROUTE_CONNECTION) },
                    icon = { Icon(Icons.Filled.Lan, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_connection)) },
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_PRODUCTS,
                    onClick = { navController.navigateTab(ROUTE_PRODUCTS) },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_products)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_CONNECTION,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(ROUTE_CONNECTION) {
                ConnectionScreen(viewModel = viewModel)
            }
            composable(ROUTE_PRODUCTS) {
                ProductsScreen(viewModel = viewModel)
            }
        }
    }
}

/** Переход между вкладками с сохранением состояния каждой (стандартный паттерн). */
private fun androidx.navigation.NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
