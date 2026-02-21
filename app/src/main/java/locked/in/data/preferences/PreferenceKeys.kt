package locked.`in`.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val FOCUS_MODE_ENABLED = booleanPreferencesKey("focus_mode_enabled")
    val CURRENT_FOCUS_SESSION_ID = stringPreferencesKey("current_focus_session_id")
    val LAST_FOCUS_SESSION_ID = stringPreferencesKey("last_focus_session_id")
    val LISTENER_ENABLED_CACHE = booleanPreferencesKey("listener_enabled_cache")
    val FOCUS_SESSION_START_TIME = longPreferencesKey("focus_session_start_time")
}
