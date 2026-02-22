package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.dao.NotificationRecordDao
import locked.`in`.data.local.entity.NotificationRecordEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRecordRepositoryImpl @Inject constructor(
    private val dao: NotificationRecordDao
) : NotificationRecordRepository {

    override suspend fun insert(entity: NotificationRecordEntity) {
        dao.insertWithFts(entity)
    }

    override fun observeAll(): Flow<List<NotificationRecordEntity>> = dao.observeAll()

    override suspend fun getById(id: String): NotificationRecordEntity? = dao.getById(id)

    override suspend fun getByOutcome(outcome: String): List<NotificationRecordEntity> =
        dao.getByOutcome(outcome)

    override suspend fun getByBundleId(bundleId: String): List<NotificationRecordEntity> =
        dao.getByBundleId(bundleId)

    override suspend fun getForDigest(since: Long, outcomes: List<String>): List<NotificationRecordEntity> =
        dao.getForDigest(since, outcomes)

    override fun observeForDigest(since: Long, outcomes: List<String>): Flow<List<NotificationRecordEntity>> =
        dao.observeForDigest(since, outcomes)

    override suspend fun search(query: String): List<NotificationRecordEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val pattern = "%${escapeForLike(trimmed)}%"
        return dao.searchByPattern(pattern)
    }

    override suspend fun getRecent(limit: Int): List<NotificationRecordEntity> =
        dao.getRecent(limit)

    private fun escapeForLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    override suspend fun countSince(since: Long): Int = dao.countSince(since)

    override suspend fun countSinceByOutcome(since: Long, outcome: String): Int =
        dao.countSinceByOutcome(since, outcome)

    override suspend fun deleteOlderThan(before: Long) = dao.deleteOlderThan(before)
}
