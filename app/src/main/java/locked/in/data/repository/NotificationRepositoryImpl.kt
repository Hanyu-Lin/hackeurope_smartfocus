package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import locked.`in`.data.local.dao.NotificationDao
import locked.`in`.data.local.entity.NotificationEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val dao: NotificationDao
) : NotificationRepository {

    override fun getRecent(limit: Int): Flow<List<NotificationEntity>> = dao.getRecent(limit)

    override fun getById(id: Long): Flow<NotificationEntity?> = dao.getById(id)

    override fun getByFocusSession(sessionId: String): Flow<List<NotificationEntity>> =
        dao.getByFocusSession(sessionId)

    override fun search(
        query: String,
        appPackage: String?,
        label: String?,
        startTime: Long?,
        endTime: Long?
    ): Flow<List<NotificationEntity>> = dao.search(query, appPackage, label, startTime, endTime)

    override fun countAllowedInSession(sessionId: String?): Flow<Int> =
        if (sessionId != null) dao.countAllowedInSession(sessionId) else flowOf(0)

    override fun countSuppressedInSession(sessionId: String?): Flow<Int> =
        if (sessionId != null) dao.countSuppressedInSession(sessionId) else flowOf(0)

    override fun getDistinctAppNames(): Flow<List<String>> = dao.getDistinctAppNames()

    override suspend fun insert(notification: NotificationEntity): Long {
        return dao.insert(notification)
    }

    override suspend fun getActiveBundledNotifications(sessionId: String): List<NotificationEntity> =
        dao.getActiveBundledNotifications(sessionId)

    override suspend fun getByBundleId(bundleId: String): List<NotificationEntity> =
        dao.getByBundleId(bundleId)

    override suspend fun updateBundleId(id: Long, bundleId: String) =
        dao.updateBundleId(id, bundleId)

    override suspend fun restoreNotification(id: Long) = dao.restoreNotification(id)

    override suspend fun deleteOlderThan(before: Long) = dao.deleteOlderThan(before)
}
