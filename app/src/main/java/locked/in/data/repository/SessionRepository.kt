package locked.`in`.data.repository

import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.entity.FocusSessionEntity

interface SessionRepository {
    fun getAll(): Flow<List<FocusSessionEntity>>
    fun getById(id: String): Flow<FocusSessionEntity?>
    suspend fun insert(session: FocusSessionEntity)
}
