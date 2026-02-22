package locked.`in`.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import locked.`in`.data.local.entity.FilterRuleEntity

@Dao
interface FilterRuleDao {

    @Query("SELECT * FROM filter_rules ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules WHERE focusModeId = :focusModeId ORDER BY sortOrder ASC")
    fun observeByFocusModeId(focusModeId: String): Flow<List<FilterRuleEntity>>

    @Query("SELECT * FROM filter_rules WHERE focusModeId = :focusModeId ORDER BY sortOrder ASC")
    suspend fun getByFocusModeId(focusModeId: String): List<FilterRuleEntity>

    @Query("SELECT * FROM filter_rules WHERE id = :id")
    suspend fun getById(id: String): FilterRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FilterRuleEntity)

    @Update
    suspend fun update(entity: FilterRuleEntity)

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM filter_rules WHERE focusModeId = :focusModeId")
    suspend fun deleteByFocusModeId(focusModeId: String)
}
