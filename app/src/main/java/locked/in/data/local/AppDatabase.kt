package locked.`in`.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import locked.`in`.data.local.dao.BundleMapDao
import locked.`in`.data.local.dao.FilterRuleDao
import locked.`in`.data.local.dao.FocusModeDao
import locked.`in`.data.local.dao.NotificationRecordDao
import locked.`in`.data.local.entity.BundleMapEntryEntity
import locked.`in`.data.local.entity.FilterRuleEntity
import locked.`in`.data.local.entity.FocusModeEntity
import locked.`in`.data.local.entity.NotificationRecordEntity
import locked.`in`.data.local.entity.NotificationRecordFts

@Database(
    entities = [
        FocusModeEntity::class,
        FilterRuleEntity::class,
        NotificationRecordEntity::class,
        NotificationRecordFts::class,
        BundleMapEntryEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusModeDao(): FocusModeDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun notificationRecordDao(): NotificationRecordDao
    abstract fun bundleMapDao(): BundleMapDao

    companion object {
        const val DATABASE_NAME = "smartfocus_v2_db"
    }
}
