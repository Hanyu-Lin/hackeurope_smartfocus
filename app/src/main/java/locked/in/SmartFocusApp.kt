package locked.`in`

import ai.djl.Model
import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import locked.`in`.service.ListenerWatchdogWorker
import locked.`in`.service.NotificationChannels
import locked.`in`.service.ScheduleCheckerWorker
import java.io.File
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class SmartFocusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        scheduleWatchdog()
        scheduleScheduleChecker()

        val tokenizerFile = copyAssetToFile(this, "tokenizer.json")
        val modelFile = copyAssetToFile(this, "model.pt")

        // val tokenizer =  HuggingFaceTokenizer.newInstance(tokenizerFile.toPath())

        val model = Model.newInstance("my-model", "PyTorch")
        model.load(modelFile.toPath())
    }

    fun copyAssetToFile(context: Context, assetName: String): File {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file
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
