package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.entity.NotificationEntity

interface NotificationRepository {
    fun getRecent(limit: Int = 50): Flow<List<NotificationEntity>>
    fun getById(id: Long): Flow<NotificationEntity?>
    fun getByFocusSession(sessionId: String): Flow<List<NotificationEntity>>
    fun search(
        query: String = "",
        appPackage: String? = null,
        label: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): Flow<List<NotificationEntity>>
    fun countAllowedInSession(sessionId: String?): Flow<Int>
    fun countSuppressedInSession(sessionId: String?): Flow<Int>
    fun getDistinctAppNames(): Flow<List<String>>
    suspend fun insert(notification: NotificationEntity): Long
    suspend fun getActiveBundledNotifications(sessionId: String): List<NotificationEntity>
    suspend fun getByBundleId(bundleId: String): List<NotificationEntity>
    suspend fun updateBundleId(id: Long, bundleId: String)
    suspend fun restoreNotification(id: Long)
    suspend fun deleteOlderThan(before: Long)
}
