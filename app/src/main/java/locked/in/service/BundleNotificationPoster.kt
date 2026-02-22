package locked.`in`.service

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import locked.`in`.R
import locked.`in`.data.local.entity.BundleMapEntryEntity
import locked.`in`.data.repository.BundleRepository
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.domain.model.ParsedNotification
import javax.inject.Inject
import javax.inject.Singleton

interface BundleNotificationPosterInterface {
    suspend fun handleBundle(bundleId: String, parsed: ParsedNotification, recordId: String)
    fun clearAll()
}

@Singleton
class BundleNotificationPosterImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bundleRepository: BundleRepository,
    private val notificationRecordRepository: NotificationRecordRepository,
    private val parsedCache: ParsedNotificationCache
) : BundleNotificationPosterInterface {

    companion object {
        private const val TAG = "BundleNotifPoster"
    }

    private val notificationManager by lazy {
        NotificationManagerCompat.from(context)
    }

    private var nextNotificationId = 20_000

    private fun hasLiveState(entry: BundleMapEntryEntity?): Boolean =
        entry != null && !entry.notificationIds.isNullOrBlank()

    override suspend fun handleBundle(bundleId: String, parsed: ParsedNotification, recordId: String) {
        val existing = bundleRepository.getBundleByBundleId(bundleId)
        val groupKey = "bundle_$bundleId"

        if (existing == null || !hasLiveState(existing)) {
            // Fallback: no live state (e.g. cleared or race). Set live columns and post solo.
            val postedId = nextNotificationId++
            bundleRepository.updateBundleLive(
                bundleId,
                parsed.appLabel,
                Json.encodeToString(listOf(recordId)),
                parsed.originalKey,
                postedId,
                null,
                parsed.timestamp
            )
            val notification = buildChildNotification(parsed, groupKey)
            notificationManager.notify(postedId, notification)
            return
        }

        val existingIds: List<String> = Json.decodeFromString(existing.notificationIds!!)
        val updatedIds = existingIds + recordId

        if (existingIds.size == 1) {
            // Transition solo → grouped: first notification was shown by system (we did not post), so only cancel our id if we had posted one
            if (existing.postedNotificationId >= 0) {
                notificationManager.cancel(existing.postedNotificationId)
            }

            // Re-post first notification as grouped child
            val firstParsed = existing.soloSbnKey?.let { parsedCache.get(it) }
            if (firstParsed != null) {
                val firstChildId = nextNotificationId++
                val firstChild = buildChildNotification(firstParsed, groupKey)
                notificationManager.notify(firstChildId, firstChild)
            }

            // Post new child
            val newChildId = nextNotificationId++
            val newChild = buildChildNotification(parsed, groupKey)
            notificationManager.notify(newChildId, newChild)

            // Summary ID must be positive; solo-system path uses -1, so assign and persist now
            val summaryId = if (existing.postedNotificationId >= 0) existing.postedNotificationId else nextNotificationId++
            bundleRepository.updateBundleLive(
                bundleId,
                existing.appLabel,
                Json.encodeToString(updatedIds),
                existing.soloSbnKey,
                summaryId,
                existing.allowAction,
                parsed.timestamp
            )
            val appLabel = existing.appLabel ?: parsed.appLabel
            val summary = buildSummaryWithMessagingStyle(
                appLabel = appLabel,
                firstParsed = firstParsed,
                latestParsed = parsed,
                groupKey = groupKey
            )
            Log.d(TAG, "Posted bundle summary id=$summaryId groupKey=$groupKey")
            notificationManager.notify(summaryId, summary)
        } else {
            // Third+: post new child, update summary with full preview lines from DB
            bundleRepository.updateBundleLive(
                bundleId,
                existing.appLabel,
                Json.encodeToString(updatedIds),
                existing.soloSbnKey,
                existing.postedNotificationId,
                existing.allowAction,
                parsed.timestamp
            )
            val newChildId = nextNotificationId++
            val newChild = buildChildNotification(parsed, groupKey)
            notificationManager.notify(newChildId, newChild)

            val summaryId = existing.postedNotificationId
            val appLabel = existing.appLabel ?: parsed.appLabel
            val records = notificationRecordRepository.getByBundleId(bundleId)
            val orderedRecords = updatedIds.mapNotNull { id -> records.find { it.id == id } }
            val memberLines = orderedRecords.takeLast(5).map { "${it.title}: ${it.text.take(60)}" }
            val summary = buildSummaryWithInboxStyle(
                appLabel = appLabel,
                count = updatedIds.size,
                groupKey = groupKey,
                memberPreviewLines = memberLines,
                contentIntent = parsed.originalContentIntent,
                sourceColor = parsed.originalColor,
                sourcePackageName = parsed.packageName
            )
            Log.d(TAG, "Posted bundle summary id=$summaryId groupKey=$groupKey")
            notificationManager.notify(summaryId, summary)
        }
    }

    private fun buildChildNotification(
        parsed: ParsedNotification,
        groupKey: String
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(context, NotificationChannels.BUNDLE_CHANNEL_ID)
            .setContentTitle(parsed.title)
            .setContentText(parsed.text)
            .setSmallIcon(categoryIcon(parsed.category))
            .setGroup(groupKey)
            .setGroupSummary(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        parsed.subText?.let { builder.setSubText(it) }
        parsed.originalColor?.let { builder.setColor(it) }
        parsed.originalContentIntent?.let { builder.setContentIntent(it) }
        parsed.originalSortKey?.let { builder.setSortKey(it) }

        // Try to set the app icon as large icon
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

        return builder.build()
    }

    /**
     * Native-style summary for 2-item bundle: MessagingStyle so it expands like a conversation.
     * Tapping opens the latest notification's app. Branded with source app icon and color.
     */
    private fun buildSummaryWithMessagingStyle(
        appLabel: String,
        firstParsed: ParsedNotification?,
        latestParsed: ParsedNotification,
        groupKey: String
    ): android.app.Notification {
        val title = "$appLabel \u00b7 2 notifications"
        val builder = NotificationCompat.Builder(context, NotificationChannels.BUNDLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_default)
            .setContentTitle(title)
            .setContentText(latestParsed.text.take(80))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(latestParsed.originalContentIntent)

        applySourceAppBranding(builder, latestParsed.packageName, latestParsed.originalColor)

        if (firstParsed != null) {
            val self = Person.Builder().setName(appLabel).build()
            val style = NotificationCompat.MessagingStyle(self)
                .setConversationTitle(appLabel)
                .setGroupConversation(true)
            val person1 = Person.Builder().setName(firstParsed.title).build()
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    firstParsed.text.take(200),
                    firstParsed.timestamp,
                    person1
                )
            )
            val person2 = Person.Builder().setName(latestParsed.title).build()
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    latestParsed.text.take(200),
                    latestParsed.timestamp,
                    person2
                )
            )
            builder.setStyle(style)
        } else {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .addLine("${latestParsed.title}: ${latestParsed.text.take(60)}")
            builder.setStyle(inboxStyle)
        }

        return builder.build()
    }

    /**
     * Inbox-style summary for 3+ items with full preview lines (from DB). Branded with source app.
     */
    private fun buildSummaryWithInboxStyle(
        appLabel: String,
        count: Int,
        groupKey: String,
        memberPreviewLines: List<String>,
        contentIntent: android.app.PendingIntent?,
        sourceColor: Int?,
        sourcePackageName: String
    ): android.app.Notification {
        val title = "$appLabel \u00b7 $count notifications"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        for (line in memberPreviewLines.takeLast(5)) {
            style.addLine(line)
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.BUNDLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_default)
            .setContentTitle(title)
            .setContentText(if (count == 1) "1 notification" else "$count notifications")
            .setStyle(style)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        contentIntent?.let { builder.setContentIntent(it) }
        applySourceAppBranding(builder, sourcePackageName, sourceColor)
        return builder.build()
    }

    private fun applySourceAppBranding(
        builder: NotificationCompat.Builder,
        packageName: String,
        color: Int?
    ) {
        color?.let { builder.setColor(it) }
        try {
            val appIcon = context.packageManager.getApplicationIcon(packageName)
            val bitmap: Bitmap? = when (appIcon) {
                is BitmapDrawable -> appIcon.bitmap
                else -> appIcon.toBitmap(48, 48)
            }
            bitmap?.let { builder.setLargeIcon(it) }
        } catch (_: PackageManager.NameNotFoundException) { }
    }

    private fun categoryIcon(category: String): Int = when (category) {
        "message", "group_message" -> R.drawable.ic_notif_message
        "email" -> R.drawable.ic_notif_email
        "call" -> R.drawable.ic_notif_call
        "social", "mention" -> R.drawable.ic_notif_social
        else -> R.drawable.ic_notif_default
    }

    override fun clearAll() {
        notificationManager.cancelAll()
    }
}
