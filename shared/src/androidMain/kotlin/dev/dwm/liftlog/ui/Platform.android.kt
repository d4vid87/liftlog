package dev.dwm.liftlog.ui

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

// set once from MainActivity.onCreate
@SuppressLint("StaticFieldLeak")
var appContext: Context? = null

private const val CHANNEL_REST = "rest"
private const val NOTIF_ID = 42

private fun vibrator(): Vibrator? = appContext?.let { ctx ->
    if (Build.VERSION.SDK_INT >= 31) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

actual fun haptic(kind: Haptic) {
    val v = vibrator() ?: return
    runCatching {
        val effect = when (kind) {
            Haptic.Tick -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            Haptic.Click -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            Haptic.Buzz -> VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1)
        }
        v.vibrate(effect)
    }
}

// ponytail: single loud generator reused for alarm + tempo tones; recreated never, fine for app lifetime
private val alarmTone by lazy {
    runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100) }.getOrNull()
}
private var tts: android.speech.tts.TextToSpeech? = null
private var ttsReady = false

actual fun playTone(t: Tone) {
    runCatching {
        val tone = when (t) {
            Tone.Low -> android.media.ToneGenerator.TONE_DTMF_1
            Tone.High -> android.media.ToneGenerator.TONE_DTMF_9
            Tone.Tick -> android.media.ToneGenerator.TONE_PROP_BEEP
        }
        alarmTone?.startTone(tone, 150)
    }
}

actual fun playAlarm() {
    runCatching { alarmTone?.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) }
}

actual fun speak(text: String) {
    val ctx = appContext ?: return
    runCatching {
        if (tts == null) {
            tts = android.speech.tts.TextToSpeech(ctx) { status ->
                ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                if (ttsReady) tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "overload")
            }
        } else if (ttsReady) {
            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "overload")
        }
    }
}

private fun manager(): NotificationManager? =
    appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

private fun ensureChannel(m: NotificationManager) {
    m.createNotificationChannel(
        NotificationChannel(CHANNEL_REST, "Rest timer", NotificationManager.IMPORTANCE_HIGH)
    )
}

actual fun notifyRest(endsAt: Long?) {
    val ctx = appContext ?: return
    val m = manager() ?: return
    ensureChannel(m)
    if (endsAt == null) {
        m.cancel(NOTIF_ID)
        return
    }
    runCatching {
        val n = NotificationCompat.Builder(ctx, CHANNEL_REST)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Resting")
            .setContentText("Rest timer running")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAt)
            .setOngoing(true)
            .setSilent(true)
            .build()
        m.notify(NOTIF_ID, n)
    }
}

actual fun notifyRestOver() {
    val ctx = appContext ?: return
    val m = manager() ?: return
    ensureChannel(m)
    runCatching {
        val n = NotificationCompat.Builder(ctx, CHANNEL_REST)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Rest over — GO!")
            .setContentText("Next set time")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        m.notify(NOTIF_ID, n)
    }
}
