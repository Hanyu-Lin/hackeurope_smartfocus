package locked.`in`.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
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

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    DIGEST("Digest", Icons.Default.Summarize),
    SEARCH("Search", Icons.Default.Search)
}

@Composable
fun AppNavigation(
    startOnboarding: Boolean = false
) {
    val navController = rememberNavController()
    val startDestination: Any = if (startOnboarding) OnboardingRoute else MainRoute

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<MainRoute> {
            MainScaffold(
                onNavigateToFocusModeDetail = { modeId ->
                    navController.navigate(FocusModeDetailRoute(modeId))
                },
                onNavigateToNotificationDetail = { id ->
                    navController.navigate(NotificationDetailRoute(id))
                }
            )
        }
        composable<FocusModeDetailRoute> {
            FocusModeDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<NotificationDetailRoute> {
            NotificationDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<OnboardingRoute> {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(MainRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    onNavigateToFocusModeDetail: (String) -> Unit,
    onNavigateToNotificationDetail: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = MainTab.entries

    val title = when (tabs[selectedTab]) {
        MainTab.HOME -> "SmartFocus"
        MainTab.DIGEST -> "Session Digest"
        MainTab.SEARCH -> "Notifications"
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (tabs[selectedTab]) {
            MainTab.HOME -> HomeScreen(
                onNavigateToFocusModeDetail = onNavigateToFocusModeDetail,
                contentPadding = padding
            )
            MainTab.DIGEST -> DigestScreen(
                onNavigateToDetail = onNavigateToNotificationDetail,
                contentPadding = padding
            )
            MainTab.SEARCH -> SearchScreen(
                onNavigateToDetail = onNavigateToNotificationDetail,
                contentPadding = padding
            )
        }
    }
}
