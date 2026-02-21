package locked.`in`.domain.model

sealed class BundleDecision {
    data class NewBundle(val bundleId: String, val priority: Float) : BundleDecision()
    data class JoinBundle(val bundleId: String, val priority: Float) : BundleDecision()
}
