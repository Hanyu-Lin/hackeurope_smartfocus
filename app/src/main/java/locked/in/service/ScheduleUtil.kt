package locked.`in`.service

import locked.`in`.domain.model.FocusMode
import java.time.LocalDateTime

object ScheduleUtil {

    /**
     * Returns true if the given [mode] should be active right now
     * based on its schedule. Returns true for non-scheduled modes
     * (they are controlled manually).
     */
    fun isInScheduledWindow(mode: FocusMode, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!mode.scheduleEnabled) return true
        if (mode.scheduleDays.isEmpty()) return false

        val currentDay = now.dayOfWeek
        val currentMinute = now.hour * 60 + now.minute

        return currentDay in mode.scheduleDays &&
            isInWindow(currentMinute, mode.scheduleStartMinute, mode.scheduleEndMinute)
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
