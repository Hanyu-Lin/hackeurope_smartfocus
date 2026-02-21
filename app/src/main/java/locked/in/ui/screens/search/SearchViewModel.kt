package locked.`in`.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import locked.`in`.data.repository.NotificationRecordRepository
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: NotificationRecordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        performSearch()
    }

    fun setOutcomeFilter(outcome: String?) {
        _uiState.value = _uiState.value.copy(outcomeFilter = outcome)
        performSearch()
    }

    private fun performSearch() {
        searchJob?.cancel()
        val state = _uiState.value
        if (state.query.isBlank()) {
            _uiState.value = state.copy(results = emptyList(), isSearching = false, resultCount = 0)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true)
            val allResults = repository.search(_uiState.value.query)
            val filtered = if (_uiState.value.outcomeFilter != null) {
                allResults.filter { it.outcome == _uiState.value.outcomeFilter }
            } else allResults
            _uiState.value = _uiState.value.copy(
                results = filtered,
                isSearching = false,
                resultCount = filtered.size
            )
        }
    }
}
