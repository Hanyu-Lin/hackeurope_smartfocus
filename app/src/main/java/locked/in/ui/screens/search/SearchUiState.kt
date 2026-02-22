package locked.`in`.ui.screens.search

import locked.`in`.data.local.entity.NotificationRecordEntity

data class SearchUiState(
    val query: String = "",
    val results: List<NotificationRecordEntity> = emptyList(),
    val isSearching: Boolean = false,
    val outcomeFilter: String? = null,
    /** Total number of items in current dataset (before outcome filter). */
    val resultCount: Int = 0,
    /** Count per outcome for the current dataset (search or recent). Keys = NotificationOutcome.name. */
    val outcomeCounts: Map<String, Int> = emptyMap(),
    /** True when showing recent list (no query); false when showing search results. */
    val isBrowseMode: Boolean = true
)
