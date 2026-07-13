package dev.dwm.liftlog.domain

/** Epley estimated 1RM. reps=1 returns weight unchanged. */
fun e1rm(weightKg: Double, reps: Int): Double =
    if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)

/**
 * Plates per side for a target barbell weight.
 * Returns list of plate weights (kg) for one side, heaviest first,
 * or null if target below bar weight.
 */
fun platesPerSide(
    targetKg: Double,
    barKg: Double = 20.0,
    available: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5, 0.25),
): List<Double>? {
    if (targetKg < barKg) return null
    var perSide = (targetKg - barKg) / 2
    val result = mutableListOf<Double>()
    for (p in available.sortedDescending()) {
        while (perSide >= p - 1e-9) {
            result.add(p)
            perSide -= p
        }
    }
    return result
}
