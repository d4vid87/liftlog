package dev.dwm.liftlog.ui

actual fun haptic(kind: Haptic) {}

actual fun notifyRest(endsAt: Long?) {}

actual fun notifyRestOver() {}

actual fun playTone(t: Tone) = playBeep()

actual fun playAlarm() = playBeep()

actual fun speak(text: String) {}
