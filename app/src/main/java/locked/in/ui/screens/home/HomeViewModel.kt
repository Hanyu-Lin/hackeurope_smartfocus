package locked.`in`.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import locked.`in`.data.local.DatabaseReset
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.SettingsRepository
import locked.`in`.domain.model.FocusMode
import locked.`in`.service.FocusModeController
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val focusModeRepository: FocusModeRepository,
    private val settingsRepository: SettingsRepository,
    private val focusModeController: FocusModeController,
    private val databaseReset: DatabaseReset
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            focusModeController.evaluateSchedule()
        }

        viewModelScope.launch {
            combine(
                focusModeRepository.observeAll(),
                focusModeRepository.observeActive(),
                settingsRepository.focusTimerEndTimes
            ) { modes, active, timerEndTimes ->
                HomeUiState.Success(
                    focusModes = modes,
                    activeModes = active,
                    timerEndTimes = timerEndTimes
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

    /** Temporary: clears Room tables and resets settings (active mode, overrides, timer times). */
    fun clearAllData() {
        viewModelScope.launch {
            databaseReset.clearAllTables()
            settingsRepository.setActiveFocusModeId(null)
            settingsRepository.setScheduleOverrideModeIds(emptySet())
            settingsRepository.clearAllTimerEndTimes()
        }
    }
}
