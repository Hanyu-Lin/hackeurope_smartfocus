package locked.`in`.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import locked.`in`.data.repository.NotificationRepository
import java.util.Calendar
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private val appFilterFlow = MutableStateFlow<String?>(null)
    private val labelFilterFlow = MutableStateFlow<String?>(null)
    private val dateRangeFlow = MutableStateFlow(DateRange.ALL)

    init {
        observeSearch()
        loadAvailableApps()
    }

    private fun observeSearch() {
        viewModelScope.launch {
            combine(
                queryFlow.debounce(300),
                appFilterFlow,
                labelFilterFlow,
                dateRangeFlow
            ) { query, app, label, dateRange ->
                SearchParams(query, app, label, dateRange)
            }
                .flatMapLatest { params ->
                    _uiState.update { it.copy(isSearching = true) }
                    val (startTime, endTime) = getTimeRange(params.dateRange)
                    notificationRepository.search(
                        query = params.query,
                        appPackage = params.appFilter,
                        label = params.labelFilter,
                        startTime = startTime,
                        endTime = endTime
                    )
                }
                .collectLatest { entities ->
                    _uiState.update { current ->
                        current.copy(
                            results = entities,
                            isSearching = false
                        )
                    }
                }
        }
    }

    private fun loadAvailableApps() {
        viewModelScope.launch {
            notificationRepository.getDistinctAppNames().collectLatest { apps ->
                _uiState.update { it.copy(availableApps = apps) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun onAppFilterChange(appPackage: String?) {
        _uiState.update { it.copy(selectedAppFilter = appPackage) }
        appFilterFlow.value = appPackage
    }

    fun onLabelFilterChange(label: String?) {
        _uiState.update { it.copy(selectedLabelFilter = label) }
        labelFilterFlow.value = label
    }

    fun onDateRangeChange(dateRange: DateRange) {
        _uiState.update { it.copy(selectedDateRange = dateRange) }
        dateRangeFlow.value = dateRange
    }

    private fun getTimeRange(dateRange: DateRange): Pair<Long?, Long?> {
        if (dateRange == DateRange.ALL) return null to null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = when (dateRange) {
            DateRange.TODAY -> cal.timeInMillis
            DateRange.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            DateRange.MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.timeInMillis
            }
            DateRange.ALL -> null
        }
        return startTime to null
    }

    private data class SearchParams(
        val query: String,
        val appFilter: String?,
        val labelFilter: String?,
        val dateRange: DateRange
    )
}
