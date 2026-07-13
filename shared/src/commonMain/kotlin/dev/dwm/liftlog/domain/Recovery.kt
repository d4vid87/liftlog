package dev.dwm.liftlog.domain

import kotlin.math.pow

enum class Muscle {
    CHEST, LATS, TRAPS, SHOULDERS, BICEPS, TRICEPS,
    ABS, OBLIQUES, QUADS, HAMSTRINGS, GLUTES, CALVES,
}

/** Map an exercise's muscle string (seed data) + category fallback to muscle groups. */
fun musclesFor(muscles: String, category: String): List<Muscle> {
    val hits = buildList {
        val m = muscles.lowercase()
        // keywords cover both wger ("trapezius", "quads") and free-exercise-db ("traps", "quadriceps") vocab
        if ("chest" in m || "serratus" in m) add(Muscle.CHEST)
        if ("lats" in m || "middle back" in m) add(Muscle.LATS)
        if ("trap" in m) add(Muscle.TRAPS)
        if ("shoulders" in m) add(Muscle.SHOULDERS)
        if ("biceps" in m || "brachialis" in m) add(Muscle.BICEPS)
        if ("triceps" in m) add(Muscle.TRICEPS)
        if (("abs" in m && "abdominis" !in m) || "abdominal" in m) add(Muscle.ABS)
        if ("obliquus" in m) add(Muscle.OBLIQUES)
        if ("quads" in m || "quadriceps" in m) add(Muscle.QUADS)
        if ("hamstrings" in m) add(Muscle.HAMSTRINGS)
        if ("glutes" in m) add(Muscle.GLUTES)
        if ("calves" in m || "soleus" in m) add(Muscle.CALVES)
    }
    if (hits.isNotEmpty()) return hits
    return when (category.lowercase()) {
        "chest" -> listOf(Muscle.CHEST)
        "back" -> listOf(Muscle.LATS, Muscle.TRAPS)
        "shoulders" -> listOf(Muscle.SHOULDERS)
        "arms" -> listOf(Muscle.BICEPS, Muscle.TRICEPS)
        "abs" -> listOf(Muscle.ABS)
        "legs" -> listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES)
        "calves" -> listOf(Muscle.CALVES)
        else -> emptyList()
    }
}

data class MuscleLoad(val muscles: List<Muscle>, val volumeKg: Double, val hoursAgo: Double)

/**
 * Fitbod-style fatigue: volume decays exponentially (48h half-life),
 * normalized so the most-fatigued muscle in the window = 1.0.
 */
fun fatigueMap(loads: List<MuscleLoad>, halfLifeHours: Double = 48.0): Map<Muscle, Double> {
    val raw = mutableMapOf<Muscle, Double>()
    for (l in loads) {
        val decayed = l.volumeKg * 2.0.pow(-l.hoursAgo / halfLifeHours)
        for (m in l.muscles) raw[m] = (raw[m] ?: 0.0) + decayed
    }
    val max = raw.values.maxOrNull() ?: return emptyMap()
    if (max <= 0.0) return emptyMap()
    return raw.mapValues { it.value / max }
}
