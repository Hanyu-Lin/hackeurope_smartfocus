package locked.`in`.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import locked.`in`.MainActivity
import locked.`in`.R
import locked.`in`.data.local.entity.FocusSessionEntity
import locked.`in`.data.repository.NotificationRepository
import locked.`in`.data.repository.SessionRepository
import locked.`in`.data.repository.SettingsRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusModeController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository,
    private val sessionRepository: SessionRepository,
    private val digestGenerator: DigestGenerator,
    private val bundleNotificationPoster: BundleNotificationPoster
) {
    companion object {
        const val EXTRA_SESSION_ID = "SESSION_ID"
        private const val DIGEST_NOTIFICATION_ID = 9001
    }

    suspend fun toggle() {
        val isEnabled = settingsRepository.focusModeEnabled.first()
        if (isEnabled) {
            stop()
        } else {
            start()
        }
    }

    suspend fun start() {
        val existingSessionId = settingsRepository.currentFocusSessionId.first()
        val sessionId = existingSessionId ?: UUID.randomUUID().toString()

        settingsRepository.setFocusModeEnabled(true)
        settingsRepository.setCurrentFocusSessionId(sessionId)
        settingsRepository.setFocusSessionStartTime(System.currentTimeMillis())

        val intent = Intent(context, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    suspend fun stop() {
        val currentSessionId = settingsRepository.currentFocusSessionId.first()
        val startTime = settingsRepository.focusSessionStartTime.first()
        val endTime = System.currentTimeMillis()

        if (currentSessionId != null) {
            // Query notifications for this session and generate digest
            val notifications = notificationRepository.getByFocusSession(currentSessionId).first()
            val digest = digestGenerator.generate(notifications)
            val allowedCount = notifications.count { it.isAllowed }
            val suppressedCount = notifications.count { !it.isAllowed }

            // Persist the session
            sessionRepository.insert(
                FocusSessionEntity(
                    id = currentSessionId,
                    startTime = if (startTime > 0) startTime else endTime,
                    endTime = endTime,
                    allowedCount = allowedCount,
                    suppressedCount = suppressedCount,
                    digestText = digest
                )
            )

            // Clear bundle notifications before posting digest
            bundleNotificationPoster.clearAll()

            // Post digest notification if there were any notifications
            if (notifications.isNotEmpty()) {
                postDigestNotification(currentSessionId, digest, suppressedCount, allowedCount)
            }

            settingsRepository.setLastFocusSessionId(currentSessionId)
        }

        // Clear raw notification log after session ends
        File(context.filesDir, "notifications_raw.jsonl").delete()

        settingsRepository.setFocusModeEnabled(false)
        settingsRepository.setCurrentFocusSessionId(null)

        val intent = Intent(context, FocusModeService::class.java).apply {
            action = FocusModeService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun postDigestNotification(
        sessionId: String,
        digest: String,
        suppressedCount: Int,
        allowedCount: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val total = suppressedCount + allowedCount
        val notification = NotificationCompat.Builder(context, NotificationChannels.DIGEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Focus Session Complete")
            .setContentText("$total notifications ($suppressedCount blocked, $allowedCount allowed)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(digest))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(DIGEST_NOTIFICATION_ID, notification)
    }
}
