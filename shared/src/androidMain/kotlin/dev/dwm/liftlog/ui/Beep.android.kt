package dev.dwm.liftlog.ui

import android.media.AudioManager
import android.media.ToneGenerator

private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull() }

actual fun playBeep() {
    runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250) }
}
