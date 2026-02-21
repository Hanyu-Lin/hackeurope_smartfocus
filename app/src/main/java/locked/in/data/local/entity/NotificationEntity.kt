package locked.`in`.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["sbn_key"]),
        Index(value = ["app_package"]),
        Index(value = ["timestamp"]),
        Index(value = ["label"]),
        Index(value = ["bundle_id"]),
        Index(value = ["is_allowed"]),
        Index(value = ["focus_session_id"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sbn_key")
    val sbnKey: String,

    @ColumnInfo(name = "app_package")
    val appPackage: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    val title: String,

    val body: String,

    val timestamp: Long,

    val label: String, // "URGENT", "NORMAL", "NOISE"

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    val reason: String,

    @ColumnInfo(name = "bundle_id")
    val bundleId: String? = null,

    @ColumnInfo(name = "is_allowed")
    val isAllowed: Boolean,

    @ColumnInfo(name = "is_restored")
    val isRestored: Boolean = false,

    @ColumnInfo(name = "is_group_chat")
    val isGroupChat: Boolean = false,

    @ColumnInfo(name = "has_mention")
    val hasMention: Boolean = false,

    @ColumnInfo(name = "notification_category")
    val notificationCategory: String? = null,

    @ColumnInfo(name = "focus_session_id")
    val focusSessionId: String? = null,

    @ColumnInfo(name = "sender_name")
    val senderName: String? = null,

    @ColumnInfo(name = "conversation_name")
    val conversationName: String? = null,

    @ColumnInfo(name = "model_input")
    val modelInput: String? = null,
)
