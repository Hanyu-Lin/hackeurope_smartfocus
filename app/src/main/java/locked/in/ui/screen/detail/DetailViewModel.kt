package locked.`in`.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import locked.`in`.data.repository.NotificationRepository
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadNotification(notificationId: Long) {
        viewModelScope.launch {
            notificationRepository.getById(notificationId).collectLatest { entity ->
                _uiState.value = if (entity != null) {
                    DetailUiState.Success(
                        id = entity.id,
                        appName = entity.appName,
                        appPackage = entity.appPackage,
                        title = entity.title,
                        body = entity.body,
                        timestamp = entity.timestamp,
                        label = entity.label,
                        confidenceScore = entity.confidenceScore,
                        reason = entity.reason,
                        isAllowed = entity.isAllowed,
                        isRestored = entity.isRestored,
                        focusSessionId = entity.focusSessionId
                    )
                } else {
                    DetailUiState.Error("Notification not found")
                }
            }
        }
    }

    fun restoreNotification() {
        val current = _uiState.value
        if (current is DetailUiState.Success) {
            viewModelScope.launch {
                notificationRepository.restoreNotification(current.id)
            }
        }
    }
}
