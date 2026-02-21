package locked.`in`.ui.screens.onboarding

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import locked.`in`.service.SmartNotificationListener
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isListenerEnabled = MutableStateFlow(false)
    val isListenerEnabled: StateFlow<Boolean> = _isListenerEnabled

    fun checkListenerEnabled() {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(context, SmartNotificationListener::class.java).flattenToString()
        _isListenerEnabled.value = !TextUtils.isEmpty(flat) && flat.split(":").any { it == componentName }
    }
}
