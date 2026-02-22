package locked.`in`.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class ScheduleCheckerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun focusModeController(): FocusModeController
    }

    companion object {
        const val WORK_NAME = "schedule_checker"
    }

    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
            .focusModeController()
            .evaluateSchedule()
        return Result.success()
    }
}
