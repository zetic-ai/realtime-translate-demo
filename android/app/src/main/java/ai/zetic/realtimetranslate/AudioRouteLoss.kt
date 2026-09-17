package ai.zetic.realtimetranslate

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

/** Stops one audio owner before a disconnected private route can fall through to the speaker. */
internal class AudioRouteLossMonitor(context: Context) {
    private val applicationContext = context.applicationContext
    private var interruption: (() -> Unit)? = null
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioRouteLossContract.interrupts(intent?.action)) interruption?.invoke()
        }
    }

    fun start(onInterrupted: () -> Unit) {
        stop()
        interruption = onInterrupted
        if (Build.VERSION.SDK_INT >= 33) {
            applicationContext.registerReceiver(
                receiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            applicationContext.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }
    }

    fun stop() {
        interruption = null
        runCatching { applicationContext.unregisterReceiver(receiver) }
    }
}

internal object AudioRouteLossContract {
    fun interrupts(action: String?): Boolean = action == AudioManager.ACTION_AUDIO_BECOMING_NOISY
}
