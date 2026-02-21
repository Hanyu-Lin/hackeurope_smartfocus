package locked.`in`

import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import locked.`in`.service.SmartNotificationListener
import locked.`in`.ui.navigation.AppNavigation
import locked.`in`.ui.theme.SmartFocusTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val needsOnboarding = !isNotificationListenerEnabled()

        setContent {
            SmartFocusTheme {
                AppNavigation(startOnboarding = needsOnboarding)
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (TextUtils.isEmpty(flat)) return false
        val componentName = ComponentName(this, SmartNotificationListener::class.java).flattenToString()
        return flat.split(":").any { it == componentName }
    }
}
