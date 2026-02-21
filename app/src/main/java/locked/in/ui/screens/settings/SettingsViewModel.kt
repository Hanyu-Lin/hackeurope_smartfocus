package locked.`in`.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.data.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationRecordRepository: NotificationRecordRepository
) : ViewModel() {

    val retentionDays: StateFlow<Int> = settingsRepository.retentionDays.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 30
    )

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setRetentionDays(days)
        }
    }

    fun purgeOldRecords() {
        viewModelScope.launch {
            val days = retentionDays.value
            val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
            notificationRecordRepository.deleteOlderThan(cutoff)
        }
    }
}
