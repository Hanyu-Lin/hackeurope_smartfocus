package locked.`in`.domain.engine

import android.util.Log
import locked.`in`.data.local.entity.BundleMapEntryEntity
import locked.`in`.data.repository.BundleRepository
import locked.`in`.domain.model.BundleDecision
import locked.`in`.domain.model.ModelOutput
import locked.`in`.domain.model.ParsedNotification
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class BundleEngine @Inject constructor(
    private val bundleRepository: BundleRepository
) {

    companion object {
        private const val TAG = "BundleEngine"
        private const val SIMILARITY_THRESHOLD = 0.75f
        private const val EMA_ALPHA = 0.3f
    }

    suspend fun assign(parsed: ParsedNotification, output: ModelOutput): BundleDecision {
        val latent = output.latent
        if (latent.isEmpty()) {
            return createNewBundle(parsed, output, latent)
        }

        val allEntries = bundleRepository.getAllBundleMapEntries()

        var bestSimilarity = -1f
        var bestEntry: BundleMapEntryEntity? = null

        for (entry in allEntries) {
            if (entry.centroid.isEmpty()) continue
            val centroid = bytesToFloatArray(entry.centroid)
            if (centroid.size != latent.size) continue
            val sim = cosineSimilarity(latent, centroid)
            if (sim > bestSimilarity) {
                bestSimilarity = sim
                bestEntry = entry
            }
        }

        return if (bestSimilarity >= SIMILARITY_THRESHOLD && bestEntry != null) {
            val oldCentroid = bytesToFloatArray(bestEntry.centroid)
            val updatedCentroid = ema(oldCentroid, latent, EMA_ALPHA)
            bundleRepository.updateCentroid(
                bestEntry.bundleIndex,
                floatArrayToBytes(updatedCentroid),
                parsed.timestamp
            )
            Log.d(TAG, "Joining bundle ${bestEntry.bundleId} (similarity=$bestSimilarity)")
            BundleDecision.JoinBundle(bestEntry.bundleId, output.priority)
        } else {
            Log.d(TAG, "Creating new bundle (best_similarity=$bestSimilarity)")
            createNewBundle(parsed, output, latent)
        }
    }

    private suspend fun createNewBundle(
        parsed: ParsedNotification,
        output: ModelOutput,
        latent: FloatArray
    ): BundleDecision {
        val newIndex = bundleRepository.nextBundleIndex()
        val newBundleId = UUID.randomUUID().toString()
        bundleRepository.insertBundleMapEntry(
            BundleMapEntryEntity(
                bundleIndex = newIndex,
                bundleId = newBundleId,
                centroid = if (latent.isNotEmpty()) floatArrayToBytes(latent) else ByteArray(0),
                packageName = parsed.packageName,
                createdAt = parsed.timestamp,
                updatedAt = parsed.timestamp
            )
        )
        return BundleDecision.NewBundle(newBundleId, output.priority)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    private fun ema(old: FloatArray, new: FloatArray, alpha: Float): FloatArray {
        return FloatArray(old.size) { i -> old[i] * (1f - alpha) + new[i] * alpha }
    }

    private fun floatArrayToBytes(arr: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in arr) buffer.putFloat(v)
        return buffer.array()
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val arr = FloatArray(bytes.size / 4)
        for (i in arr.indices) arr[i] = buffer.getFloat()
        return arr
    }
}
