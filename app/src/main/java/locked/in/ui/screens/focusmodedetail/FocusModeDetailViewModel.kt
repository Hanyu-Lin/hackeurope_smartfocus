package locked.`in`.ui.screens.focusmodedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init {
        viewModelScope.launch {
            repository.observeRulesForMode(modeId).collect { rules ->
                val mode = repository.getById(modeId)
                _uiState.value = FocusModeDetailUiState(
                    mode = mode,
                    rules = rules,
                    isLoading = false
                )
            }
        }
    }

    fun updateName(name: String) {
        viewModelScope.launch {
            val mode = repository.getById(modeId) ?: return@launch
            repository.update(mode.copy(name = name))
            refreshMode()
        }
    }

    fun updateThreshold(threshold: Float) {
        viewModelScope.launch {
            val mode = repository.getById(modeId) ?: return@launch
            repository.update(mode.copy(priorityThreshold = threshold))
            refreshMode()
        }
    }

    fun addRule(type: RuleType, value: String, effect: RuleEffect) {
        viewModelScope.launch {
            val rule = FilterRule(
                id = UUID.randomUUID().toString(),
                focusModeId = modeId,
                type = type,
                value = value,
                effect = effect,
                action = RuleAction.NONE
            )
            repository.insertRule(rule)
        }
    }

    private suspend fun refreshMode() {
        val mode = repository.getById(modeId)
        _uiState.value = _uiState.value.copy(mode = mode)
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
            val mode = repository.getById(modeId)
            _uiState.value = _uiState.value.copy(mode = mode)
        }
    }

    fun toggleScheduleEnabled() {
        viewModelScope.launch {
            val mode = repository.getById(modeId) ?: return@launch
            repository.update(mode.copy(scheduleEnabled = !mode.scheduleEnabled))
            onScheduleChanged()
        }
    }

    fun updateScheduleDays(days: Set<DayOfWeek>) {
        viewModelScope.launch {
            val mode = repository.getById(modeId) ?: return@launch
            repository.update(mode.copy(scheduleDays = days))
            onScheduleChanged()
        }
    }

    fun updateScheduleStartTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val mode = repository.getById(modeId) ?: return@launch
            repository.update(mode.copy(scheduleStartMinute = hour * 60 + minute))
            onScheduleChanged()
        }
    }

    fun updateScheduleEndTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            val mode = repository.getById(modeId) ?: return@launch
            repository.update(mode.copy(scheduleEndMinute = hour * 60 + minute))
            onScheduleChanged()
        }
    }

    private suspend fun onScheduleChanged() {
        // Clear manual override — the user is actively editing schedule settings
        settingsRepository.setScheduleOverrideModeId(null)
        // Only activate if the new schedule matches now; never deactivate mid-edit
        focusModeController.evaluateScheduleForActivation()
        refreshMode()
    }

    fun deleteMode(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteById(modeId)
            onDone()
        }
    }
}
