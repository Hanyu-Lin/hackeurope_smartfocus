package locked.`in`.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object MainRoute
@Serializable data object HomeRoute
@Serializable data class FocusModeDetailRoute(val modeId: String)
@Serializable data object DigestRoute
@Serializable data object SearchRoute
@Serializable data class NotificationDetailRoute(val notificationId: String)
@Serializable data object OnboardingRoute
