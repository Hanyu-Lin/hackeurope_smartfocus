package locked.`in`.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {

    const val FOCUS_MODE_CHANNEL_ID = "focus_mode_channel"
    const val DIGEST_CHANNEL_ID = "digest_channel"
    const val BUNDLE_CHANNEL_ID = "bundle_channel"
    const val ALARM_CHANNEL_ID = "alarm_channel"
    const val SUPPRESSED_CHANNEL_ID = "suppressed_channel"

    fun createAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                FOCUS_MODE_CHANNEL_ID, "Focus Mode", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when focus mode is active"
                setShowBadge(false)
            },
            NotificationChannel(
                DIGEST_CHANNEL_ID, "Focus Digest", NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Summary of notifications filtered during focus mode"
            },
            NotificationChannel(
                BUNDLE_CHANNEL_ID, "Bundled Notifications", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Grouped notifications shown during focus mode"
                setShowBadge(false)
            },
            NotificationChannel(
                ALARM_CHANNEL_ID, "Alarm Notifications", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alert notifications"
            },
            NotificationChannel(
                SUPPRESSED_CHANNEL_ID, "Suppressed Notifications", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications suppressed by focus mode rules, available for later review"
                setShowBadge(false)
            },
        )

        manager.createNotificationChannels(channels)
    }
}
