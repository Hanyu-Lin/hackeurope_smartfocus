package locked.`in`.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import locked.`in`.data.local.entity.NotificationBundleEntity

@Dao
interface NotificationBundleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationBundleEntity)

    @Update
    suspend fun update(entity: NotificationBundleEntity)

    @Query("SELECT * FROM notification_bundle WHERE bundleId = :bundleId")
    suspend fun getByBundleId(bundleId: String): NotificationBundleEntity?

    @Query("SELECT * FROM notification_bundle")
    suspend fun getAll(): List<NotificationBundleEntity>

    @Query("DELETE FROM notification_bundle")
    suspend fun deleteAll()
}
