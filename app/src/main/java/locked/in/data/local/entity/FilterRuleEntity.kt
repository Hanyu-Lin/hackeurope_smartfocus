package locked.`in`.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "filter_rules",
    foreignKeys = [
        ForeignKey(
            entity = FocusModeEntity::class,
            parentColumns = ["id"],
            childColumns = ["focusModeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("focusModeId")]
)
data class FilterRuleEntity(
    @PrimaryKey val id: String,
    val focusModeId: String,
    val type: String,
    val value: String,
    val effect: String,
    val action: String,
    val sortOrder: Int
)
