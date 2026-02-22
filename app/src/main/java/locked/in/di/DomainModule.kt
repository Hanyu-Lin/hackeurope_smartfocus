package locked.`in`.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import locked.`in`.domain.classifier.DjlNotificationModel
import locked.`in`.domain.classifier.NotificationModel
import locked.`in`.service.BundleNotificationPosterImpl
import locked.`in`.service.BundleNotificationPosterInterface
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindNotificationModel(impl: DjlNotificationModel): NotificationModel

    @Binds
    @Singleton
    abstract fun bindBundleNotificationPoster(impl: BundleNotificationPosterImpl): BundleNotificationPosterInterface
}
