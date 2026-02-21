package locked.`in`.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_bundle")
data class NotificationBundleEntity(
    @PrimaryKey val bundleId: String,
    val bundleIndex: Int,
    val packageName: String,
    val appLabel: String,
    val notificationIds: String,
    val soloSbnKey: String,
    val postedNotificationId: Int,
    val createdAt: Long,
    val updatedAt: Long
)
