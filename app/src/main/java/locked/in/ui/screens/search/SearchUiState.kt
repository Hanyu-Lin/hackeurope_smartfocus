package locked.`in`.ui.screens.search

import locked.`in`.data.local.entity.NotificationRecordEntity

data class SearchUiState(
    val query: String = "",
    val results: List<NotificationRecordEntity> = emptyList(),
    val isSearching: Boolean = false,
    val outcomeFilter: String? = null,
    val resultCount: Int = 0
)
