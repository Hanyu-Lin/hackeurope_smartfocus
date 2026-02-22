package locked.`in`.service

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import locked.`in`.R
import locked.`in`.data.local.entity.BundleMapEntryEntity
import locked.`in`.data.repository.BundleRepository
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
    private val parsedCache: ParsedNotificationCache
) : BundleNotificationPosterInterface {

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            val members = buildMemberList(existing.soloSbnKey!!, parsed)
            val appLabel = existing.appLabel ?: parsed.appLabel
            val summary = buildSummaryNotification(appLabel, updatedIds.size, groupKey, members)
            notificationManager.notify(summaryId, summary)
        } else {
            // Third+: post new child, update summary
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
            val members = listOf("${parsed.title}: ${parsed.text.take(60)}")
            val appLabel = existing.appLabel ?: parsed.appLabel
            val summary = buildSummaryNotification(appLabel, updatedIds.size, groupKey, members)
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

    private fun buildSummaryNotification(
        appLabel: String,
        count: Int,
        groupKey: String,
        memberPreviewLines: List<String>
    ): android.app.Notification {
        val title = "$appLabel \u00b7 $count notifications"
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)

        for (line in memberPreviewLines.takeLast(5)) {
            style.addLine(line)
        }

        return NotificationCompat.Builder(context, NotificationChannels.BUNDLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_default)
            .setContentTitle(title)
            .setContentText("$count bundled notifications")
            .setStyle(style)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .build()
    }

    private fun buildMemberList(firstKey: String, newParsed: ParsedNotification): List<String> {
        val lines = mutableListOf<String>()
        val firstParsed = parsedCache.get(firstKey)
        if (firstParsed != null) {
            lines.add("${firstParsed.title}: ${firstParsed.text.take(60)}")
        }
        lines.add("${newParsed.title}: ${newParsed.text.take(60)}")
        return lines
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
