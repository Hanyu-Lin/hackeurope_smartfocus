package locked.`in`.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SessionHistoryRoute

@Serializable
data class SessionDetailRoute(val sessionId: String)

@Serializable
data object SearchRoute

@Serializable
data class DetailRoute(val notificationId: Long)
