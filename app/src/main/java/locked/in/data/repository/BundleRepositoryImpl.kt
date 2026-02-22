package locked.`in`.data.repository

import locked.`in`.data.local.dao.BundleMapDao
import locked.`in`.data.local.dao.NotificationBundleDao
import locked.`in`.data.local.entity.BundleMapEntryEntity
import locked.`in`.data.local.entity.NotificationBundleEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundleRepositoryImpl @Inject constructor(
    private val bundleMapDao: BundleMapDao,
    private val notificationBundleDao: NotificationBundleDao
) : BundleRepository {

    override suspend fun insertBundleMapEntry(entity: BundleMapEntryEntity) =
        bundleMapDao.insert(entity)

    override suspend fun getBundleMapByIndex(index: Int): BundleMapEntryEntity? =
        bundleMapDao.getByIndex(index)

    override suspend fun getBundleMapByBundleId(bundleId: String): BundleMapEntryEntity? =
        bundleMapDao.getByBundleId(bundleId)

    override suspend fun getAllBundleMapEntries(): List<BundleMapEntryEntity> =
        bundleMapDao.getAll()

    override suspend fun updateCentroid(index: Int, centroid: ByteArray, updatedAt: Long) =
        bundleMapDao.updateCentroid(index, centroid, updatedAt)


    override suspend fun nextBundleIndex(): Int = bundleMapDao.nextIndex()

    override suspend fun bundleMapCount(): Int = bundleMapDao.count()

    override suspend fun insertBundle(entity: NotificationBundleEntity) =
        notificationBundleDao.insert(entity)

    override suspend fun updateBundle(entity: NotificationBundleEntity) =
        notificationBundleDao.update(entity)

    override suspend fun getBundleByBundleId(bundleId: String): NotificationBundleEntity? =
        notificationBundleDao.getByBundleId(bundleId)

    override suspend fun getAllBundles(): List<NotificationBundleEntity> =
        notificationBundleDao.getAll()

    override suspend fun clearAllBundles() = notificationBundleDao.deleteAll()

    override suspend fun clearBundleMap() = bundleMapDao.deleteAll()
}
