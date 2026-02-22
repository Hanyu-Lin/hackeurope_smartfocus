package locked.`in`.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.entity.FocusModeEntity

@Dao
interface FocusModeDao {

    @Query("SELECT * FROM focus_modes ORDER BY name ASC")
    fun observeAll(): Flow<List<FocusModeEntity>>

    @Query("SELECT * FROM focus_modes WHERE id = :id")
    suspend fun getById(id: String): FocusModeEntity?

    @Query("SELECT * FROM focus_modes WHERE id = :id")
    fun observeById(id: String): Flow<FocusModeEntity?>

    @Query("SELECT * FROM focus_modes WHERE isActive = 1")
    suspend fun getActiveModes(): List<FocusModeEntity>

    @Query("SELECT * FROM focus_modes WHERE isActive = 1")
    fun observeActiveModes(): Flow<List<FocusModeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FocusModeEntity)

    @Update
    suspend fun update(entity: FocusModeEntity)

    @Query("DELETE FROM focus_modes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE focus_modes SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE focus_modes SET isActive = 0 WHERE id = :id")
    suspend fun deactivateMode(id: String)

    @Query("UPDATE focus_modes SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: String)

    @Query("SELECT * FROM focus_modes WHERE scheduleEnabled = 1")
    suspend fun getScheduledModes(): List<FocusModeEntity>
}
