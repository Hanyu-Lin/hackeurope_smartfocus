package locked.`in`.data.repository

import locked.`in`.data.local.entity.BundleMapEntryEntity

interface BundleRepository {
    suspend fun insertBundleMapEntry(entity: BundleMapEntryEntity)
    suspend fun getBundleMapByIndex(index: Int): BundleMapEntryEntity?
    suspend fun getBundleMapByBundleId(bundleId: String): BundleMapEntryEntity?
    suspend fun updateCentroid(index: Int, centroid: ByteArray, updatedAt: Long)
    suspend fun nextBundleIndex(): Int
    suspend fun bundleMapCount(): Int
    suspend fun getBundleByBundleId(bundleId: String): BundleMapEntryEntity?
    suspend fun updateBundleLive(
        bundleId: String,
        appLabel: String?,
        notificationIds: String?,
        soloSbnKey: String?,
        postedNotificationId: Int,
        allowAction: String?,
        updatedAt: Long
    )
    suspend fun clearAllBundles()
    suspend fun clearBundleMap()
}
