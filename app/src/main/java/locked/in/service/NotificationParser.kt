package locked.`in`.service

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class Category {
    MESSAGE, EMAIL, CALL, MISSED_CALL, SOCIAL, PROMO, NEWS, SYSTEM, OTHER;

    fun label(): String = name.lowercase()
}

data class ParsedNotification(
    val app: String,
    val sender: String?,
    val conversation: String?,
    val title: String,
    val text: String,
    val bigText: String?,
    val category: Category,
    val isGroup: Boolean,
    val flags: List<String>,
    val isGroupSummary: Boolean,
) {
    fun toModelInput(): String {
        val safeTitle = title.ifBlank { "" }
        val safeText = (bigText ?: text).ifBlank { "" }
        return "$app|${category.label()}\ntitle:$safeTitle\ntext:$safeText"
    }
}

@Singleton
class NotificationParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Package constants
        private const val PKG_GMAIL = "com.google.android.gm"
        private const val PKG_INSTAGRAM = "com.instagram.android"
        private const val PKG_MESSENGER = "com.facebook.orca"
        private const val PKG_LINKEDIN = "com.linkedin.android"
        private const val PKG_DISCORD = "com.discord"
        private const val PKG_SLACK = "com.Slack"
        private const val PKG_SNAPCHAT = "com.snapchat.android"
        private const val PKG_TWITTER = "com.twitter.android"

        private val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging"
        )
        private val PHONE_PACKAGES = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.phone"
        )

        private val CATEGORY_MAP = mapOf(
            "msg" to Category.MESSAGE,
            "email" to Category.EMAIL,
            "call" to Category.CALL,
            "missed_call" to Category.MISSED_CALL,
            "social" to Category.SOCIAL,
            "promo" to Category.PROMO,
            "recommendation" to Category.PROMO,
            "alarm" to Category.SYSTEM,
            "sys" to Category.SYSTEM,
            "status" to Category.SYSTEM,
            "progress" to Category.SYSTEM,
            "err" to Category.SYSTEM,
            "navigation" to Category.SYSTEM,
            "event" to Category.OTHER,
            "reminder" to Category.OTHER,
            "transport" to Category.OTHER,
        )

        private val PACKAGE_CATEGORY_FALLBACK = mapOf(
            PKG_GMAIL to Category.EMAIL,
            PKG_INSTAGRAM to Category.SOCIAL,
            PKG_MESSENGER to Category.MESSAGE,
            PKG_LINKEDIN to Category.SOCIAL,
            PKG_DISCORD to Category.MESSAGE,
            PKG_SLACK to Category.MESSAGE,
            PKG_SNAPCHAT to Category.SOCIAL,
            PKG_TWITTER to Category.SOCIAL,
        )

        private val NEW_MESSAGES_PREFIX = Regex("^\\d+ new messages?:\\s*", RegexOption.IGNORE_CASE)
    }

    fun parse(sbn: StatusBarNotification): ParsedNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val pkg = sbn.packageName

        // --- Layer 1: Base Extraction ---

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val rawBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        // InboxStyle: join textLines
        val textLines = extras.getCharSequenceArray("android.textLines")
        val text = if (textLines != null && textLines.isNotEmpty()) {
            textLines.joinToString("\n") { it.toString() }
        } else {
            rawText
        }

        val bigText = when {
            rawBigText == null -> null
            rawBigText == text -> null
            rawBigText == rawText -> null
            else -> rawBigText
        }

        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence("android.hiddenConversationTitle")?.toString()

        val styleType = extras.getString("android.template")
        val isMessagingStyle = styleType?.contains("MessagingStyle") == true
        val isBigTextStyle = styleType?.contains("BigTextStyle") == true
        val isInboxStyle = styleType?.contains("InboxStyle") == true || textLines != null

        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        val hasReplyAction = notification.actions?.any { action ->
            action.remoteInputs?.isNotEmpty() == true
        } == true

        val channelId = notification.channelId
        val groupKey = sbn.groupKey

        val app = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkg
        }

        // --- Category normalization ---
        val rawCategory = notification.category
        var category = if (rawCategory != null) {
            CATEGORY_MAP[rawCategory] ?: Category.OTHER
        } else {
            PACKAGE_CATEGORY_FALLBACK[pkg]
                ?: if (pkg in SMS_PACKAGES) Category.MESSAGE
                else if (pkg in PHONE_PACKAGES) Category.CALL
                else Category.OTHER
        }

        // --- Layer 2: Sender Resolution ---

        var sender: String? = null
        var conversation: String? = conversationTitle

        when {
            isMessagingStyle && !isGroup -> {
                sender = rawTitle
            }
            isMessagingStyle && isGroup -> {
                sender = NEW_MESSAGES_PREFIX.replace(rawTitle, "").ifBlank { null }
                if (conversation == null) conversation = rawTitle
            }
            isBigTextStyle && (pkg == PKG_GMAIL || category == Category.EMAIL) -> {
                sender = rawTitle
            }
            isInboxStyle -> {
                sender = null // Aggregated, no single sender
            }
            category == Category.CALL || category == Category.MISSED_CALL -> {
                sender = rawTitle
            }
            pkg in SMS_PACKAGES -> {
                sender = rawTitle
            }
            pkg in PHONE_PACKAGES -> {
                sender = rawTitle
            }
            else -> {
                sender = rawTitle.ifBlank { null }
            }
        }

        // --- Layer 3: App-Specific Enrichment ---

        val flags = mutableListOf<String>()
        if (hasReplyAction) flags.add("reply")

        val selfDisplayName = extras.getCharSequence("android.selfDisplayName")?.toString()

        when (pkg) {
            PKG_GMAIL -> {
                category = Category.EMAIL
                val notifType = extras.getString("argNotificationType")
                if (notifType?.contains("IMPORTANT", ignoreCase = true) == true) {
                    flags.add("important")
                }
                if (extras.getBoolean("has_attachment", false)) {
                    flags.add("attachment")
                }
            }

            PKG_INSTAGRAM -> {
                val pushCategory = extras.getString("push_category")
                category = if (channelId == "ig_direct" || pushCategory?.startsWith("direct") == true) {
                    Category.MESSAGE
                } else {
                    Category.SOCIAL
                }
            }

            PKG_MESSENGER -> {
                category = Category.MESSAGE
            }

            PKG_LINKEDIN -> {
                category = if (channelId?.startsWith("Invitation") == true) {
                    Category.SOCIAL
                } else {
                    Category.MESSAGE
                }
                if (conversation == null) {
                    conversation = extras.getCharSequence("android.hiddenConversationTitle")?.toString()
                }
                if (sender != null) {
                    sender = NEW_MESSAGES_PREFIX.replace(sender, "").ifBlank { null }
                }
            }

            PKG_DISCORD -> {
                category = Category.MESSAGE
            }

            PKG_SLACK -> {
                category = Category.MESSAGE
            }

            PKG_SNAPCHAT -> {
                category = if (rawCategory == "msg") Category.MESSAGE else Category.SOCIAL
            }

            PKG_TWITTER -> {
                category = if (channelId?.contains("Direct", ignoreCase = true) == true
                    || rawCategory == "msg"
                ) {
                    Category.MESSAGE
                } else {
                    Category.SOCIAL
                }
            }

            in SMS_PACKAGES -> {
                category = Category.MESSAGE
            }

            in PHONE_PACKAGES -> {
                category = when (rawCategory) {
                    "missed_call" -> Category.MISSED_CALL
                    "call" -> Category.CALL
                    else -> Category.CALL
                }
            }
        }

        // General mention detection for other apps
        if ("mention" !in flags && detectMention(text, bigText, selfDisplayName)) {
            flags.add("mention")
        }

        return ParsedNotification(
            app = app,
            sender = sender?.ifBlank { null },
            conversation = conversation?.ifBlank { null },
            title = rawTitle,
            text = text,
            bigText = bigText,
            category = category,
            isGroup = isGroup || conversation != null,
            flags = flags,
            isGroupSummary = isGroupSummary,
        )
    }

    private fun detectMention(text: String, bigText: String?, selfDisplayName: String?): Boolean {
        if (selfDisplayName.isNullOrBlank()) return false
        val searchIn = "$text ${bigText ?: ""}"
        return searchIn.contains(selfDisplayName, ignoreCase = true)
            || searchIn.contains("@$selfDisplayName", ignoreCase = true)
    }
}
