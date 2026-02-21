package locked.`in`

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import locked.`in`.service.NotificationChannels

@HiltAndroidApp
class SmartFocusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
    }
}
