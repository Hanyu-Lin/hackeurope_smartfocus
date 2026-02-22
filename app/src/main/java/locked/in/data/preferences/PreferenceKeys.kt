package locked.`in`.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val ACTIVE_FOCUS_MODE_ID = stringPreferencesKey("active_focus_mode_id")
    val LISTENER_ENABLED_CACHE = booleanPreferencesKey("listener_enabled_cache")
    val RETENTION_DAYS = intPreferencesKey("retention_days")
    val FOCUS_SESSION_START_TIME = longPreferencesKey("focus_session_start_time")
    val SCHEDULE_OVERRIDE_MODE_ID = stringPreferencesKey("schedule_override_mode_id")
    val SCHEDULE_OVERRIDE_MODE_IDS = stringPreferencesKey("schedule_override_mode_ids")
    val FOCUS_TIMER_END_TIMES = stringPreferencesKey("focus_timer_end_times")
}
