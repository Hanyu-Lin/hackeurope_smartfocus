package locked.`in`.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import locked.`in`.MainActivity
import locked.`in`.R

@AndroidEntryPoint
class FocusModeService : Service() {

    companion object {
        const val ACTION_START = "locked.in.action.START_FOCUS"
        const val ACTION_STOP = "locked.in.action.STOP_FOCUS"
        const val EXTRA_MODE_NAMES = "mode_names"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val modeNamesRaw = intent.getStringExtra(EXTRA_MODE_NAMES) ?: "Focus"
                val modeNames = modeNamesRaw.split(",").filter { it.isNotBlank() }
                val notification = buildFocusNotification(modeNames)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun buildFocusNotification(modeNames: List<String>): Notification {
        val title = if (modeNames.size == 1) {
            "${modeNames.first()} Mode Active"
        } else {
            "${modeNames.size} focus modes active"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StopFocusBroadcastReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannels.FOCUS_MODE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Filtering notifications. Tap to open SmartFocus.")
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Stop Focus", stopPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
