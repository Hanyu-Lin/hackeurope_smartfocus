package locked.`in`.domain.model

import java.time.DayOfWeek

data class FocusMode(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val rules: List<FilterRule>,
    val priorityThreshold: Float,
    val scheduleEnabled: Boolean = false,
    val scheduleDays: Set<DayOfWeek> = emptySet(),
    val scheduleStartMinute: Int = 0,
    val scheduleEndMinute: Int = 0,
    val timerEnabled: Boolean = false,
    val timerDurationMinutes: Int = 25
)
