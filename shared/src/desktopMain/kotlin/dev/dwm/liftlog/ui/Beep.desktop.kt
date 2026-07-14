package dev.dwm.liftlog.ui

actual fun playBeep() {
    runCatching { java.awt.Toolkit.getDefaultToolkit().beep() }
}
