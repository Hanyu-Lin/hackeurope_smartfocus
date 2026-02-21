package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.dao.FocusSessionDao
import locked.`in`.data.local.entity.FocusSessionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val focusSessionDao: FocusSessionDao
) : SessionRepository {

    override fun getAll(): Flow<List<FocusSessionEntity>> =
        focusSessionDao.getAll()

    override fun getById(id: String): Flow<FocusSessionEntity?> =
        focusSessionDao.getById(id)

    override suspend fun insert(session: FocusSessionEntity) =
        focusSessionDao.insert(session)
}
