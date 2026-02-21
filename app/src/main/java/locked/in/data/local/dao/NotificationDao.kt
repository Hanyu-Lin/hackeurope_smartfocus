package locked.`in`.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.entity.NotificationEntity

@Dao
interface NotificationDao {

    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE id = :id")
    fun getById(id: Long): Flow<NotificationEntity?>

    @Query("SELECT * FROM notifications WHERE focus_session_id = :sessionId ORDER BY timestamp DESC")
    fun getByFocusSession(sessionId: String): Flow<List<NotificationEntity>>

    // Search with optional filters
    @Query(
        """
        SELECT * FROM notifications
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
        AND (:appPackage IS NULL OR app_package = :appPackage)
        AND (:label IS NULL OR label = :label)
        AND (:startTime IS NULL OR timestamp >= :startTime)
        AND (:endTime IS NULL OR timestamp <= :endTime)
        ORDER BY timestamp DESC
        """
    )
    fun search(
        query: String = "",
        appPackage: String? = null,
        label: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): Flow<List<NotificationEntity>>

    // Session-scoped counts
    @Query("SELECT COUNT(*) FROM notifications WHERE focus_session_id = :sessionId AND is_allowed = 1")
    fun countAllowedInSession(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE focus_session_id = :sessionId AND is_allowed = 0")
    fun countSuppressedInSession(sessionId: String): Flow<Int>

    // Distinct app packages for search filter
    @Query("SELECT DISTINCT app_name FROM notifications ORDER BY app_name")
    fun getDistinctAppNames(): Flow<List<String>>

    // Restore
    @Query("UPDATE notifications SET is_restored = 1 WHERE id = :id")
    suspend fun restoreNotification(id: Long)

    // Bundle queries
    @Query("""
        SELECT * FROM notifications
        WHERE bundle_id IS NOT NULL AND focus_session_id = :sessionId
        ORDER BY timestamp DESC
    """)
    suspend fun getActiveBundledNotifications(sessionId: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE bundle_id = :bundleId ORDER BY timestamp DESC")
    suspend fun getByBundleId(bundleId: String): List<NotificationEntity>

    @Query("UPDATE notifications SET bundle_id = :bundleId WHERE id = :id")
    suspend fun updateBundleId(id: Long, bundleId: String)

    // Cleanup
    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
