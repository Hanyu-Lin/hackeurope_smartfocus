package locked.`in`.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.entity.FocusSessionEntity

@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_sessions ORDER BY end_time DESC")
    fun getAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    fun getById(id: String): Flow<FocusSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity)
}
