package locked.`in`.service

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import locked.`in`.data.repository.BundleRepository
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.SettingsRepository
import java.time.LocalDateTime
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
        val intent = Intent(context, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_START
            putExtra(FocusModeService.EXTRA_MODE_NAME, mode?.name ?: "Focus")
        }
        context.startForegroundService(intent)
    }

    suspend fun deactivate() {
        val startTime = settingsRepository.focusSessionStartTime
        bundleNotificationPoster.clearAll()
        bundleRepository.clearAllBundles()

        focusModeRepository.deactivate()
        settingsRepository.setActiveFocusModeId(null)
        settingsRepository.setFocusSessionStartTime(null)

        val intent = Intent(context, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_STOP
        }
        context.startService(intent)
    }

    suspend fun toggle(modeId: String) {
        val active = focusModeRepository.getActive()
        if (active != null && active.id == modeId) {
            deactivate()
        } else {
            if (active != null) deactivate()
            activate(modeId)
        }
        settingsRepository.setScheduleOverrideModeId(modeId)
    }

    /**
     * Full schedule evaluation — can both activate and deactivate.
     * Called by [ScheduleCheckerWorker] periodically and by the HomeViewModel
     * on init so opening the app immediately syncs schedule state.
     */
    suspend fun evaluateSchedule() {
        val now = LocalDateTime.now()
        val currentDay = now.dayOfWeek
        val currentMinute = now.hour * 60 + now.minute

        val scheduledModes = focusModeRepository.getScheduledModes()
        val targetMode = scheduledModes.firstOrNull { mode ->
            currentDay in mode.scheduleDays && isInWindow(currentMinute, mode.scheduleStartMinute, mode.scheduleEndMinute)
        }

        val activeMode = focusModeRepository.getActive()
        val overrideModeId = settingsRepository.scheduleOverrideModeId.first()

        if (overrideModeId != null) {
            val scheduleSaysActive = targetMode != null && targetMode.id == overrideModeId
            val isCurrentlyActive = activeMode != null && activeMode.id == overrideModeId

            if (scheduleSaysActive == isCurrentlyActive) {
                // Schedule agrees with current state — override served its purpose, clear it.
                settingsRepository.setScheduleOverrideModeId(null)
                Log.d(TAG, "Override cleared for $overrideModeId (converged)")
                // State is already correct, nothing to do.
            } else {
                // Schedule wants the opposite of what the user chose. Respect the user.
                Log.d(TAG, "Respecting manual override for $overrideModeId")
            }
            return
        }

        // No override — follow the schedule.
        if (targetMode != null) {
            if (activeMode == null || activeMode.id != targetMode.id) {
                Log.d(TAG, "Activating scheduled mode: ${targetMode.name}")
                activate(targetMode.id)
            }
        } else {
            if (activeMode != null && activeMode.scheduleEnabled) {
                Log.d(TAG, "Deactivating scheduled mode: ${activeMode.name}")
                deactivate()
            }
        }
    }

    /**
     * Activation-only schedule check — called by the UI when schedule settings
     * are edited. Will activate a mode if the new schedule matches now, but will
     * never deactivate one (avoids jarring UX while the user is editing).
     */
    suspend fun evaluateScheduleForActivation() {
        val now = LocalDateTime.now()
        val currentDay = now.dayOfWeek
        val currentMinute = now.hour * 60 + now.minute

        val scheduledModes = focusModeRepository.getScheduledModes()
        val targetMode = scheduledModes.firstOrNull { mode ->
            currentDay in mode.scheduleDays && isInWindow(currentMinute, mode.scheduleStartMinute, mode.scheduleEndMinute)
        }

        val activeMode = focusModeRepository.getActive()
        if (targetMode != null && (activeMode == null || activeMode.id != targetMode.id)) {
            Log.d(TAG, "Activating scheduled mode from settings change: ${targetMode.name}")
            activate(targetMode.id)
        }
    }

    fun isInWindow(current: Int, start: Int, end: Int): Boolean {
        return if (start <= end) {
            current in start until end
        } else {
            // Wraps midnight (e.g., 22:00 - 06:00)
            current >= start || current < end
        }
    }
}
