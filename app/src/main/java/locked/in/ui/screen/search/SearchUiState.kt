package locked.`in`.ui.screen.search

import locked.`in`.data.local.entity.NotificationEntity

data class SearchUiState(
    val query: String = "",
    val selectedAppFilter: String? = null,
    val selectedLabelFilter: String? = null,
    val selectedDateRange: DateRange = DateRange.ALL,
    val results: List<NotificationEntity> = emptyList(),
    val availableApps: List<String> = emptyList(),
    val isSearching: Boolean = false
)

enum class DateRange(val label: String) {
    TODAY("Today"),
    WEEK("This Week"),
    MONTH("This Month"),
    ALL("All Time")
}
