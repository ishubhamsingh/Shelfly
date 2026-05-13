package dev.ishubhamsingh.shelfly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.ishubhamsingh.shelfly.ui.detail.ItemDetailScreen
import dev.ishubhamsingh.shelfly.ui.form.ItemFormScreen
import dev.ishubhamsingh.shelfly.ui.home.HomeScreen
import dev.ishubhamsingh.shelfly.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Form : Screen("form?itemId={itemId}") {
        fun createRoute(itemId: String? = null) =
            if (itemId != null) "form?itemId=$itemId" else "form"
    }
    data object Detail : Screen("detail/{itemId}") {
        fun createRoute(itemId: String) = "detail/$itemId"
    }
    data object Settings : Screen("settings")
}

@Composable
fun ShelflyNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToForm   = { navController.navigate(Screen.Form.createRoute()) },
                onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(
            route = Screen.Form.route,
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            ItemFormScreen(
                itemId = itemId,
                onNavigateUp = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val itemId = requireNotNull(backStackEntry.arguments?.getString("itemId"))
            ItemDetailScreen(
                itemId = itemId,
                onNavigateUp = { navController.popBackStack() },
                onNavigateToEdit = { navController.navigate(Screen.Form.createRoute(itemId)) },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateUp = { navController.popBackStack() })
        }
    }
}
