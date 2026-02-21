package locked.`in`.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_records")
data class NotificationRecordEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val appLabel: String,
    val category: String,
    val title: String,
    val text: String,
    val rawPrompt: String,
    val timestamp: Long,
    val isContact: Boolean,
    val outcome: String,
    val appliedRuleId: String?,
    val priorityScore: Float?,
    val bundleId: String?
)
