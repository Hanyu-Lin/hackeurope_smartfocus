package locked.`in`.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bundle_map")
data class BundleMapEntryEntity(
    @PrimaryKey val bundleIndex: Int,
    val bundleId: String,
    val centroid: ByteArray,
    val packageName: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BundleMapEntryEntity) return false
        return bundleIndex == other.bundleIndex &&
            bundleId == other.bundleId &&
            centroid.contentEquals(other.centroid) &&
            packageName == other.packageName
    }

    override fun hashCode(): Int {
        var result = bundleIndex
        result = 31 * result + bundleId.hashCode()
        result = 31 * result + centroid.contentHashCode()
        result = 31 * result + packageName.hashCode()
        return result
    }
}
