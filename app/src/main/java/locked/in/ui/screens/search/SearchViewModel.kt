package locked.`in`.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import locked.`in`.data.local.entity.NotificationRecordEntity
import locked.`in`.data.repository.NotificationRecordRepository
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: NotificationRecordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null

    /** Current dataset (recent or search results) before outcome filter. */
    private var currentFullList: List<NotificationRecordEntity> = emptyList()

    init {
        loadRecent()
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        performSearch()
    }

    fun setOutcomeFilter(outcome: String?) {
        val state = _uiState.value
        _uiState.value = state.copy(
            outcomeFilter = outcome,
            results = applyOutcomeFilter(currentFullList, outcome),
            resultCount = currentFullList.size
        )
    }

    private fun applyOutcomeFilter(
        list: List<NotificationRecordEntity>,
        outcome: String?
    ): List<NotificationRecordEntity> =
        if (outcome != null) list.filter { it.outcome == outcome } else list

    private fun outcomeCounts(list: List<NotificationRecordEntity>): Map<String, Int> =
        list.groupingBy { it.outcome }.eachCount()

    private fun loadRecent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            val list = repository.getRecent(RECENT_LIMIT)
            currentFullList = list
            val state = _uiState.value
            _uiState.value = state.copy(
                results = applyOutcomeFilter(list, state.outcomeFilter),
                isSearching = false,
                resultCount = list.size,
                outcomeCounts = outcomeCounts(list),
                isBrowseMode = true
            )
        }
    }

    private fun performSearch() {
        searchJob?.cancel()
        val state = _uiState.value
        if (state.query.isBlank()) {
            loadRecent()
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _uiState.value = _uiState.value.copy(isSearching = true)
            val list = repository.search(state.query)
            currentFullList = list
            val outcomeFilter = _uiState.value.outcomeFilter
            _uiState.value = _uiState.value.copy(
                results = applyOutcomeFilter(list, outcomeFilter),
                isSearching = false,
                resultCount = list.size,
                outcomeCounts = outcomeCounts(list),
                isBrowseMode = false
            )
        }
    }

    companion object {
        private const val RECENT_LIMIT = 100
        private const val DEBOUNCE_MS = 300L
    }
}
