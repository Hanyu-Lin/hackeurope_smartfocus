package locked.`in`.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import locked.`in`.data.repository.BundleRepository
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.SettingsRepository
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusModeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val focusModeRepository: FocusModeRepository,
    private val settingsRepository: SettingsRepository,
    private val bundleRepository: BundleRepository,
    private val digestGenerator: DigestGenerator,
    private val bundleNotificationPoster: BundleNotificationPosterInterface
) {

    companion object {
        private const val TAG = "FocusModeController"
    }

    suspend fun activate(modeId: String) {
        focusModeRepository.activate(modeId)
        settingsRepository.setActiveFocusModeId(modeId)
        settingsRepository.setFocusSessionStartTime(System.currentTimeMillis())

        val mode = focusModeRepository.getById(modeId)

        // If timer is enabled, schedule auto-deactivation for this mode
        if (mode != null && mode.timerEnabled) {
            val endTime = System.currentTimeMillis() + mode.timerDurationMinutes * 60_000L
            settingsRepository.setFocusTimerEndTime(modeId, endTime)

            val workRequest = OneTimeWorkRequestBuilder<TimerExpiryWorker>()
                .setInputData(workDataOf(TimerExpiryWorker.KEY_MODE_ID to modeId))
                .setInitialDelay(mode.timerDurationMinutes.toLong(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                TimerExpiryWorker.workName(modeId),
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        // Start/update foreground service with all active mode names
        val activeModes = focusModeRepository.getActive()
        val modeNames = activeModes.map { it.name }
        startService(modeNames)
    }

    suspend fun deactivateMode(modeId: String) {
        // Cancel timer worker for this mode
        WorkManager.getInstance(context).cancelUniqueWork(TimerExpiryWorker.workName(modeId))
        settingsRepository.setFocusTimerEndTime(modeId, null)

        focusModeRepository.deactivateMode(modeId)

        val remaining = focusModeRepository.getActive()
        if (remaining.isEmpty()) {
            bundleNotificationPoster.clearAll()
            bundleRepository.clearAllBundles()
            settingsRepository.setActiveFocusModeId(null)
            settingsRepository.setFocusSessionStartTime(null)
            stopService()
        } else {
            settingsRepository.setActiveFocusModeId(remaining.first().id)
            startService(remaining.map { it.name })
        }
    }

    suspend fun deactivateAll() {
        bundleNotificationPoster.clearAll()
        bundleRepository.clearAllBundles()

        // Cancel all timer workers
        val timerEndTimes = settingsRepository.focusTimerEndTimes.first()
        for (modeId in timerEndTimes.keys) {
            WorkManager.getInstance(context).cancelUniqueWork(TimerExpiryWorker.workName(modeId))
        }
        settingsRepository.clearAllTimerEndTimes()

        focusModeRepository.deactivate()
        settingsRepository.setActiveFocusModeId(null)
        settingsRepository.setFocusSessionStartTime(null)

        stopService()
    }

    suspend fun toggle(modeId: String) {
        val activeModes = focusModeRepository.getActive()
        val isThisModeActive = activeModes.any { it.id == modeId }
        if (isThisModeActive) {
            deactivateMode(modeId)
        } else {
            activate(modeId)
        }
        val mode = focusModeRepository.getById(modeId)
        val currentOverrides = settingsRepository.scheduleOverrideModeIds.first()
        val newOverrides = if (mode?.scheduleEnabled == true) currentOverrides + modeId else currentOverrides - modeId
        settingsRepository.setScheduleOverrideModeIds(newOverrides)
    }

    /** Evaluates schedule (activate/deactivate) and sets the next boundary alarm. Multi-mode: all modes in window are activated; expired scheduled modes always deactivated. */
    suspend fun evaluateSchedule() {
        // Timer expiry fallback
        val timerEndTimes = settingsRepository.focusTimerEndTimes.first()
        val now = System.currentTimeMillis()
        for ((modeId, endTime) in timerEndTimes) {
            if (now >= endTime) {
                Log.d(TAG, "Timer expired (fallback) for mode $modeId, deactivating")
                deactivateMode(modeId)
            }
        }

        val currentTime = LocalDateTime.now()
        val scheduledModes = focusModeRepository.getScheduledModes()
        val inWindowModes = scheduledModes.filter { ScheduleUtil.isInScheduledWindow(it, currentTime) }
        val inWindowIds = inWindowModes.map { it.id }.toSet()

        var activeModes = focusModeRepository.getActive()
        // (1) Always deactivate any active scheduled mode that is outside its window
        for (mode in activeModes) {
            if (mode.scheduleEnabled && mode.id !in inWindowIds) {
                Log.d(TAG, "Deactivating scheduled mode outside window: ${mode.name}")
                deactivateMode(mode.id)
            }
        }
        activeModes = focusModeRepository.getActive()
        val activeIds = activeModes.map { it.id }.toSet()

        var overrideIds = settingsRepository.scheduleOverrideModeIds.first()
        overrideIds = overrideIds.filter { id ->
            val scheduleSaysActive = id in inWindowIds
            val isCurrentlyActive = id in activeIds
            if (scheduleSaysActive == isCurrentlyActive) {
                false
            } else {
                Log.d(TAG, "Respecting manual override for $id")
                true
            }
        }.toSet()
        settingsRepository.setScheduleOverrideModeIds(overrideIds)
        if (overrideIds.isNotEmpty()) {
            scheduleNextBoundaryAlarm()
            return
        }

        // (2) Activate every in-window mode that is not already active
        for (mode in inWindowModes) {
            if (mode.id !in activeIds) {
                Log.d(TAG, "Activating scheduled mode: ${mode.name}")
                activate(mode.id)
            }
        }
        scheduleNextBoundaryAlarm()
    }

    /** Schedules one exact alarm at the next start/end of any scheduled mode; cancels if none. */
    suspend fun scheduleNextBoundaryAlarm() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val scheduledModes = focusModeRepository.getScheduledModes()
            val now = LocalDateTime.now()
            val nextEpoch = ScheduleUtil.nextBoundaryEpochMillis(scheduledModes, now)
            val intent = Intent(context, ScheduleBoundaryReceiver::class.java).apply {
                action = ScheduleBoundaryReceiver.ACTION_SCHEDULE_BOUNDARY
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
            if (nextEpoch != null) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(nextEpoch, pending), pending)
            } else {
                alarmManager.cancel(pending)
            }
        } catch (e: Exception) {
            Log.e(TAG, "scheduleNextBoundaryAlarm failed", e)
        }
    }

    private fun startService(modeNames: List<String>) {
        val intent = Intent(context, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_START
            putExtra(FocusModeService.EXTRA_MODE_NAMES, modeNames.joinToString(","))
        }
        context.startForegroundService(intent)
    }

    private fun stopService() {
        val intent = Intent(context, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_STOP
        }
        context.startService(intent)
    }
}
