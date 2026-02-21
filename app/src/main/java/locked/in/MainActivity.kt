package locked.`in`

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import locked.`in`.service.FocusModeController
import locked.`in`.ui.navigation.AppNavigation
import locked.`in`.ui.theme.MyApplicationTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionId = intent?.getStringExtra(FocusModeController.EXTRA_SESSION_ID)

        setContent {
            MyApplicationTheme {
                AppNavigation(deepLinkSessionId = sessionId)
            }
        }
    }
}
