package locked.`in`.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "allowed_count") val allowedCount: Int,
    @ColumnInfo(name = "suppressed_count") val suppressedCount: Int,
    @ColumnInfo(name = "digest_text") val digestText: String
)
