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
import locked.`in`.data.local.MIGRATION_1_2
import locked.`in`.data.local.MIGRATION_2_3
import locked.`in`.data.local.MIGRATION_3_4
import locked.`in`.data.local.MIGRATION_4_5
import locked.`in`.data.local.dao.FocusSessionDao
import locked.`in`.data.local.dao.NotificationDao
import locked.`in`.data.repository.NotificationRepository
import locked.`in`.data.repository.NotificationRepositoryImpl
import locked.`in`.data.repository.SessionRepository
import locked.`in`.data.repository.SessionRepositoryImpl
import locked.`in`.data.repository.SettingsRepository
import locked.`in`.data.repository.SettingsRepositoryImpl
import locked.`in`.service.HeuristicNotificationBundler
import locked.`in`.service.NotificationBundler
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
            "smartfocus_db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
    }

    @Provides
    @Singleton
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    @Singleton
    fun provideFocusSessionDao(database: AppDatabase): FocusSessionDao {
        return database.focusSessionDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        impl: NotificationRepositoryImpl
    ): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository

    @Binds
    @Singleton
    abstract fun bindNotificationBundler(
        impl: HeuristicNotificationBundler
    ): NotificationBundler
}
