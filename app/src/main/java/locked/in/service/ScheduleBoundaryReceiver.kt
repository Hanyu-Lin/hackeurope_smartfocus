package locked.`in`.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/** When the schedule-boundary alarm fires, runs schedule evaluation (via worker). */
class ScheduleBoundaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SCHEDULE_BOUNDARY) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            ScheduleCheckerWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScheduleCheckerWorker>().build()
        )
    }

    companion object {
        const val ACTION_SCHEDULE_BOUNDARY = "locked.in.SCHEDULE_BOUNDARY"
    }
}
