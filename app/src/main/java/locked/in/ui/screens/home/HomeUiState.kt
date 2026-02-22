package locked.`in`.ui.screens.home

import locked.`in`.domain.model.FocusMode

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val focusModes: List<FocusMode>,
        val activeModes: List<FocusMode>,
        val timerEndTimes: Map<String, Long> = emptyMap()
    ) : HomeUiState()
}
