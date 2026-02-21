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

    override val activeFocusModeId: Flow<String?> =
        dataStore.data.map { it[PreferenceKeys.ACTIVE_FOCUS_MODE_ID] }

    override val listenerEnabled: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.LISTENER_ENABLED_CACHE] ?: false }

    override val retentionDays: Flow<Int> =
        dataStore.data.map { it[PreferenceKeys.RETENTION_DAYS] ?: 30 }

    override val focusSessionStartTime: Flow<Long?> =
        dataStore.data.map { it[PreferenceKeys.FOCUS_SESSION_START_TIME] }

    override suspend fun setActiveFocusModeId(id: String?) {
        dataStore.edit { prefs ->
            if (id != null) prefs[PreferenceKeys.ACTIVE_FOCUS_MODE_ID] = id
            else prefs.remove(PreferenceKeys.ACTIVE_FOCUS_MODE_ID)
        }
    }

    override suspend fun setListenerEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.LISTENER_ENABLED_CACHE] = enabled }
    }

    override suspend fun setRetentionDays(days: Int) {
        dataStore.edit { it[PreferenceKeys.RETENTION_DAYS] = days }
    }

    override suspend fun setFocusSessionStartTime(time: Long?) {
        dataStore.edit { prefs ->
            if (time != null) prefs[PreferenceKeys.FOCUS_SESSION_START_TIME] = time
            else prefs.remove(PreferenceKeys.FOCUS_SESSION_START_TIME)
        }
    }

    override val scheduleOverrideModeId: Flow<String?> =
        dataStore.data.map { it[PreferenceKeys.SCHEDULE_OVERRIDE_MODE_ID] }

    override suspend fun setScheduleOverrideModeId(id: String?) {
        dataStore.edit { prefs ->
            if (id != null) prefs[PreferenceKeys.SCHEDULE_OVERRIDE_MODE_ID] = id
            else prefs.remove(PreferenceKeys.SCHEDULE_OVERRIDE_MODE_ID)
        }
    }
}
