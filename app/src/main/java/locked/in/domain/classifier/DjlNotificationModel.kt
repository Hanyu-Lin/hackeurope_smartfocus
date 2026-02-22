package locked.`in`.domain.classifier

import ai.djl.Model
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.inference.Predictor
import ai.djl.ndarray.NDList
import ai.djl.ndarray.types.DataType
import ai.djl.ndarray.types.Shape
import ai.djl.translate.Batchifier
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import locked.`in`.domain.model.ModelOutput
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DjlNotificationModel @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationModel {

    companion object {
        private const val TAG = "DjlNotificationModel"
        private const val LATENT_DIM = 384
        private const val MODEL_ASSET = "model_mobile.pt"
        private const val TOKENIZER_ASSET = "tokenizer.json"
    }

    private val initMutex = Mutex()

    @Volatile
    private var predictor: Predictor<String, ModelOutput>? = null

    @Volatile
    private var initFailed = false

    private val fallback = NaiveNotificationModel()

    override suspend fun infer(prompt: String, packageName: String): ModelOutput {
        val p = getPredictor()
        if (p == null) {
            return fallback.infer(prompt, packageName)
        }
        return try {
            withContext(Dispatchers.IO) {
                synchronized(p) {
                    p.predict(prompt)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed, using fallback", e)
            fallback.infer(prompt, packageName)
        }
    }

    private suspend fun getPredictor(): Predictor<String, ModelOutput>? {
        if (initFailed) return null
        predictor?.let { return it }

        initMutex.withLock {
            if (initFailed) return null
            predictor?.let { return it }

            return try {
                withContext(Dispatchers.IO) {
                    val modelDir = File(context.filesDir, "djl_model")
                    modelDir.mkdirs()

                    val modelFile = File(modelDir, MODEL_ASSET)
                    if (!modelFile.exists()) {
                        context.assets.open(MODEL_ASSET).use { input ->
                            modelFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }

                    val tokenizerFile = File(context.filesDir, TOKENIZER_ASSET)
                    if (!tokenizerFile.exists()) {
                        context.assets.open(TOKENIZER_ASSET).use { input ->
                            tokenizerFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }

                    val tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile.toPath())
                    val translator = AttentionTranslator(tokenizer)

                    val model = Model.newInstance("model_mobile", "PyTorch")
                    model.load(modelDir.toPath())

                    val pred = model.newPredictor(translator)
                    predictor = pred
                    Log.i(TAG, "Model and tokenizer loaded successfully")
                    pred
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize DJL model", e)
                initFailed = true
                null
            }
        }
    }

    private class AttentionTranslator(
        private val tokenizer: HuggingFaceTokenizer
    ) : Translator<String, ModelOutput> {

        override fun getBatchifier(): Batchifier? = null

        override fun processInput(ctx: TranslatorContext, input: String): NDList {
            val encoding = tokenizer.encode(input)
            val manager = ctx.ndManager

            val inputIds = manager.create(encoding.ids).toType(DataType.INT64, false)
                .reshape(Shape(1, encoding.ids.size.toLong()))

            val attentionMask = manager.create(encoding.attentionMask).toType(DataType.INT64, false)
                .reshape(Shape(1, encoding.attentionMask.size.toLong()))

            val dummyGx = manager.zeros(Shape(1, 1, LATENT_DIM.toLong()))

            return NDList(inputIds, attentionMask, dummyGx)
        }

        override fun processOutput(ctx: TranslatorContext, list: NDList): ModelOutput {
            val priorityTensor = list[0]  // (1, 1)
            val latentTensor = list[2]    // (1, 384)

            val priority = priorityTensor.toFloatArray()[0].coerceIn(0f, 1f)
            val latent = latentTensor.reshape(Shape(LATENT_DIM.toLong())).toFloatArray()

            return ModelOutput(
                priority = priority,
                group = FloatArray(0),
                latent = latent
            )
        }
    }
}
