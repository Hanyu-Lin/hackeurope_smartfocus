package locked.`in`.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import locked.`in`.data.repository.BundleRepository

/**
 * Runs once on app start so that after process death, the next notification runs rules
 * (and Rule ALLOW actions like vibrate/alarm fire) instead of being treated as JoinBundle
 * with a stale NotificationBundle row.
 */
class ClearBundlesOnStartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun bundleRepository(): BundleRepository
    }

    companion object {
        const val WORK_NAME = "clear_bundles_on_start"
        private const val TAG = "ClearBundlesOnStart"
    }

    override suspend fun doWork(): Result {
        return try {
            EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
                .bundleRepository()
                .clearAllBundles()
            Log.d(TAG, "Cleared NotificationBundle rows on app start")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear bundles on start", e)
            Result.failure()
        }
    }
}
