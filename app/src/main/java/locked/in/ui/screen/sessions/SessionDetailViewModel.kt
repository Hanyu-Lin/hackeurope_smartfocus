package locked.`in`.ui.screen.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import locked.`in`.data.local.entity.FocusSessionEntity
import locked.`in`.data.local.entity.NotificationEntity
import locked.`in`.data.repository.NotificationRepository
import locked.`in`.data.repository.SessionRepository
import javax.inject.Inject

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState
    data class Success(
        val session: FocusSessionEntity,
        val notifications: List<NotificationEntity>
    ) : SessionDetailUiState
    data class Error(val message: String) : SessionDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessionRepository: SessionRepository,
    notificationRepository: NotificationRepository
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    val uiState: StateFlow<SessionDetailUiState> = sessionRepository.getById(sessionId)
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(SessionDetailUiState.Error("Session not found"))
            } else {
                notificationRepository.getByFocusSession(sessionId).combine(flowOf(session)) { notifications, s ->
                    SessionDetailUiState.Success(
                        session = s,
                        notifications = notifications
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionDetailUiState.Loading
        )
}
