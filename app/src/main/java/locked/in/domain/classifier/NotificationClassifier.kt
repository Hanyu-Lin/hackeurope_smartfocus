package locked.`in`.domain.classifier

import locked.`in`.domain.model.ClassificationResult

interface NotificationClassifier {
    suspend fun classify(prompt: String): ClassificationResult
}
