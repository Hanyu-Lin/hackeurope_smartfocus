package locked.`in`.domain.engine

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import locked.`in`.domain.model.RuleAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun dispatch(action: RuleAction) {
        when (action) {
            RuleAction.BUZZ -> {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            RuleAction.ALARM -> {
                val uri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                uri?.let {
                    runCatching {
                        val ringtone = RingtoneManager.getRingtone(context, it)
                        ringtone.streamType = AudioManager.STREAM_NOTIFICATION
                        ringtone.play()
                    }
                }
            }
            RuleAction.SILENT -> {
                // Force silent — no action needed, notification passes through silently
            }
            RuleAction.NONE -> {
                // Default system behavior
            }
        }
    }
}
