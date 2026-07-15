package dev.dwm.liftlog.ui

enum class Haptic { Tick, Click, Buzz }

/** Vibration feedback; no-op on desktop. */
expect fun haptic(kind: Haptic)

/** Ongoing rest-countdown notification (Android); pass null to cancel. No-op on desktop. */
expect fun notifyRest(endsAt: Long?)

/** High-priority "rest over" notification with sound. No-op on desktop. */
expect fun notifyRestOver()

enum class Tone { Low, High, Tick }

/** Short distinct pitch for tempo metronome phases. Falls back to beep on desktop. */
expect fun playTone(t: Tone)

/** Loud alarm-stream blast for rest-over. Falls back to beep on desktop. */
expect fun playAlarm()

/** Text-to-speech announcement, best-effort. No-op on desktop. */
expect fun speak(text: String)

/** Prevent screen lock while an exercise/timer is active. No-op on desktop. */
expect fun keepScreenAwake(on: Boolean)
