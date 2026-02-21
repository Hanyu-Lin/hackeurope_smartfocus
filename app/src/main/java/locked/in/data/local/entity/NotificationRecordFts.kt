package locked.`in`.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = NotificationRecordEntity::class)
@Entity(tableName = "notification_records_fts")
data class NotificationRecordFts(
    val title: String,
    val text: String,
    val rawPrompt: String
)
