package locked.`in`

import ai.djl.Model
import ai.djl.ndarray.NDArray
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.DataType
import ai.djl.ndarray.types.Shape
import ai.djl.translate.NoopTranslator
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
import java.nio.LongBuffer
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class SmartFocusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        scheduleWatchdog()
        scheduleScheduleChecker()

        // val tokenizerFile = copyAssetToFile(this, "tokenizer.json")
        // val modelFile = copyAssetToFile(this, "model")

        val tokenizer = BertTokenizer(this)

        val model = Model.newInstance("model")
        // model.load(modelFile.toPath())

        val tokenized = tokenizer.encode("hej")

        val gxArray = FloatArray(1 * 5 * 384) { 0.1f }  // Replace with your actual data
        val manager = NDManager.newBaseManager()
// Convert to NDArray

        val inputIdsArray = LongBuffer.wrap(longArrayOf(101, 2054, 2003, 1996, 2171, 102) + LongArray(10) { 0L })
        val attentionMaskArray = LongBuffer.wrap(LongArray(16) { if (it < 6) 1L else 0L })
        val inputIds: NDArray = manager.create(inputIdsArray, Shape(1, 16), DataType.INT64)
        val attentionMask: NDArray = manager.create(attentionMaskArray, Shape(1, 16), DataType.INT64)
        val gx: NDArray = manager.create(gxArray, Shape(1, 5, 384))

        val predictor = model.newPredictor(NoopTranslator())

// Combine inputs
        val inputs = NDList(inputIds, attentionMask, gx)

// Forward pass
        val outputs = predictor.predict(inputs)

// outputs[0] contains your model output as NDArray
        val result: NDArray = outputs[0]

        println("Output shape: ${result.shape}")
        println("Output data: ${result.toFloatArray().joinToString(", ", limit = 10)} ...")
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
