package locked.`in`.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import locked.`in`.ui.screen.detail.DetailScreen
import locked.`in`.ui.screen.home.HomeScreen
import locked.`in`.ui.screen.search.SearchScreen
import locked.`in`.ui.screen.sessions.SessionDetailScreen
import locked.`in`.ui.screen.sessions.SessionHistoryScreen

@Composable
fun AppNavigation(
    deepLinkSessionId: String? = null,
    viewModel: AppNavigationViewModel = hiltViewModel()
) {
    val startDestination by viewModel.startDestination.collectAsState()

    if (startDestination == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()

    // Handle deep link from digest notification
    LaunchedEffect(deepLinkSessionId) {
        if (deepLinkSessionId != null) {
            navController.navigate(SessionDetailRoute(deepLinkSessionId))
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToSearch = { navController.navigate(SearchRoute) },
                onNavigateToSessions = { navController.navigate(SessionHistoryRoute) },
                onNavigateToDetail = { id -> navController.navigate(DetailRoute(id)) }
            )
        }
        composable<SessionHistoryRoute> {
            SessionHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { sessionId ->
                    navController.navigate(SessionDetailRoute(sessionId))
                }
            )
        }
        composable<SessionDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SessionDetailRoute>()
            SessionDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNotificationDetail = { id -> navController.navigate(DetailRoute(id)) }
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id -> navController.navigate(DetailRoute(id)) }
            )
        }
        composable<DetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DetailRoute>()
            DetailScreen(
                notificationId = route.notificationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
