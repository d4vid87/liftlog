package dev.dwm.liftlog.ui

enum class Haptic { Tick, Click, Buzz }

/** Vibration feedback; no-op on desktop. */
expect fun haptic(kind: Haptic)

/** Ongoing rest-countdown notification (Android); pass null to cancel. No-op on desktop. */
expect fun notifyRest(endsAt: Long?)

/** High-priority "rest over" notification with sound. No-op on desktop. */
expect fun notifyRestOver()
