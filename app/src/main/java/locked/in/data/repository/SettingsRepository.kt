package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val activeFocusModeId: Flow<String?>
    val listenerEnabled: Flow<Boolean>
    val retentionDays: Flow<Int>
    val focusSessionStartTime: Flow<Long?>

    suspend fun setActiveFocusModeId(id: String?)
    suspend fun setListenerEnabled(enabled: Boolean)
    suspend fun setRetentionDays(days: Int)
    suspend fun setFocusSessionStartTime(time: Long?)

    val scheduleOverrideModeId: Flow<String?>
    suspend fun setScheduleOverrideModeId(id: String?)

    val focusTimerEndTimes: Flow<Map<String, Long>>
    suspend fun setFocusTimerEndTime(modeId: String, endTime: Long?)
    suspend fun clearAllTimerEndTimes()
}
