package dev.dwm.liftlog.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.ui.notifyRest

/** App-wide "workout in progress" flag — drives keep-screen-awake. */
object WorkoutSession {
    var active by mutableStateOf(false)
}

/** App-wide rest timer — lives outside any screen so it survives tab switches. */
object RestTimer {
    var endsAt by mutableStateOf<Long?>(null)
        private set
    var durationMs by mutableStateOf(90_000L)
        private set
    var over by mutableStateOf(false)
        private set

    fun start(seconds: Int) {
        durationMs = seconds * 1000L
        endsAt = nowMillis() + durationMs
        over = false
        notifyRest(endsAt)
    }

    fun add(seconds: Int) {
        val e = endsAt ?: return
        val newEnd = (e + seconds * 1000L).coerceAtLeast(nowMillis())
        durationMs = (durationMs + seconds * 1000L).coerceAtLeast(1000L)
        endsAt = newEnd
        notifyRest(newEnd)
    }

    fun expire() {
        over = true
        notifyRest(null)
    }

    fun clear() {
        endsAt = null
        over = false
        notifyRest(null)
    }
}
