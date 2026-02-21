package locked.`in`.service

import locked.`in`.data.local.entity.NotificationEntity

interface NotificationBundler {
    suspend fun assignBundleId(
        entity: NotificationEntity,
        existingBundles: List<BundleInfo>
    ): String?
}

data class BundleInfo(
    val bundleId: String,
    val latestEntities: List<NotificationEntity>
)
