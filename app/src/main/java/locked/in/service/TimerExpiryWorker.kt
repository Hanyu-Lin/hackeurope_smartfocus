package locked.`in`.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class TimerExpiryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun focusModeController(): FocusModeController
    }

    companion object {
        const val KEY_MODE_ID = "modeId"
        fun workName(modeId: String) = "timer_expiry_$modeId"
    }

    override suspend fun doWork(): Result {
        val modeId = inputData.getString(KEY_MODE_ID) ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, WorkerEntryPoint::class.java
        )
        entryPoint.focusModeController().deactivateMode(modeId)
        return Result.success()
    }
}
