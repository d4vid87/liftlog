package dev.dwm.liftlog.ui

/** A photo the user took: base64 JPEG for the vision model, plus a persistent local URI to display. */
data class CapturedPhoto(val base64: String, val localUri: String? = null)
