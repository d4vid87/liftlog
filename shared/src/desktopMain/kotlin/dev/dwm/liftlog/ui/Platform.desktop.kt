package dev.dwm.liftlog.ui

actual fun haptic(kind: Haptic) {}

actual fun notifyRest(endsAt: Long?) {}

actual fun notifyRestOver() {}

// soft enveloped-sine tempo notes (marimba-ish), same pitches as Android; on a bg thread so it
// never blocks. DOWN low, UP high, pause a quieter tick.
private const val TONE_SR = 44100
private fun synthTone(freq: Double, ms: Int, peak: Double) {
    runCatching {
        val n = TONE_SR * ms / 1000
        val data = ByteArray(n * 2)
        for (i in 0 until n) {
            val t = i.toDouble() / TONE_SR
            val env = Math.exp(-t * 22.0) * (1.0 - Math.exp(-t * 500.0))
            val s = (Math.sin(2.0 * Math.PI * freq * t) * env * peak * Short.MAX_VALUE).toInt()
            data[i * 2] = (s and 0xFF).toByte()
            data[i * 2 + 1] = (s shr 8 and 0xFF).toByte()
        }
        val fmt = javax.sound.sampled.AudioFormat(TONE_SR.toFloat(), 16, 1, true, false)
        val line = javax.sound.sampled.AudioSystem.getSourceDataLine(fmt)
        line.open(fmt); line.start()
        line.write(data, 0, data.size)
        line.drain(); line.stop(); line.close()
    }
}

actual fun playTone(t: Tone) {
    val (freq, ms, peak) = when (t) {
        Tone.Low -> Triple(392.0, 150, 0.6)
        Tone.High -> Triple(587.0, 150, 0.6)
        Tone.Tick -> Triple(494.0, 90, 0.35)
    }
    Thread { synthTone(freq, ms, peak) }.start()
}

actual fun playAlarm() = repeat(3) { playBeep() }

actual fun speak(text: String) {}

actual fun keepScreenAwake(on: Boolean) {}
