package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.entity.NotificationRecordEntity

interface NotificationRecordRepository {
    suspend fun insert(entity: NotificationRecordEntity)
    fun observeAll(): Flow<List<NotificationRecordEntity>>
    suspend fun getById(id: String): NotificationRecordEntity?
    suspend fun getByOutcome(outcome: String): List<NotificationRecordEntity>
    suspend fun getByBundleId(bundleId: String): List<NotificationRecordEntity>
    suspend fun getForDigest(since: Long, outcomes: List<String>): List<NotificationRecordEntity>
    fun observeForDigest(since: Long, outcomes: List<String>): Flow<List<NotificationRecordEntity>>
    suspend fun search(query: String): List<NotificationRecordEntity>
    suspend fun getRecent(limit: Int): List<NotificationRecordEntity>
    suspend fun countSince(since: Long): Int
    suspend fun countSinceByOutcome(since: Long, outcome: String): Int
    suspend fun deleteOlderThan(before: Long)
}
