package locked.`in`.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import locked.`in`.data.local.entity.BundleMapEntryEntity

@Dao
interface BundleMapDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BundleMapEntryEntity)

    @Query("SELECT * FROM bundle_map WHERE bundleIndex = :index")
    suspend fun getByIndex(index: Int): BundleMapEntryEntity?

    @Query("SELECT * FROM bundle_map WHERE bundleId = :bundleId")
    suspend fun getByBundleId(bundleId: String): BundleMapEntryEntity?

    @Query("UPDATE bundle_map SET centroid = :centroid, updatedAt = :updatedAt WHERE bundleIndex = :index")
    suspend fun updateCentroid(index: Int, centroid: ByteArray, updatedAt: Long)

    @Query("SELECT COALESCE(MAX(bundleIndex) + 1, 0) FROM bundle_map")
    suspend fun nextIndex(): Int


    @Query("SELECT COUNT(*) FROM bundle_map")
    suspend fun count(): Int

    @Query("DELETE FROM bundle_map")
    suspend fun deleteAll()
}
