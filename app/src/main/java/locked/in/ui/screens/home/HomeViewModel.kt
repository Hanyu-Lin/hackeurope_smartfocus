package locked.`in`.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.domain.model.FocusMode
import locked.`in`.domain.model.NotificationOutcome
import locked.`in`.service.FocusModeController
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val focusModeRepository: FocusModeRepository,
    private val notificationRecordRepository: NotificationRecordRepository,
    private val focusModeController: FocusModeController
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // Immediately sync schedule state when the user opens the app.
        // This catches any transitions the periodic worker may have missed
        // (e.g. day changed while Doze was active).
        viewModelScope.launch {
            focusModeController.evaluateSchedule()
        }

        viewModelScope.launch {
            combine(
                focusModeRepository.observeAll(),
                focusModeRepository.observeActive()
            ) { modes, active ->
                val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000
                val count = notificationRecordRepository.countSince(since)
                val suppressed = notificationRecordRepository.countSinceByOutcome(since, NotificationOutcome.SUPPRESSED.name)
                val allowed = notificationRecordRepository.countSinceByOutcome(since, NotificationOutcome.ALLOWED.name)
                val bundled = notificationRecordRepository.countSinceByOutcome(since, NotificationOutcome.BUNDLED.name)
                HomeUiState.Success(
                    focusModes = modes,
                    activeMode = active,
                    recentNotificationCount = count,
                    suppressedCount = suppressed,
                    allowedCount = allowed,
                    bundledCount = bundled
                )
            }.collect { _uiState.value = it }
        }
    }

    fun toggleMode(modeId: String) {
        viewModelScope.launch {
            focusModeController.toggle(modeId)
        }
    }

    fun createMode(name: String) {
        viewModelScope.launch {
            focusModeRepository.insert(
                FocusMode(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    isActive = false,
                    rules = emptyList(),
                    priorityThreshold = 5.0f
                )
            )
        }
    }
}
