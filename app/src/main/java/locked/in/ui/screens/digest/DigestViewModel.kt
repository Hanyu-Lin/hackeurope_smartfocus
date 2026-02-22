package locked.`in`.ui.screens.digest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import locked.`in`.data.local.entity.NotificationRecordEntity
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.data.repository.SettingsRepository
import locked.`in`.domain.model.NotificationOutcome
import javax.inject.Inject

data class DigestUiState(
    val records: List<NotificationRecordEntity> = emptyList(),
    val totalCount: Int = 0,
    val suppressedCount: Int = 0,
    val bundledCount: Int = 0,
    val allowedCount: Int = 0,
    val isLoading: Boolean = true
)

private val DEFAULT_SINCE_MS = 24L * 60 * 60 * 1000

@HiltViewModel
class DigestViewModel @Inject constructor(
    private val notificationRecordRepository: NotificationRecordRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DigestUiState())
    val uiState: StateFlow<DigestUiState> = _uiState

    private val digestOutcomes = listOf(
        NotificationOutcome.SUPPRESSED.name,
        NotificationOutcome.BUNDLED.name,
        NotificationOutcome.ALLOWED.name,
        NotificationOutcome.PASSED_THROUGH.name
    )

    init {
        viewModelScope.launch {
            settingsRepository.focusSessionStartTime
                .flatMapLatest { startTime ->
                    val since = startTime ?: (System.currentTimeMillis() - DEFAULT_SINCE_MS)
                    notificationRecordRepository.observeForDigest(since, digestOutcomes)
                }
                .map { records ->
                    DigestUiState(
                        records = records,
                        totalCount = records.size,
                        suppressedCount = records.count { it.outcome == NotificationOutcome.SUPPRESSED.name },
                        bundledCount = records.count { it.outcome == NotificationOutcome.BUNDLED.name },
                        allowedCount = records.count { it.outcome == NotificationOutcome.ALLOWED.name || it.outcome == NotificationOutcome.PASSED_THROUGH.name },
                        isLoading = false
                    )
                }
                .collect { _uiState.value = it }
        }
    }
}
