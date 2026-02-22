package locked.`in`.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import locked.`in`.data.repository.SettingsRepository
import locked.`in`.domain.engine.ActionDispatcher

class SmartNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SmartNotifListener"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ListenerEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun notificationParser(): NotificationParser
        fun classifierPipeline(): ClassifierPipeline
        fun suppressedNotificationPoster(): SuppressedNotificationPoster
        fun actionDispatcher(): ActionDispatcher
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ListenerEntryPoint::class.java)
    }

    private val settingsRepository by lazy { entryPoint.settingsRepository() }
    private val parser by lazy { entryPoint.notificationParser() }
    private val pipeline by lazy { entryPoint.classifierPipeline() }
    private val suppressedPoster by lazy { entryPoint.suppressedNotificationPoster() }
    private val actionDispatcher by lazy { entryPoint.actionDispatcher() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        serviceScope.launch {
            settingsRepository.setListenerEnabled(true)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
        serviceScope.launch {
            settingsRepository.setListenerEnabled(false)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == applicationContext.packageName) return

        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        serviceScope.launch {
            try {
                val parsed = parser.parse(sbn)
                Log.d(TAG, "Parsed: pkg=${sbn.packageName}, key=${sbn.key}, title=${parsed.title}, text=${parsed.text.take(50)}")

                val result = pipeline.process(parsed)
                Log.d(TAG, "Pipeline result: $result for ${parsed.appLabel} - ${parsed.title}")

                when (result) {
                    is PipelineResult.PassThrough -> {
                        // Notification passes through untouched
                    }
                    is PipelineResult.Allow -> {
                        // Rule matched ALLOW — notification passes through; dispatch action (alarm/buzz/silent)
                        actionDispatcher.dispatch(result.action)
                    }
                    is PipelineResult.Suppress -> {
                        // Cancel the original notification and resurface from our app.
                        // Note: messaging apps (Messenger, WhatsApp, etc.) reuse the same
                        // sbn.key per conversation thread. If an earlier message in the same
                        // thread was allowed, cancelling this key also removes that earlier
                        // message from the shade. This is an Android platform limitation —
                        // we cannot selectively strip individual messages from a notification.
                        cancelNotification(sbn.key)
                        suppressedPoster.post(parsed)
                        Log.d(TAG, "Suppressed and resurfaced: ${parsed.appLabel} - ${parsed.title}")
                    }
                    is PipelineResult.Bundle -> {
                        // Bundle path (unused when AI model is disabled)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification from ${sbn.packageName}, allowing through", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
