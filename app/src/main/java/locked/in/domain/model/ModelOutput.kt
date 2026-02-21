package locked.`in`.domain.model

data class ModelOutput(
    val priority: Float,
    val group: FloatArray,
    val latent: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelOutput) return false
        return priority == other.priority &&
            group.contentEquals(other.group) &&
            latent.contentEquals(other.latent)
    }

    override fun hashCode(): Int {
        var result = priority.hashCode()
        result = 31 * result + group.contentHashCode()
        result = 31 * result + latent.contentHashCode()
        return result
    }
}
