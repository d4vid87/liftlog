package dev.dwm.liftlog

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.dwm.liftlog.data.db.createDatabase
import dev.dwm.liftlog.data.logSetFromWatch
import kotlinx.coroutines.runBlocking

class WearListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/liftlog/log-set") return
        val parts = event.data.decodeToString().split("|")
        if (parts.size != 3) return
        val weight = parts[1].toDoubleOrNull() ?: return
        val reps = parts[2].toIntOrNull() ?: return
        val db = createDatabase(applicationContext)
        // ponytail: runBlocking in service callback; fine for a single tiny insert
        runBlocking { logSetFromWatch(db, parts[0], weight, reps) }
        db.close()
    }
}
