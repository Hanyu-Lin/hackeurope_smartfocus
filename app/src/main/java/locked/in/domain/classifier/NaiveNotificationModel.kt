package locked.`in`.domain.classifier

import locked.`in`.domain.model.ModelOutput
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaiveNotificationModel @Inject constructor() : NotificationModel {

    private val packageBundleIndex = mutableMapOf<String, Int>()
    private var nextIndex = 0

    override suspend fun infer(prompt: String, packageName: String): ModelOutput {
        val index = packageBundleIndex.getOrPut(packageName) { nextIndex++ }
        val group = FloatArray(nextIndex) { i -> if (i == index) 1.0f else 0.0f }
        return ModelOutput(
            priority = 5.0f,
            group = group,
            latent = FloatArray(1024) { 0f }
        )
    }
}
