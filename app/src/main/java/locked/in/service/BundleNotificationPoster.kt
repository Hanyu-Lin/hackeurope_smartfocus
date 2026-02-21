package locked.`in`.service

import android.util.Log
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import locked.`in`.MainActivity
import locked.`in`.R
import locked.`in`.data.local.entity.NotificationEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundleNotificationPoster @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BundlePoster"
        private const val CHILD_ID_BASE = 10_000
        private const val SUMMARY_ID_BASE = 50_000
    }

    private val activeBundles = mutableMapOf<String, MutableSet<Long>>()

    fun postOrUpdate(
        bundleId: String,
        newEntity: NotificationEntity,
        allBundleEntities: List<NotificationEntity>
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        Log.d(TAG, "postOrUpdate: bundleId=$bundleId, entityId=${newEntity.id}, members=${allBundleEntities.size}")

        activeBundles.getOrPut(bundleId) { mutableSetOf() }.add(newEntity.id)

        // Post child notification
        val childId = CHILD_ID_BASE + newEntity.id.toInt()
        val childIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_id", newEntity.id)
        }
        val childPendingIntent = PendingIntent.getActivity(
            context,
            childId,
            childIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val childNotification = NotificationCompat.Builder(context, NotificationChannels.BUNDLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(newEntity.appName)
            .setContentText(newEntity.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(newEntity.title)
                    .bigText(newEntity.body)
            )
            .setGroup(bundleId)
            .setContentIntent(childPendingIntent)
            .setAutoCancel(true)
            .setWhen(newEntity.timestamp)
            .build()

        manager.notify(childId, childNotification)

        // Post/update group summary
        val summaryId = SUMMARY_ID_BASE + bundleId.hashCode()
        val count = allBundleEntities.size
        val categoryLabel = deriveCategoryLabel(bundleId)

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("$categoryLabel ($count)")

        allBundleEntities.takeLast(5).reversed().forEach { entity ->
            inboxStyle.addLine("${entity.appName}: ${entity.body}".take(80))
        }

        if (count > 5) {
            val appCount = allBundleEntities.map { it.appPackage }.distinct().size
            inboxStyle.setSummaryText("+${count - 5} more from $appCount apps")
        }

        val summaryIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val summaryPendingIntent = PendingIntent.getActivity(
            context,
            summaryId,
            summaryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryNotification = NotificationCompat.Builder(context, NotificationChannels.BUNDLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$categoryLabel ($count)")
            .setContentText("$count notifications")
            .setStyle(inboxStyle)
            .setGroup(bundleId)
            .setGroupSummary(true)
            .setContentIntent(summaryPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(summaryId, summaryNotification)
        Log.d(TAG, "Posted child=$childId, summary=$summaryId for bundle=$bundleId")
    }

    private fun deriveCategoryLabel(bundleId: String): String {
        val raw = bundleId.removePrefix("bundle_")
        return raw.replaceFirstChar { it.uppercase() } + "s"
    }

    fun clearAll() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        for ((bundleId, entityIds) in activeBundles) {
            for (entityId in entityIds) {
                manager.cancel(CHILD_ID_BASE + entityId.toInt())
            }
            manager.cancel(SUMMARY_ID_BASE + bundleId.hashCode())
        }

        activeBundles.clear()
    }
}
