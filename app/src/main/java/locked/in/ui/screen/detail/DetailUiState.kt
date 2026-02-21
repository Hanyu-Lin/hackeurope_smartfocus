package locked.`in`.ui.screen.detail

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(
        val id: Long,
        val appName: String,
        val appPackage: String,
        val title: String,
        val body: String,
        val timestamp: Long,
        val label: String,
        val confidenceScore: Float,
        val reason: String,
        val isAllowed: Boolean,
        val isRestored: Boolean,
        val focusSessionId: String?
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}
