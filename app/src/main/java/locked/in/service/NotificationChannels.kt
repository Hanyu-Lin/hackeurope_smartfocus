package locked.`in`.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val FOCUS_MODE_CHANNEL_ID = "focus_mode_channel"
    const val DIGEST_CHANNEL_ID = "digest_channel"
    const val BUNDLE_CHANNEL_ID = "bundle_channel"

    fun createAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val focusChannel = NotificationChannel(
            FOCUS_MODE_CHANNEL_ID,
            "Focus Mode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when focus mode is active"
            setShowBadge(false)
        }

        val digestChannel = NotificationChannel(
            DIGEST_CHANNEL_ID,
            "Focus Digest",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Summary of notifications filtered during focus mode"
        }

        val bundleChannel = NotificationChannel(
            BUNDLE_CHANNEL_ID,
            "Bundled Notifications",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Grouped notifications shown during focus mode"
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(focusChannel, digestChannel, bundleChannel))
    }
}
