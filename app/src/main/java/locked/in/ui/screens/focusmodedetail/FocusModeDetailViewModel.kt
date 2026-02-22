package locked.`in`.ui.screens.focusmodedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.SettingsRepository
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.FocusMode
import locked.`in`.domain.model.RuleAction
import locked.`in`.domain.model.RuleEffect
import locked.`in`.domain.model.RuleType
import locked.`in`.service.FocusModeController
import locked.`in`.ui.navigation.FocusModeDetailRoute
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject

data class FocusModeDetailUiState(
    val mode: FocusMode? = null,
    val rules: List<FilterRule> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class FocusModeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FocusModeRepository,
    private val focusModeController: FocusModeController,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<FocusModeDetailRoute>()
    val modeId = route.modeId

    private val _uiState = MutableStateFlow(FocusModeDetailUiState())
    val uiState: StateFlow<FocusModeDetailUiState> = _uiState

    private var scheduleEvaluateJob: Job? = null

    init {
        // Do not run evaluateSchedule() here: opening the detail screen is for viewing/editing.
        // Running it can deactivate the current mode if it's wrongly considered out-of-window
        // (e.g. scheduleDays empty in DB or timing edge). Schedule is already evaluated on
        // boundary alarms and when the user changes schedule (onScheduleChanged).

        // Reactively observe both the mode entity AND its rules.
        // Any DB change (isActive, schedule fields, name, etc.) automatically
        // propagates to the UI — no manual refreshMode() needed.
        viewModelScope.launch {
            combine(
                repository.observeById(modeId),
                repository.observeRulesForMode(modeId)
            ) { mode, rules ->
                FocusModeDetailUiState(
                    mode = mode?.copy(rules = rules),
                    rules = rules,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun updateName(name: String) {
        viewModelScope.launch { repository.updateName(modeId, name) }
    }

    fun updateThreshold(threshold: Float) {
        viewModelScope.launch { repository.updatePriorityThreshold(modeId, threshold) }
    }

    fun addRule(type: RuleType, value: String, effect: RuleEffect, action: RuleAction) {
        viewModelScope.launch {
            val rule = FilterRule(
                id = UUID.randomUUID().toString(),
                focusModeId = modeId,
                type = type,
                value = value,
                effect = effect,
                action = if (effect == RuleEffect.ALLOW) action else RuleAction.NONE
            )
            repository.insertRule(rule)
        }
    }

    fun updateRule(rule: FilterRule) {
        viewModelScope.launch {
            repository.updateRule(rule)
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            repository.deleteRule(ruleId)
        }
    }

    fun toggleActive() {
        viewModelScope.launch {
            focusModeController.toggle(modeId)
        }
    }

    fun toggleTimerEnabled() {
        viewModelScope.launch {
            val mode = _uiState.value.mode ?: return@launch
            val newTimerEnabled = !mode.timerEnabled
            repository.updateTimerEnabled(modeId, newTimerEnabled)
            if (newTimerEnabled) repository.updateScheduleEnabled(modeId, false)
            onScheduleChanged()
        }
    }

    fun updateTimerDuration(minutes: Int) {
        viewModelScope.launch {
            repository.updateTimerDurationMinutes(modeId, minutes)
            onScheduleChanged()
        }
    }

    fun toggleScheduleEnabled() {
        viewModelScope.launch {
            val mode = _uiState.value.mode ?: return@launch
            val newScheduleEnabled = !mode.scheduleEnabled
            repository.updateScheduleEnabled(modeId, newScheduleEnabled)
            if (newScheduleEnabled) repository.updateTimerEnabled(modeId, false)
            onScheduleChanged()
        }
    }

    fun updateScheduleDays(days: Set<DayOfWeek>) {
        viewModelScope.launch {
            repository.updateScheduleDays(modeId, days)
            onScheduleChanged()
        }
    }

    fun updateScheduleStartTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            repository.updateScheduleStartMinute(modeId, hour * 60 + minute)
            onScheduleChanged()
        }
    }

    fun updateScheduleEndTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            repository.updateScheduleEndMinute(modeId, hour * 60 + minute)
            onScheduleChanged()
        }
    }

    private fun onScheduleChanged() {
        viewModelScope.launch { settingsRepository.setScheduleOverrideModeIds(emptySet()) }
        scheduleEvaluateJob?.cancel()
        scheduleEvaluateJob = viewModelScope.launch {
            delay(500)
            focusModeController.evaluateSchedule()
            scheduleEvaluateJob = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        scheduleEvaluateJob?.cancel()
    }

    fun deleteMode(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteById(modeId)
            onDone()
        }
    }
}
