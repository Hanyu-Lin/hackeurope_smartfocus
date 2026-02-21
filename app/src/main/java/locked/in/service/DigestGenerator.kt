package locked.`in`.service

import locked.`in`.data.local.entity.NotificationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigestGenerator @Inject constructor() {

    fun generate(notifications: List<NotificationEntity>): String {
        if (notifications.isEmpty()) {
            return "No notifications during this session"
        }

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val totalCount = notifications.size
        val blockedCount = notifications.count { !it.isAllowed }
        val allowedCount = notifications.count { it.isAllowed }
        val appGroups = notifications.groupBy { it.appName }
        val distinctApps = appGroups.size

        // Sort groups: blocked-heavy first, then by total count
        val sortedGroups = appGroups.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<NotificationEntity>>> { entry ->
                entry.value.count { !it.isAllowed }
            }.thenByDescending { it.value.size }
        )

        val sb = StringBuilder()
        sb.appendLine("Focus Session Summary")
        sb.appendLine("---------------------")
        sb.appendLine("$totalCount notifications from $distinctApps apps ($blockedCount blocked, $allowedCount allowed)")

        for ((appName, appNotifications) in sortedGroups) {
            val appBlocked = appNotifications.count { !it.isAllowed }
            val appAllowed = appNotifications.count { it.isAllowed }
            val sorted = appNotifications.sortedBy { it.timestamp }

            sb.appendLine()
            sb.append("$appName (")
            val parts = mutableListOf<String>()
            if (appBlocked > 0) parts.add("$appBlocked blocked")
            if (appAllowed > 0) parts.add("$appAllowed allowed")
            sb.append(parts.joinToString(", "))
            sb.appendLine("):")

            val showCount = minOf(sorted.size, if (sorted.size <= 3) 3 else 2)
            for (i in 0 until showCount) {
                val n = sorted[i]
                val displayName = formatNotificationLine(n)
                val time = timeFormat.format(Date(n.timestamp))
                sb.appendLine("  - $displayName ($time)")
            }
            val remaining = sorted.size - showCount
            if (remaining > 0) {
                sb.appendLine("  - and $remaining other notifications")
            }
        }

        return sb.toString().trimEnd()
    }

    private fun formatNotificationLine(n: NotificationEntity): String {
        val sender = n.senderName
        val conversation = n.conversationName
        val category = n.notificationCategory

        val prefix = when {
            sender != null && conversation != null -> "$sender in $conversation"
            sender != null -> sender
            else -> n.title.take(40).let { if (n.title.length > 40) "$it..." else it }
        }

        val suffix = when (category) {
            "call" -> " [call]"
            "missed_call" -> " [missed call]"
            "email" -> " [email]"
            else -> ""
        }

        val result = prefix.take(50).let { if (prefix.length > 50) "$it..." else it }
        return "$result$suffix"
    }
}
