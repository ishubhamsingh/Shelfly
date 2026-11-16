package dev.ishubhamsingh.shelfly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import dev.ishubhamsingh.shelfly.ui.detail.ItemDetailScreen
import dev.ishubhamsingh.shelfly.ui.form.ItemFormScreen
import dev.ishubhamsingh.shelfly.ui.home.HomeScreen
import dev.ishubhamsingh.shelfly.ui.onboarding.OnboardingScreen
import dev.ishubhamsingh.shelfly.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Form : Screen("form?itemId={itemId}&prefill={prefill}") {
        fun createRoute(itemId: String? = null, prefill: String? = null): String {
            val params = buildList {
                if (itemId != null) add("itemId=$itemId")
                // Uri.encode, NOT URLEncoder: URLEncoder applies
                // application/x-www-form-urlencoded rules, where a space becomes "+".
                // Navigation percent-decodes the argument, and per RFC 3986 a literal
                // "+" in a URI is just a plus sign — so every space in the prefill
                // survived as "+" and was saved to the database that way
                // ("Diet+Coke", "130+ml"). Uri.encode emits %20, which round-trips.
                if (prefill != null) add("prefill=${Uri.encode(prefill)}")
            }
            return if (params.isEmpty()) "form" else "form?${params.joinToString("&")}"
        }
    }
    data object Detail : Screen("detail/{itemId}") {
        fun createRoute(itemId: String) = "detail/$itemId"
    }
    data object Settings : Screen("settings")
}

/** Back-stack result key: the form saved an item (as opposed to being cancelled). */
private const val KEY_ITEM_SAVED = "item_saved"

@Composable
fun ShelflyNavHost(
    startDestination: String,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier,
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) { entry ->
            // Set by the form on a successful save so Home can tear down the agent's
            // confirm card. Without it, returning from a save looks identical to
            // returning from a cancel.
            val itemSaved by entry.savedStateHandle
                .getStateFlow(KEY_ITEM_SAVED, false)
                .collectAsStateWithLifecycle()

            HomeScreen(
                onNavigateToForm   = { prefill -> navController.navigate(Screen.Form.createRoute(prefill = prefill)) },
                onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                itemSavedFromForm  = itemSaved,
                onItemSavedHandled = { entry.savedStateHandle[KEY_ITEM_SAVED] = false },
            )
        }

        composable(
            route = Screen.Form.route,
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("prefill") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            ItemFormScreen(
                itemId       = itemId,
                onNavigateUp = { navController.popBackStack() },
                onSaved      = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set(KEY_ITEM_SAVED, true)
                    navController.popBackStack()
                },
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
