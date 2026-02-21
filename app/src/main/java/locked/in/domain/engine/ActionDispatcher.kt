package locked.`in`.domain.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
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

    private val soundPool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }

    fun dispatch(action: RuleAction) {
        when (action) {
            RuleAction.BUZZ -> {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            RuleAction.ALARM -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION).toFloat() /
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).toFloat()
                // Play default notification sound via SoundPool
                // In production, load a user-selected sound; for now use a short beep
                soundPool.play(0, volume, volume, 1, 0, 1f)
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
