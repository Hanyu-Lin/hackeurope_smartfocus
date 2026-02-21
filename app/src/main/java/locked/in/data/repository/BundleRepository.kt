package locked.`in`.data.repository

import locked.`in`.data.local.entity.BundleMapEntryEntity
import locked.`in`.data.local.entity.NotificationBundleEntity

interface BundleRepository {
    suspend fun insertBundleMapEntry(entity: BundleMapEntryEntity)
    suspend fun getBundleMapByIndex(index: Int): BundleMapEntryEntity?
    suspend fun getBundleMapByBundleId(bundleId: String): BundleMapEntryEntity?
    suspend fun updateCentroid(index: Int, centroid: ByteArray, updatedAt: Long)
    suspend fun nextBundleIndex(): Int
    suspend fun bundleMapCount(): Int
    suspend fun insertBundle(entity: NotificationBundleEntity)
    suspend fun updateBundle(entity: NotificationBundleEntity)
    suspend fun getBundleByBundleId(bundleId: String): NotificationBundleEntity?
    suspend fun getAllBundles(): List<NotificationBundleEntity>
    suspend fun clearAllBundles()
    suspend fun clearBundleMap()
}
