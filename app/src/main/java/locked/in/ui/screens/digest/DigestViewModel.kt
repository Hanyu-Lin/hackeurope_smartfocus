package locked.`in`.ui.screens.digest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import locked.`in`.data.local.entity.NotificationRecordEntity
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.data.repository.SettingsRepository
import locked.`in`.domain.model.NotificationOutcome
import locked.`in`.service.DigestGenerator
import javax.inject.Inject

data class DigestUiState(
    val digestText: String = "",
    val records: List<NotificationRecordEntity> = emptyList(),
    val totalCount: Int = 0,
    val suppressedCount: Int = 0,
    val bundledCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class DigestViewModel @Inject constructor(
    private val notificationRecordRepository: NotificationRecordRepository,
    private val settingsRepository: SettingsRepository,
    private val digestGenerator: DigestGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(DigestUiState())
    val uiState: StateFlow<DigestUiState> = _uiState

    init {
        viewModelScope.launch {
            val startTime = settingsRepository.focusSessionStartTime.first()
            val since = startTime ?: (System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            val outcomes = listOf(NotificationOutcome.SUPPRESSED.name, NotificationOutcome.BUNDLED.name)
            val records = notificationRecordRepository.getForDigest(since, outcomes)
            val digest = digestGenerator.generate(records)
            val suppressed = records.count { it.outcome == NotificationOutcome.SUPPRESSED.name }
            val bundled = records.count { it.outcome == NotificationOutcome.BUNDLED.name }
            _uiState.value = DigestUiState(
                digestText = digest,
                records = records,
                totalCount = records.size,
                suppressedCount = suppressed,
                bundledCount = bundled,
                isLoading = false
            )
        }
    }
}
