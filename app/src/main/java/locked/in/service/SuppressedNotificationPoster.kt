package locked.`in`.service

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import locked.`in`.R
import locked.`in`.domain.model.ParsedNotification
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuppressedNotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val notificationManager by lazy {
        NotificationManagerCompat.from(context)
    }

    private var nextId = 30_000

    /**
     * Posts a suppressed notification from our app so the user can still find it.
     * Uses IMPORTANCE_LOW channel — silent, no heads-up, available in the shade.
     */
    fun post(parsed: ParsedNotification) {
        val groupKey = "suppressed_${parsed.packageName}"

        val builder = NotificationCompat.Builder(context, NotificationChannels.SUPPRESSED_CHANNEL_ID)
            .setContentTitle(parsed.title)
            .setContentText(parsed.text)
            .setSmallIcon(categoryIcon(parsed.category))
            .setSubText("${parsed.appLabel} \u00b7 Suppressed")
            .setGroup(groupKey)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setWhen(parsed.timestamp)
            .setShowWhen(true)

        parsed.originalColor?.let { builder.setColor(it) }
        parsed.originalContentIntent?.let { builder.setContentIntent(it) }

        // Set app icon as large icon so user can identify the source app
        try {
            val appIcon = context.packageManager.getApplicationIcon(parsed.packageName)
            val bitmap = if (appIcon is BitmapDrawable) {
                appIcon.bitmap
            } else {
                appIcon.toBitmap(48, 48)
            }
            builder.setLargeIcon(bitmap)
        } catch (_: PackageManager.NameNotFoundException) {
            // No large icon if app not found
        }

        notificationManager.notify(nextId++, builder.build())
    }

    fun clearAll() {
        // Only clears suppressed notifications posted by us (they share a range)
        // NotificationManagerCompat doesn't support range cancel, so we track nothing here.
        // They auto-cancel on tap. On focus mode end, the digest replaces them.
    }

    private fun categoryIcon(category: String): Int = when (category) {
        "message", "group_message" -> R.drawable.ic_notif_message
        "email" -> R.drawable.ic_notif_email
        "call" -> R.drawable.ic_notif_call
        "social", "mention" -> R.drawable.ic_notif_social
        else -> R.drawable.ic_notif_default
    }
}
