package locked.`in`.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.entity.NotificationRecordEntity

@Dao
interface NotificationRecordDao {

    @Insert
    suspend fun insertRecord(entity: NotificationRecordEntity)

    @Transaction
    suspend fun insertWithFts(entity: NotificationRecordEntity) {
        insertRecord(entity)
    }

    @Query("SELECT * FROM notification_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<NotificationRecordEntity>>

    @Query("SELECT * FROM notification_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE id = :id")
    suspend fun getById(id: String): NotificationRecordEntity?

    @Query("SELECT * FROM notification_records WHERE outcome = :outcome ORDER BY timestamp DESC")
    suspend fun getByOutcome(outcome: String): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getByPackageName(packageName: String): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE bundleId = :bundleId ORDER BY timestamp DESC")
    suspend fun getByBundleId(bundleId: String): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE timestamp >= :since AND outcome IN (:outcomes) ORDER BY priorityScore DESC, timestamp DESC")
    suspend fun getForDigest(since: Long, outcomes: List<String>): List<NotificationRecordEntity>

    @Query("SELECT * FROM notification_records WHERE timestamp >= :since AND outcome IN (:outcomes) ORDER BY priorityScore DESC, timestamp DESC")
    fun observeForDigest(since: Long, outcomes: List<String>): Flow<List<NotificationRecordEntity>>

    @Query("""
        SELECT * FROM notification_records
        WHERE (title LIKE :pattern ESCAPE '\' COLLATE NOCASE OR text LIKE :pattern ESCAPE '\' COLLATE NOCASE
            OR rawPrompt LIKE :pattern ESCAPE '\' COLLATE NOCASE OR appLabel LIKE :pattern ESCAPE '\' COLLATE NOCASE
            OR packageName LIKE :pattern ESCAPE '\' COLLATE NOCASE)
        ORDER BY timestamp DESC
    """)
    suspend fun searchByPattern(pattern: String): List<NotificationRecordEntity>

    @Query("SELECT COUNT(*) FROM notification_records WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM notification_records WHERE timestamp >= :since AND outcome = :outcome")
    suspend fun countSinceByOutcome(since: Long, outcome: String): Int

    @Query("DELETE FROM notification_records WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
