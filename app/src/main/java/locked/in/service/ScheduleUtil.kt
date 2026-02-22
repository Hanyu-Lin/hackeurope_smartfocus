package locked.`in`.service

import locked.`in`.domain.model.FocusMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ScheduleUtil {

    /** True if [mode] should be active at [now] per its schedule; true for non-scheduled modes. */
    fun isInScheduledWindow(mode: FocusMode, now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!mode.scheduleEnabled) return true
        if (mode.scheduleDays.isEmpty()) return false

        val currentDay = now.dayOfWeek
        val currentMinute = now.hour * 60 + now.minute

        return currentDay in mode.scheduleDays &&
            isInWindow(currentMinute, mode.scheduleStartMinute, mode.scheduleEndMinute)
    }

    /** True if [current] (min-of-day) is in [start..end); end exclusive. start==end => all day. */
    fun isInWindow(current: Int, start: Int, end: Int): Boolean {
        if (start == end) return true
        return if (start <= end) {
            current in start until end
        } else {
            // Wraps midnight (e.g., 22:00 - 06:00)
            current >= start || current < end
        }
    }

    /** Next start or end boundary after [after] as epoch millis, or null (max 8 days ahead). */
    fun nextBoundaryEpochMillis(scheduledModes: List<FocusMode>, after: LocalDateTime): Long? {
        if (scheduledModes.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val afterEpoch = after.atZone(zone).toInstant().toEpochMilli()
        var next: Long? = null
        val endOfSearch = after.plusDays(8)
        var date = after.toLocalDate()
        while (!date.isAfter(endOfSearch.toLocalDate())) {
            val dayOfWeek = date.dayOfWeek
            for (mode in scheduledModes) {
                if (!mode.scheduleEnabled || dayOfWeek !in mode.scheduleDays) continue
                val start = (mode.scheduleStartMinute % 1440).let { if (it < 0) it + 1440 else it }
                val end = (mode.scheduleEndMinute % 1440).let { if (it < 0) it + 1440 else it }
                val startTime = LocalTime.of(start / 60, start % 60)
                val endTime = LocalTime.of(end / 60, end % 60)
                val startDt = LocalDateTime.of(date, startTime)
                val endDt = if (start < end) {
                    LocalDateTime.of(date, endTime)
                } else {
                    LocalDateTime.of(date.plusDays(1), endTime)
                }
                for (boundary in listOf(startDt, endDt)) {
                    if (boundary.isBefore(after) || boundary.isAfter(endOfSearch)) continue
                    val epoch = boundary.atZone(zone).toInstant().toEpochMilli()
                    if (epoch > afterEpoch && (next == null || epoch < next)) next = epoch
                }
            }
            date = date.plusDays(1)
        }
        return next
    }
}
