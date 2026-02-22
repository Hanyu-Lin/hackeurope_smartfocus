package locked.`in`

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import locked.`in`.service.ClearBundlesOnStartWorker
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
        scheduleClearBundlesOnStart()
    }

    private fun scheduleClearBundlesOnStart() {
        WorkManager.getInstance(this).enqueueUniqueWork(
            ClearBundlesOnStartWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ClearBundlesOnStartWorker>()
                .setInitialDelay(1, TimeUnit.SECONDS)
                .build()
        )
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
        // Run once after a short delay so Hilt/DB are ready; AlarmManager then fires at each boundary.
        WorkManager.getInstance(this).enqueueUniqueWork(
            ScheduleCheckerWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScheduleCheckerWorker>()
                .setInitialDelay(2, TimeUnit.SECONDS)
                .build()
        )
    }
}
