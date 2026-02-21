package locked.`in`.ui.screen.home

import locked.`in`.data.local.entity.FocusSessionEntity
import locked.`in`.data.local.entity.NotificationEntity

data class HomeStats(
    val allowedCount: Int = 0,
    val suppressedCount: Int = 0
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Active(
        val notifications: List<NotificationEntity> = emptyList(),
        val stats: HomeStats = HomeStats(),
        val isListenerEnabled: Boolean = false
    ) : HomeUiState
    data class Summary(
        val session: FocusSessionEntity,
        val hasSessionHistory: Boolean = false
    ) : HomeUiState
    data class Idle(
        val isListenerEnabled: Boolean = false,
        val hasSessionHistory: Boolean = false
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
