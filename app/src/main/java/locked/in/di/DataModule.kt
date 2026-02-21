package locked.`in`.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import locked.`in`.data.local.AppDatabase
import locked.`in`.data.local.dao.BundleMapDao
import locked.`in`.data.local.dao.FilterRuleDao
import locked.`in`.data.local.dao.FocusModeDao
import locked.`in`.data.local.dao.NotificationBundleDao
import locked.`in`.data.local.dao.NotificationRecordDao
import locked.`in`.data.repository.BundleRepository
import locked.`in`.data.repository.BundleRepositoryImpl
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.FocusModeRepositoryImpl
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.data.repository.NotificationRecordRepositoryImpl
import locked.`in`.data.repository.SettingsRepository
import locked.`in`.data.repository.SettingsRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideFocusModeDao(db: AppDatabase): FocusModeDao = db.focusModeDao()

    @Provides
    @Singleton
    fun provideFilterRuleDao(db: AppDatabase): FilterRuleDao = db.filterRuleDao()

    @Provides
    @Singleton
    fun provideNotificationRecordDao(db: AppDatabase): NotificationRecordDao = db.notificationRecordDao()

    @Provides
    @Singleton
    fun provideBundleMapDao(db: AppDatabase): BundleMapDao = db.bundleMapDao()

    @Provides
    @Singleton
    fun provideNotificationBundleDao(db: AppDatabase): NotificationBundleDao = db.notificationBundleDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFocusModeRepository(impl: FocusModeRepositoryImpl): FocusModeRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRecordRepository(impl: NotificationRecordRepositoryImpl): NotificationRecordRepository

    @Binds
    @Singleton
    abstract fun bindBundleRepository(impl: BundleRepositoryImpl): BundleRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
