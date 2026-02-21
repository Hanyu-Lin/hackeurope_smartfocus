package locked.`in`.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListenerStatusChecker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun isListenerEnabled(): Boolean {
        val componentName = ComponentName(context, SmartNotificationListener::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(componentName.flattenToString())
    }
}
