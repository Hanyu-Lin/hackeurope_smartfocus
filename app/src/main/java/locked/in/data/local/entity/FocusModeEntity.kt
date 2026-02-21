package locked.`in`.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_modes")
data class FocusModeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean,
    val priorityThreshold: Float,
    val scheduleEnabled: Boolean = false,
    val scheduleDays: String = "",
    val scheduleStartMinute: Int = 0,
    val scheduleEndMinute: Int = 0
)
