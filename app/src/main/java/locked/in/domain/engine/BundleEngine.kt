package locked.`in`.domain.engine

import locked.`in`.data.local.entity.BundleMapEntryEntity
import locked.`in`.data.repository.BundleRepository
import locked.`in`.domain.model.BundleDecision
import locked.`in`.domain.model.ModelOutput
import locked.`in`.domain.model.ParsedNotification
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundleEngine @Inject constructor(
    private val bundleRepository: BundleRepository
) {

    suspend fun assign(parsed: ParsedNotification, output: ModelOutput): BundleDecision {
        val bundleIndex = output.group.indexOfFirst { it == 1.0f }
        if (bundleIndex < 0) {
            return createNewBundle(parsed, output)
        }

        val existing = bundleRepository.getBundleMapByIndex(bundleIndex)
        return if (existing != null) {
            BundleDecision.JoinBundle(existing.bundleId, output.priority)
        } else {
            createNewBundle(parsed, output, bundleIndex)
        }
    }

    private suspend fun createNewBundle(
        parsed: ParsedNotification,
        output: ModelOutput,
        index: Int? = null
    ): BundleDecision {
        val newIndex = index ?: bundleRepository.nextBundleIndex()
        val newBundleId = UUID.randomUUID().toString()
        bundleRepository.insertBundleMapEntry(
            BundleMapEntryEntity(
                bundleIndex = newIndex,
                bundleId = newBundleId,
                centroid = ByteArray(0),
                packageName = parsed.packageName,
                createdAt = parsed.timestamp,
                updatedAt = parsed.timestamp
            )
        )
        return BundleDecision.NewBundle(newBundleId, output.priority)
    }
}
