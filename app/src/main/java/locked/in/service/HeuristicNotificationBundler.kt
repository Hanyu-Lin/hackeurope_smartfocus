package locked.`in`.service

import locked.`in`.data.local.entity.NotificationEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeuristicNotificationBundler @Inject constructor() : NotificationBundler {

    private val nonBundleableCategories = setOf("call", "missed_call", "system")

    override suspend fun assignBundleId(
        entity: NotificationEntity,
        existingBundles: List<BundleInfo>
    ): String? {
        val category = entity.notificationCategory ?: return null
        if (category in nonBundleableCategories) return null
        return "bundle_$category"
    }
}
