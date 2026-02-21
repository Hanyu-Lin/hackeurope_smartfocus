package locked.`in`.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import locked.`in`.ui.screens.digest.DigestScreen
import locked.`in`.ui.screens.focusmodedetail.FocusModeDetailScreen
import locked.`in`.ui.screens.home.HomeScreen
import locked.`in`.ui.screens.notificationdetail.NotificationDetailScreen
import locked.`in`.ui.screens.onboarding.OnboardingScreen
import locked.`in`.ui.screens.search.SearchScreen
import locked.`in`.ui.screens.settings.SettingsScreen

@Composable
fun AppNavigation(
    startOnboarding: Boolean = false
) {
    val navController = rememberNavController()
    val startDestination: Any = if (startOnboarding) OnboardingRoute else HomeRoute

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToSearch = { navController.navigate(SearchRoute) },
                onNavigateToFocusModeDetail = { modeId ->
                    navController.navigate(FocusModeDetailRoute(modeId))
                },
                onNavigateToDigest = { navController.navigate(DigestRoute) },
                onNavigateToSettings = { navController.navigate(SettingsRoute) }
            )
        }
        composable<FocusModeDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FocusModeDetailRoute>()
            FocusModeDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<DigestRoute> {
            DigestScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id -> navController.navigate(NotificationDetailRoute(id)) }
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id -> navController.navigate(NotificationDetailRoute(id)) }
            )
        }
        composable<NotificationDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<NotificationDetailRoute>()
            NotificationDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(HomeRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
