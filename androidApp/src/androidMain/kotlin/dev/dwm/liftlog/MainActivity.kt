package dev.dwm.liftlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.dwm.liftlog.data.db.createDatabase
import dev.dwm.liftlog.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = createDatabase(applicationContext)
        setContent { App(db) }
    }
}
