package dev.dwm.liftlog.domain

import dev.dwm.liftlog.data.db.ProgramExercise
import dev.dwm.liftlog.data.db.Rules
import dev.dwm.liftlog.data.db.WorkoutSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SchemeSet(val pct: Double, val reps: Int, val amrap: Boolean = false)

private val json = Json { ignoreUnknownKeys = true }

/** schemeJson = JSON array of weeks; each week = array of sets. cyclePos indexes the week. */
fun parseScheme(schemeJson: String?): List<List<SchemeSet>> =
    schemeJson?.let { json.decodeFromString<List<List<SchemeSet>>>(it) } ?: emptyList()

fun encodeScheme(weeks: List<List<SchemeSet>>): String = json.encodeToString(weeks)

/** Round to nearest 2.5kg (plate-loadable). */
fun roundToPlate(kg: Double): Double = kotlin.math.round(kg / 2.5) * 2.5

data class Prescribed(val weightKg: Double, val reps: Int, val amrap: Boolean)

/** Prescribed sets for a program exercise at its current cycle position. */
fun prescription(pe: ProgramExercise): List<Prescribed> = when (pe.rule) {
    Rules.TM_PCT -> {
        val weeks = parseScheme(pe.schemeJson)
        val week = weeks.getOrNull(pe.cyclePos % weeks.size.coerceAtLeast(1)).orEmpty()
        week.map { Prescribed(roundToPlate(pe.tmKg * it.pct), it.reps, it.amrap) }
    }
    else -> List(pe.sets) { Prescribed(pe.workingWeightKg, pe.repsMin, false) }
}

/**
 * Progression after completing a program-day workout.
 * completedSets = the logged, completed sets for this exercise, in set order.
 */
fun progress(pe: ProgramExercise, completedSets: List<WorkoutSet>): ProgramExercise = when (pe.rule) {
    Rules.LINEAR -> {
        val success = completedSets.size >= pe.sets && completedSets.all { it.reps >= pe.repsMin }
        when {
            success -> pe.copy(workingWeightKg = pe.workingWeightKg + pe.incrementKg, failCount = 0)
            pe.failCount + 1 >= pe.failLimit ->
                pe.copy(workingWeightKg = roundToPlate(pe.workingWeightKg * 0.9), failCount = 0)
            else -> pe.copy(failCount = pe.failCount + 1)
        }
    }
    Rules.DOUBLE -> {
        val maxedOut = completedSets.size >= pe.sets && completedSets.all { it.reps >= pe.repsMax }
        val success = completedSets.size >= pe.sets && completedSets.all { it.reps >= pe.repsMin }
        when {
            maxedOut -> pe.copy(workingWeightKg = pe.workingWeightKg + pe.incrementKg, failCount = 0)
            success -> pe.copy(failCount = 0)
            pe.failCount + 1 >= pe.failLimit ->
                pe.copy(workingWeightKg = roundToPlate(pe.workingWeightKg * 0.9), failCount = 0)
            else -> pe.copy(failCount = pe.failCount + 1)
        }
    }
    Rules.TM_PCT -> {
        val weeks = parseScheme(pe.schemeJson).size.coerceAtLeast(1)
        val nextPos = (pe.cyclePos + 1) % weeks
        val cycleDone = pe.cyclePos + 1 >= weeks
        val bump = when {
            !cycleDone -> 0.0
            pe.amrapBump -> {
                // nSuns-style: TM bump keyed to reps achieved on the AMRAP (1+) set
                val week = parseScheme(pe.schemeJson).getOrNull(pe.cyclePos).orEmpty()
                val amrapIndex = week.indexOfFirst { it.amrap }
                val amrapReps = completedSets.getOrNull(amrapIndex)?.reps ?: 0
                val target = week.getOrNull(amrapIndex)?.reps ?: 1
                when {
                    amrapReps >= target + 4 -> pe.incrementKg * 2
                    amrapReps >= target + 1 -> pe.incrementKg
                    amrapReps >= target -> pe.incrementKg / 2
                    else -> 0.0
                }
            }
            else -> pe.incrementKg
        }
        pe.copy(tmKg = pe.tmKg + bump, cyclePos = nextPos)
    }
    else -> pe
}
