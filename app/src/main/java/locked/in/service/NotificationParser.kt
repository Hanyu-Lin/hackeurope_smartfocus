package locked.`in`.service

import android.app.Notification
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import locked.`in`.domain.model.ParsedNotification
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun parse(sbn: StatusBarNotification): ParsedNotification {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val appLabel = resolveAppLabel(pkg)

        val parsed = when (pkg) {
            "com.android.phone",
            "com.google.android.dialer" -> parseCall(extras, appLabel, pkg, sbn)
            "com.google.android.apps.messaging",
            "com.android.mms" -> parseSms(extras, appLabel, pkg, sbn)
            "com.google.android.gm" -> parseGmail(extras, appLabel, pkg, sbn)
            "com.linkedin.android" -> parseLinkedIn(extras, appLabel, pkg, sbn)
            "com.instagram.android" -> parseInstagram(extras, appLabel, pkg, sbn)
            "com.discord" -> parseDiscord(extras, appLabel, pkg, sbn)
            else -> parseGeneric(extras, appLabel, pkg, sbn)
        }

        return parsed.copy(rawPrompt = buildPrompt(parsed))
    }

    private fun parseCall(extras: Bundle, appLabel: String, pkg: String, sbn: StatusBarNotification): ParsedNotification {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val resolvedText = resolveContact(text) ?: text

        return baseParsed(
            packageName = pkg, appLabel = appLabel, category = "call",
            title = title, text = resolvedText, sbn = sbn, extras = extras
        )
    }

    private fun parseSms(extras: Bundle, appLabel: String, pkg: String, sbn: StatusBarNotification): ParsedNotification {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
        if (style != null) {
            val lastMsg = style.messages.lastOrNull()
            val sender = lastMsg?.person?.name?.toString() ?: extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = lastMsg?.text?.toString() ?: ""
            val isGroup = style.isGroupConversation
            val convTitle = style.conversationTitle?.toString()
            val category = if (isGroup) "group_message" else "message"
            val title = convTitle ?: sender

            return baseParsed(
                packageName = pkg, appLabel = appLabel, category = category,
                title = title, text = text, sbn = sbn, extras = extras,
                sender = sender, isGroupConversation = isGroup, conversationTitle = convTitle
            )
        }
        // Fallback
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_BIG_TEXT) ?: extras.getString(Notification.EXTRA_TEXT) ?: ""
        return baseParsed(
            packageName = pkg, appLabel = appLabel, category = "message",
            title = title, text = text, sbn = sbn, extras = extras
        )
    }

    private fun parseGmail(extras: Bundle, appLabel: String, pkg: String, sbn: StatusBarNotification): ParsedNotification {
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val title: String
        val text: String
        if (textLines != null && textLines.isNotEmpty()) {
            title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            text = textLines.joinToString(" | ")
        } else {
            title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            text = extras.getString(Notification.EXTRA_BIG_TEXT) ?: extras.getString(Notification.EXTRA_TEXT) ?: ""
        }
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT)

        return baseParsed(
            packageName = pkg, appLabel = appLabel, category = "email",
            title = title, text = text, subText = subText, sbn = sbn, extras = extras
        )
    }

    private fun parseLinkedIn(extras: Bundle, appLabel: String, pkg: String, sbn: StatusBarNotification): ParsedNotification {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_BIG_TEXT) ?: extras.getString(Notification.EXTRA_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT)
        val category = inferLinkedInCategory(title)

        return baseParsed(
            packageName = pkg, appLabel = appLabel, category = category,
            title = title, text = text, subText = subText, sbn = sbn, extras = extras
        )
    }

    private fun parseInstagram(extras: Bundle, appLabel: String, pkg: String, sbn: StatusBarNotification): ParsedNotification {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
        if (style != null) {
            val lastMsg = style.messages.lastOrNull()
            val sender = lastMsg?.person?.name?.toString() ?: extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = lastMsg?.text?.toString() ?: ""
            val isGroup = style.isGroupConversation
            val convTitle = style.conversationTitle?.toString() ?: sender
            val category = if (isGroup) "group_message" else "message"

            return baseParsed(
                packageName = pkg, appLabel = appLabel, category = category,
                title = convTitle, text = text, sbn = sbn, extras = extras,
                sender = sender, isGroupConversation = isGroup, conversationTitle = convTitle
            )
        }
        // Fallback: activity notification
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        val category = inferInstagramCategory(text)

        return baseParsed(
            packageName = pkg, appLabel = appLabel, category = category,
            title = title, text = text, sbn = sbn, extras = extras
        )
    }

    private fun parseDiscord(extras: Bundle, appLabel: String, pkg: String, sbn: StatusBarNotification): ParsedNotification {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
        if (style != null) {
            val lastMsg = style.messages.lastOrNull()
            val sender = lastMsg?.person?.name?.toString() ?: extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = lastMsg?.text?.toString() ?: ""
            val isGroup = style.isGroupConversation
            val convTitle = style.conversationTitle?.toString()
            val title = convTitle ?: sender
            val category = if (isGroup) "group_message" else "message"

            return baseParsed(
                packageName = pkg, appLabel = appLabel, category = category,
                title = title, text = text, sbn = sbn, extras = extras,
                sender = sender, isGroupConversation = isGroup, conversationTitle = convTitle
            )
        }
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
        return baseParsed(
            packageName = pkg, appLabel = appLabel, category = "group_message",
            title = title, text = text, sbn = sbn, extras = extras
        )
    }

    private fun parseGeneric(extras: Bundle, appLabel: String, pkg: String, sbn: StatusBarNotification): ParsedNotification {
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_BIG_TEXT) ?: extras.getString(Notification.EXTRA_TEXT) ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT)
        val rawCategory = sbn.notification.category
        val category = when (rawCategory) {
            Notification.CATEGORY_CALL -> "call"
            Notification.CATEGORY_MESSAGE -> "message"
            Notification.CATEGORY_EMAIL -> "email"
            Notification.CATEGORY_SOCIAL -> "social"
            Notification.CATEGORY_SYSTEM, Notification.CATEGORY_SERVICE, Notification.CATEGORY_STATUS -> "system"
            else -> "other"
        }

        return baseParsed(
            packageName = pkg, appLabel = appLabel, category = category,
            title = title, text = text, subText = subText, sbn = sbn, extras = extras
        )
    }

    private fun baseParsed(
        packageName: String,
        appLabel: String,
        category: String,
        title: String,
        text: String,
        sbn: StatusBarNotification,
        extras: Bundle,
        subText: String? = null,
        sender: String? = null,
        isGroupConversation: Boolean = false,
        conversationTitle: String? = null
    ): ParsedNotification {
        val notification = sbn.notification
        return ParsedNotification(
            packageName = packageName,
            appLabel = appLabel,
            category = category,
            title = title,
            text = text,
            subText = subText,
            isGroupConversation = isGroupConversation,
            conversationTitle = conversationTitle,
            sender = sender,
            timestamp = sbn.postTime,
            rawPrompt = "",
            originalKey = sbn.key,
            originalTag = sbn.tag,
            originalId = sbn.id,
            originalSmallIconResId = runCatching { notification.smallIcon?.resId }.getOrNull(),
            originalLargeIconBitmap = null,
            originalColor = notification.color,
            originalContentIntent = notification.contentIntent,
            originalSortKey = notification.sortKey
        )
    }

    private fun buildPrompt(p: ParsedNotification): String = buildString {
        appendLine("app: ${p.appLabel}")
        appendLine("category: ${p.category}")
        if (p.title.isNotBlank()) appendLine("title: ${p.title.singleLine()}")
        if (p.text.isNotBlank()) appendLine("text: ${p.text.singleLine().take(200)}")
    }.trimEnd()

    private fun String.singleLine() =
        replace('\n', ' ').replace('\r', ' ').trim().replace("\\s+".toRegex(), " ")

    private fun resolveAppLabel(pkg: String): String =
        runCatching {
            context.packageManager
                .getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0))
                .toString()
        }.getOrDefault(pkg)

    private fun resolveContact(phoneNumber: String): String? {
        if (!phoneNumber.any { it.isDigit() }) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun inferLinkedInCategory(title: String): String = when {
            title.contains("messaged you", ignoreCase = true) -> "message"
            title.contains("sent you a message", ignoreCase = true) -> "message"
            title.contains("mentioned you", ignoreCase = true) -> "mention"
            title.contains("commented", ignoreCase = true) -> "mention"
            title.contains("liked", ignoreCase = true) -> "social"
            title.contains("reacted", ignoreCase = true) -> "social"
            title.contains("connection request", ignoreCase = true) -> "social"
            title.contains("accepted your", ignoreCase = true) -> "social"
            else -> "other"
        }

        fun inferInstagramCategory(text: String): String = when {
            text.contains("liked", ignoreCase = true) -> "social"
            text.contains("started following", ignoreCase = true) -> "social"
            text.contains("mentioned you", ignoreCase = true) -> "mention"
            text.contains("commented", ignoreCase = true) -> "mention"
            text.contains("sent you a message", ignoreCase = true) -> "message"
            text.contains("replied to your story", ignoreCase = true) -> "message"
            else -> "social"
        }
    }
}
