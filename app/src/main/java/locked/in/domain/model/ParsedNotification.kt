package locked.`in`.domain.model

import android.app.PendingIntent
import android.graphics.Bitmap

data class ParsedNotification(
    val packageName: String,
    val appLabel: String,
    val category: String,
    val title: String,
    val text: String,
    val subText: String?,
    val isGroupConversation: Boolean,
    val conversationTitle: String?,
    val sender: String?,
    val timestamp: Long,
    val rawPrompt: String,
    val originalKey: String,
    val originalTag: String?,
    val originalId: Int,
    val originalSmallIconResId: Int?,
    val originalLargeIconBitmap: Bitmap?,
    val originalColor: Int?,
    val originalContentIntent: PendingIntent?,
    val originalSortKey: String?
)
