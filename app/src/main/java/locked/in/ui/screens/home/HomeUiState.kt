package locked.`in`.ui.screens.home

import locked.`in`.domain.model.FocusMode

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val focusModes: List<FocusMode>,
        val activeMode: FocusMode?,
        val recentNotificationCount: Int,
        val suppressedCount: Int = 0,
        val allowedCount: Int = 0,
        val bundledCount: Int = 0
    ) : HomeUiState()
}
