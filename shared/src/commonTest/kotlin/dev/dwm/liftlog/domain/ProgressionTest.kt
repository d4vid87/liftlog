package dev.dwm.liftlog.domain

import dev.dwm.liftlog.data.db.ProgramExercise
import dev.dwm.liftlog.data.db.Rules
import dev.dwm.liftlog.data.db.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressionTest {
    private fun pe(rule: String, weight: Double = 100.0, tm: Double = 100.0, scheme: String? = null, amrapBump: Boolean = false, cyclePos: Int = 0) =
        ProgramExercise(
            programDayId = "d", exerciseId = "e", position = 0, rule = rule,
            sets = 3, repsMin = 5, repsMax = 8, workingWeightKg = weight, tmKg = tm,
            schemeJson = scheme, cycleLength = scheme?.let { parseScheme(it).size } ?: 1,
            incrementKg = 2.5, amrapBump = amrapBump, cyclePos = cyclePos,
        )

    private fun sets(vararg reps: Int) = reps.mapIndexed { i, r ->
        WorkoutSet(workoutId = "w", exerciseId = "e", setIndex = i, weightKg = 100.0, reps = r, completed = true)
    }

    @Test
    fun linearSuccessAddsWeight() {
        val next = progress(pe(Rules.LINEAR), sets(5, 5, 5))
        assertEquals(102.5, next.workingWeightKg)
        assertEquals(0, next.failCount)
    }

    @Test
    fun linearThreeFailsDeloads() {
        var p = pe(Rules.LINEAR)
        repeat(3) { p = progress(p, sets(5, 5, 3)) }
        assertEquals(90.0, p.workingWeightKg)
        assertEquals(0, p.failCount)
    }

    @Test
    fun doubleProgressionOnlyAtRepsMax() {
        val held = progress(pe(Rules.DOUBLE), sets(6, 6, 5))
        assertEquals(100.0, held.workingWeightKg)
        val bumped = progress(pe(Rules.DOUBLE), sets(8, 8, 8))
        assertEquals(102.5, bumped.workingWeightKg)
    }

    @Test
    fun tmPctCycleAdvancesThenBumps() {
        val scheme = encodeScheme(
            listOf(
                listOf(SchemeSet(0.65, 5), SchemeSet(0.85, 5, amrap = true)),
                listOf(SchemeSet(0.70, 3), SchemeSet(0.90, 3, amrap = true)),
            )
        )
        var p = pe(Rules.TM_PCT, tm = 100.0, scheme = scheme)
        p = progress(p, sets(5, 5))
        assertEquals(100.0, p.tmKg)
        assertEquals(1, p.cyclePos)
        p = progress(p, sets(3, 3))
        assertEquals(102.5, p.tmKg)
        assertEquals(0, p.cyclePos)
    }

    @Test
    fun nsunsAmrapBumpScales() {
        val scheme = encodeScheme(listOf(listOf(SchemeSet(0.75, 5), SchemeSet(0.95, 1, amrap = true))))
        // 5+ reps over target of 1 → double increment
        val big = progress(pe(Rules.TM_PCT, tm = 100.0, scheme = scheme, amrapBump = true), sets(5, 5))
        assertEquals(105.0, big.tmKg)
        // 0 reps on AMRAP → no bump
        val none = progress(pe(Rules.TM_PCT, tm = 100.0, scheme = scheme, amrapBump = true), sets(5, 0))
        assertEquals(100.0, none.tmKg)
    }

    @Test
    fun prescriptionUsesTmPercent() {
        val scheme = encodeScheme(listOf(listOf(SchemeSet(0.65, 5), SchemeSet(0.85, 3))))
        val p = prescription(pe(Rules.TM_PCT, tm = 100.0, scheme = scheme))
        assertEquals(65.0, p[0].weightKg)
        assertEquals(85.0, p[1].weightKg)
    }
}
