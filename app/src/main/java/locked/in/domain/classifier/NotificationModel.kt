package locked.`in`.domain.classifier

import locked.`in`.domain.model.ModelOutput

interface NotificationModel {
    suspend fun infer(prompt: String, packageName: String): ModelOutput
}
