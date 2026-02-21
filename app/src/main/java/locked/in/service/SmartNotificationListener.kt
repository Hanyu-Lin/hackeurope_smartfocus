package locked.`in`.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.io.File
import org.json.JSONObject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import locked.`in`.data.local.entity.NotificationEntity
import locked.`in`.data.repository.NotificationRepository
import locked.`in`.data.repository.SettingsRepository

class SmartNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SmartNotifListener"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ListenerEntryPoint {
        fun notificationRepository(): NotificationRepository
        fun settingsRepository(): SettingsRepository
        fun notificationParser(): NotificationParser
        fun notificationBundler(): NotificationBundler
        fun bundleNotificationPoster(): BundleNotificationPoster
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ListenerEntryPoint::class.java)
    }

    private val notificationRepository by lazy { entryPoint.notificationRepository() }
    private val settingsRepository by lazy { entryPoint.settingsRepository() }
    private val parser by lazy { entryPoint.notificationParser() }
    private val bundler by lazy { entryPoint.notificationBundler() }
    private val bundlePoster by lazy { entryPoint.bundleNotificationPoster() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        serviceScope.launch {
            settingsRepository.setListenerEnabledCache(true)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        serviceScope.launch {
            settingsRepository.setListenerEnabledCache(false)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Skip our own notifications
        if (sbn.packageName == applicationContext.packageName) return

        // Skip ongoing/foreground service notifications
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        serviceScope.launch {
            logRawNotification(sbn)

            try {
                val parsed = parser.parse(sbn)

                // Skip group summary notifications
                if (parsed.isGroupSummary) {
                    Log.d(TAG, "Skipped group summary: ${parsed.app} - ${parsed.title}")
                    return@launch
                }

                val focusEnabled = settingsRepository.focusModeEnabled.first()
                val focusSessionId = settingsRepository.currentFocusSessionId.first()

                val entity = mapToEntity(sbn, parsed, focusEnabled, focusSessionId)
                val insertedId = notificationRepository.insert(entity)
                val savedEntity = entity.copy(id = insertedId)

                if (focusEnabled && !savedEntity.isAllowed) {
                    cancelNotification(sbn.key)

                    val activeBundled = if (focusSessionId != null) {
                        notificationRepository.getActiveBundledNotifications(focusSessionId)
                    } else {
                        emptyList()
                    }
                    val existingBundles = activeBundled
                        .groupBy { it.bundleId!! }
                        .map { (id, entities) -> BundleInfo(id, entities) }

                    val bundleId = bundler.assignBundleId(savedEntity, existingBundles)
                    if (bundleId != null) {
                        notificationRepository.updateBundleId(insertedId, bundleId)
                        val members = notificationRepository.getByBundleId(bundleId)
                        bundlePoster.postOrUpdate(bundleId, savedEntity.copy(bundleId = bundleId), members)
                    }

                    Log.d(TAG, "Suppressed: ${savedEntity.appName} - ${savedEntity.title}")
                } else {
                    Log.d(TAG, "Allowed: ${savedEntity.appName} - ${savedEntity.title}")
                }
            } catch (e: Exception) {
                // Safety: if anything fails, allow the notification through
                Log.e(TAG, "Error processing notification, allowing through", e)
            }
        }
    }

    private fun mapToEntity(
        sbn: StatusBarNotification,
        parsed: ParsedNotification,
        focusEnabled: Boolean,
        focusSessionId: String?
    ): NotificationEntity {
        val body = parsed.bigText ?: parsed.text

        // Simple filtering: block Instagram when focus is on, allow everything else
        val isInstagram = sbn.packageName == INSTAGRAM_PACKAGE
        val shouldBlock = focusEnabled && isInstagram

        val label = when {
            shouldBlock -> "NOISE"
            else -> "NORMAL"
        }
        val confidence = if (shouldBlock) 0.9f else 0.5f
        val reason = when {
            !focusEnabled -> "Focus mode off"
            isInstagram -> "Instagram blocked during focus"
            else -> "Allowed"
        }
        val isAllowed = !shouldBlock

        return NotificationEntity(
            sbnKey = sbn.key,
            appPackage = sbn.packageName,
            appName = parsed.app,
            title = parsed.title,
            body = body,
            timestamp = sbn.postTime,
            label = label,
            confidenceScore = confidence,
            reason = reason,
            isAllowed = isAllowed,
            isGroupChat = parsed.isGroup,
            hasMention = "mention" in parsed.flags,
            notificationCategory = parsed.category.label(),
            focusSessionId = if (focusEnabled) focusSessionId else null,
            senderName = parsed.sender,
            conversationName = parsed.conversation,
            modelInput = parsed.toModelInput(),
        )
    }

    private fun logRawNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification ?: return
            val json = JSONObject()

            json.put("sbn", sbn.toString())
            json.put("notification", notification.toString())

            // Extras bundle iterated separately because Bundle.toString() truncates values
            val extras = notification.extras
            val extrasJson = JSONObject()
            for (key in extras.keySet()) {
                extrasJson.put(key, extras.get(key)?.toString())
            }
            json.put("extras", extrasJson)

            // Append as JSON line
            val file = File(filesDir, "notifications_raw.jsonl")
            file.appendText(json.toString() + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log raw notification", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
