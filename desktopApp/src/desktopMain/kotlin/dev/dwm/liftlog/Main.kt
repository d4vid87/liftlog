package dev.dwm.liftlog

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.dwm.liftlog.data.db.createDatabase
import dev.dwm.liftlog.ui.App

fun main() {
    val db = createDatabase()
    application {
        Window(onCloseRequest = ::exitApplication, title = "LiftLog") {
            App(db, saveExport = { content ->
                val file = java.io.File(
                    System.getProperty("user.home"),
                    "liftlog-export-${System.currentTimeMillis()}.json",
                )
                file.writeText(content)
                file.absolutePath
            })
        }
    }
}
