package locked.`in`.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import locked.`in`.data.preferences.PreferenceKeys
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val focusModeEnabled: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.FOCUS_MODE_ENABLED] ?: false }

    override val currentFocusSessionId: Flow<String?> =
        dataStore.data.map { it[PreferenceKeys.CURRENT_FOCUS_SESSION_ID] }

    override val lastFocusSessionId: Flow<String?> =
        dataStore.data.map { it[PreferenceKeys.LAST_FOCUS_SESSION_ID] }

    override val listenerEnabledCache: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.LISTENER_ENABLED_CACHE] ?: false }

    override val focusSessionStartTime: Flow<Long> =
        dataStore.data.map { it[PreferenceKeys.FOCUS_SESSION_START_TIME] ?: 0L }

    override suspend fun setFocusModeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.FOCUS_MODE_ENABLED] = enabled }
    }

    override suspend fun setCurrentFocusSessionId(sessionId: String?) {
        dataStore.edit { prefs ->
            if (sessionId != null) {
                prefs[PreferenceKeys.CURRENT_FOCUS_SESSION_ID] = sessionId
            } else {
                prefs.remove(PreferenceKeys.CURRENT_FOCUS_SESSION_ID)
            }
        }
    }

    override suspend fun setLastFocusSessionId(sessionId: String?) {
        dataStore.edit { prefs ->
            if (sessionId != null) {
                prefs[PreferenceKeys.LAST_FOCUS_SESSION_ID] = sessionId
            } else {
                prefs.remove(PreferenceKeys.LAST_FOCUS_SESSION_ID)
            }
        }
    }

    override suspend fun setListenerEnabledCache(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.LISTENER_ENABLED_CACHE] = enabled }
    }

    override suspend fun setFocusSessionStartTime(time: Long) {
        dataStore.edit { it[PreferenceKeys.FOCUS_SESSION_START_TIME] = time }
    }
}
