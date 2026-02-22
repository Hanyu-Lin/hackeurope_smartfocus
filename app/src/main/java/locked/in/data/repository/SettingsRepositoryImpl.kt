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

    override val focusTimerEndTimes: Flow<Map<String, Long>> =
        dataStore.data.map { prefs ->
            val raw = prefs[PreferenceKeys.FOCUS_TIMER_END_TIMES] ?: return@map emptyMap()
            parseTimerEndTimes(raw)
        }

    override suspend fun setFocusTimerEndTime(modeId: String, endTime: Long?) {
        dataStore.edit { prefs ->
            val current = parseTimerEndTimes(prefs[PreferenceKeys.FOCUS_TIMER_END_TIMES] ?: "")
                .toMutableMap()
            if (endTime != null) {
                current[modeId] = endTime
            } else {
                current.remove(modeId)
            }
            if (current.isEmpty()) {
                prefs.remove(PreferenceKeys.FOCUS_TIMER_END_TIMES)
            } else {
                prefs[PreferenceKeys.FOCUS_TIMER_END_TIMES] = serializeTimerEndTimes(current)
            }
        }
    }

    override suspend fun clearAllTimerEndTimes() {
        dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.FOCUS_TIMER_END_TIMES)
        }
    }

    private fun parseTimerEndTimes(raw: String): Map<String, Long> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val id = parts[0]
                val ts = parts[1].toLongOrNull()
                if (ts != null) id to ts else null
            } else null
        }.toMap()
    }

    private fun serializeTimerEndTimes(map: Map<String, Long>): String =
        map.entries.joinToString(",") { "${it.key}:${it.value}" }
}
