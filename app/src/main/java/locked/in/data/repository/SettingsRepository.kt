package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val focusModeEnabled: Flow<Boolean>
    val currentFocusSessionId: Flow<String?>
    val lastFocusSessionId: Flow<String?>
    val listenerEnabledCache: Flow<Boolean>
    val focusSessionStartTime: Flow<Long>

    suspend fun setFocusModeEnabled(enabled: Boolean)
    suspend fun setCurrentFocusSessionId(sessionId: String?)
    suspend fun setLastFocusSessionId(sessionId: String?)
    suspend fun setListenerEnabledCache(enabled: Boolean)
    suspend fun setFocusSessionStartTime(time: Long)
}
