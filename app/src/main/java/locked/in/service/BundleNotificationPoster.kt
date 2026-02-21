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
import locked.`in`.data.local.entity.NotificationBundleEntity
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

    override suspend fun handleBundle(bundleId: String, parsed: ParsedNotification, recordId: String) {
        val existing = bundleRepository.getBundleByBundleId(bundleId)
        val groupKey = "bundle_$bundleId"

        if (existing == null) {
            // First notification in this bundle — post as solo child
            val postedId = nextNotificationId++
            bundleRepository.insertBundle(
                NotificationBundleEntity(
                    bundleId = bundleId,
                    bundleIndex = 0,
                    packageName = parsed.packageName,
                    appLabel = parsed.appLabel,
                    notificationIds = Json.encodeToString(listOf(recordId)),
                    soloSbnKey = parsed.originalKey,
                    postedNotificationId = postedId,
                    createdAt = parsed.timestamp,
                    updatedAt = parsed.timestamp
                )
            )
            // Post solo notification preserving original content
            val notification = buildChildNotification(parsed, groupKey)
            notificationManager.notify(postedId, notification)
            return
        }

        val existingIds: List<String> = Json.decodeFromString(existing.notificationIds)
        val updatedIds = existingIds + recordId
        val updatedBundle = existing.copy(
            notificationIds = Json.encodeToString(updatedIds),
            updatedAt = parsed.timestamp
        )
        bundleRepository.updateBundle(updatedBundle)

        if (existingIds.size == 1) {
            // Transition solo → grouped: cancel solo, re-post first as child + new child + summary
            notificationManager.cancel(existing.postedNotificationId)

            // Re-post first notification as grouped child
            val firstParsed = parsedCache.get(existing.soloSbnKey)
            if (firstParsed != null) {
                val firstChildId = nextNotificationId++
                val firstChild = buildChildNotification(firstParsed, groupKey)
                notificationManager.notify(firstChildId, firstChild)
            }

            // Post new child
            val newChildId = nextNotificationId++
            val newChild = buildChildNotification(parsed, groupKey)
            notificationManager.notify(newChildId, newChild)

            // Post group summary
            val summaryId = existing.postedNotificationId // reuse the bundle's id for summary
            val members = buildMemberList(existing.soloSbnKey, parsed)
            val summary = buildSummaryNotification(updatedBundle, updatedIds.size, groupKey, members)
            notificationManager.notify(summaryId, summary)
        } else {
            // Third+: post new child, update summary
            val newChildId = nextNotificationId++
            val newChild = buildChildNotification(parsed, groupKey)
            notificationManager.notify(newChildId, newChild)

            // Update summary in-place
            val summaryId = existing.postedNotificationId
            val members = listOf("${parsed.title}: ${parsed.text.take(60)}")
            val summary = buildSummaryNotification(updatedBundle, updatedIds.size, groupKey, members)
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
        bundle: NotificationBundleEntity,
        count: Int,
        groupKey: String,
        memberPreviewLines: List<String>
    ): android.app.Notification {
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle("${bundle.appLabel} \u00b7 $count notifications")

        for (line in memberPreviewLines.takeLast(5)) {
            style.addLine(line)
        }

        return NotificationCompat.Builder(context, NotificationChannels.BUNDLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_default)
            .setContentTitle("${bundle.appLabel} \u00b7 $count notifications")
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
