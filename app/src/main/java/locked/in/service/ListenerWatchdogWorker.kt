package locked.`in`.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ListenerWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "listener_watchdog"
        private const val TAG = "ListenerWatchdog"
    }

    override suspend fun doWork(): Result {
        val connected = isNotificationListenerEnabled(applicationContext)
        if (!connected) {
            Log.w(TAG, "Notification listener is not enabled. User should re-enable it.")
        }
        return Result.success()
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (TextUtils.isEmpty(flat)) return false
        val componentName = ComponentName(context, SmartNotificationListener::class.java).flattenToString()
        return flat.split(":").any { it == componentName }
    }
}
