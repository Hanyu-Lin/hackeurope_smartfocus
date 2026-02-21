package locked.`in`.ui.screens.notificationdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import locked.`in`.data.local.entity.NotificationRecordEntity
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.ui.navigation.NotificationDetailRoute
import javax.inject.Inject

@HiltViewModel
class NotificationDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NotificationRecordRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<NotificationDetailRoute>()

    private val _record = MutableStateFlow<NotificationRecordEntity?>(null)
    val record: StateFlow<NotificationRecordEntity?> = _record

    init {
        viewModelScope.launch {
            _record.value = repository.getById(route.notificationId)
        }
    }
}
