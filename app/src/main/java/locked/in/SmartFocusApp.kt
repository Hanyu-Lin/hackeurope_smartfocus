package locked.`in`

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import locked.`in`.service.ListenerWatchdogWorker
import locked.`in`.service.NotificationChannels
import locked.`in`.service.ScheduleCheckerWorker
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class SmartFocusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        scheduleWatchdog()
        scheduleScheduleChecker()
    }

    private fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<ListenerWatchdogWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ListenerWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleScheduleChecker() {
        val request = PeriodicWorkRequestBuilder<ScheduleCheckerWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduleCheckerWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
