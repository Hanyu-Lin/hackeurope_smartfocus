package locked.`in`.ui.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import locked.`in`.data.local.entity.FocusSessionEntity
import locked.`in`.data.repository.SessionRepository
import javax.inject.Inject

sealed interface SessionHistoryUiState {
    data object Loading : SessionHistoryUiState
    data class Success(val sessions: List<FocusSessionEntity>) : SessionHistoryUiState
}

@HiltViewModel
class SessionHistoryViewModel @Inject constructor(
    sessionRepository: SessionRepository
) : ViewModel() {

    val uiState: StateFlow<SessionHistoryUiState> = sessionRepository.getAll()
        .map { sessions -> SessionHistoryUiState.Success(sessions) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionHistoryUiState.Loading
        )
}
