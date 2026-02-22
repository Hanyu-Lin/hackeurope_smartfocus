package locked.`in`.data.repository

import locked.`in`.data.local.dao.BundleMapDao
import locked.`in`.data.local.entity.BundleMapEntryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundleRepositoryImpl @Inject constructor(
    private val bundleMapDao: BundleMapDao
) : BundleRepository {

    override suspend fun insertBundleMapEntry(entity: BundleMapEntryEntity) =
        bundleMapDao.insert(entity)

    override suspend fun getBundleMapByIndex(index: Int): BundleMapEntryEntity? =
        bundleMapDao.getByIndex(index)

    override suspend fun getBundleMapByBundleId(bundleId: String): BundleMapEntryEntity? =
        bundleMapDao.getByBundleId(bundleId)

    override suspend fun updateCentroid(index: Int, centroid: ByteArray, updatedAt: Long) =
        bundleMapDao.updateCentroid(index, centroid, updatedAt)


    override suspend fun nextBundleIndex(): Int = bundleMapDao.nextIndex()

    override suspend fun bundleMapCount(): Int = bundleMapDao.count()

    override suspend fun getBundleByBundleId(bundleId: String): BundleMapEntryEntity? =
        bundleMapDao.getByBundleId(bundleId)

    override suspend fun updateBundleLive(
        bundleId: String,
        appLabel: String?,
        notificationIds: String?,
        soloSbnKey: String?,
        postedNotificationId: Int,
        allowAction: String?,
        updatedAt: Long
    ) = bundleMapDao.updateLiveByBundleId(
        bundleId, appLabel, notificationIds, soloSbnKey, postedNotificationId, allowAction, updatedAt
    )

    override suspend fun clearAllBundles() = bundleMapDao.clearAllLive(System.currentTimeMillis())

    override suspend fun clearBundleMap() = bundleMapDao.deleteAll()
}
