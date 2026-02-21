package locked.`in`.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import locked.`in`.data.repository.NotificationRepository
import locked.`in`.data.repository.SessionRepository
import locked.`in`.data.repository.SettingsRepository
import locked.`in`.service.FocusModeController
import locked.`in`.service.ListenerStatusChecker
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val focusModeController: FocusModeController,
    private val listenerStatusChecker: ListenerStatusChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _listenerEnabled = MutableStateFlow(false)
    private val _summaryDismissed = MutableStateFlow(false)

    private val _isToggling = AtomicBoolean(false)

    init {
        checkListenerStatus()
        observeData()
        recoverFocusModeIfNeeded()
    }

    fun checkListenerStatus() {
        _listenerEnabled.value = listenerStatusChecker.isListenerEnabled()
        viewModelScope.launch {
            settingsRepository.setListenerEnabledCache(_listenerEnabled.value)
        }
    }

    private fun recoverFocusModeIfNeeded() {
        viewModelScope.launch {
            val isEnabled = settingsRepository.focusModeEnabled.first()
            if (isEnabled) {
                focusModeController.start()
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                settingsRepository.focusModeEnabled,
                settingsRepository.currentFocusSessionId,
                settingsRepository.lastFocusSessionId,
                _listenerEnabled,
                _summaryDismissed
            ) { focusEnabled, currentSessionId, lastSessionId, listenerEnabled, dismissed ->
                DataSnapshot(focusEnabled, currentSessionId, lastSessionId, listenerEnabled, dismissed)
            }
                .flatMapLatest { snapshot ->
                    when {
                        // Active session
                        snapshot.focusEnabled && snapshot.currentSessionId != null -> {
                            combine(
                                notificationRepository.getByFocusSession(snapshot.currentSessionId),
                                notificationRepository.countAllowedInSession(snapshot.currentSessionId),
                                notificationRepository.countSuppressedInSession(snapshot.currentSessionId)
                            ) { notifications, allowed, suppressed ->
                                HomeUiState.Active(
                                    notifications = notifications,
                                    stats = HomeStats(
                                        allowedCount = allowed,
                                        suppressedCount = suppressed
                                    ),
                                    isListenerEnabled = snapshot.listenerEnabled
                                )
                            }
                        }
                        // Just ended — show summary
                        !snapshot.focusEnabled && !snapshot.dismissed && snapshot.lastSessionId != null -> {
                            combine(
                                sessionRepository.getById(snapshot.lastSessionId),
                                sessionRepository.getAll().map { it.size > 1 }
                            ) { session, hasHistory ->
                                if (session != null) {
                                    HomeUiState.Summary(
                                        session = session,
                                        hasSessionHistory = hasHistory
                                    )
                                } else {
                                    HomeUiState.Idle(
                                        isListenerEnabled = snapshot.listenerEnabled,
                                        hasSessionHistory = false
                                    )
                                }
                            }
                        }
                        // Idle
                        else -> {
                            sessionRepository.getAll().map { sessions ->
                                HomeUiState.Idle(
                                    isListenerEnabled = snapshot.listenerEnabled,
                                    hasSessionHistory = sessions.isNotEmpty()
                                )
                            }
                        }
                    }
                }
                .catch { e ->
                    _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun toggleFocusMode() {
        if (!_isToggling.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val wasEnabled = settingsRepository.focusModeEnabled.first()
                if (!wasEnabled) {
                    // Starting a new session — reset the dismiss flag
                    _summaryDismissed.value = false
                }
                focusModeController.toggle()
            } finally {
                _isToggling.set(false)
            }
        }
    }

    fun dismissSummary() {
        _summaryDismissed.value = true
    }

    private data class DataSnapshot(
        val focusEnabled: Boolean,
        val currentSessionId: String?,
        val lastSessionId: String?,
        val listenerEnabled: Boolean,
        val dismissed: Boolean
    )
}
