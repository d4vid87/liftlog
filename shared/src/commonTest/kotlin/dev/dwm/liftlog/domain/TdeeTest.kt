package dev.dwm.liftlog.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TdeeTest {
    @Test
    fun stableWeightMeansTdeeEqualsIntake() {
        val weights = (0L..20L).map { DayWeight(it, 80.0) }
        val intakes = (0L..20L).map { DayIntake(it, 2500.0) }
        val r = computeTdee(weights, intakes, goalKgPerWeek = 0.0)!!
        assertTrue(abs(r.tdeeKcal - 2500.0) < 1.0, "tdee=${r.tdeeKcal}")
        assertTrue(abs(r.targetKcal - 2500.0) < 1.0)
    }

    @Test
    fun losingWeightRaisesTdeeAboveIntake() {
        // losing ~0.5 kg/week while eating 2000 → TDEE ≈ 2000 + 0.5*7700/7 ≈ 2550
        val weights = (0L..27L).map { DayWeight(it, 80.0 - it * 0.5 / 7) }
        val intakes = (0L..27L).map { DayIntake(it, 2000.0) }
        val r = computeTdee(weights, intakes, goalKgPerWeek = 0.0)!!
        assertTrue(r.tdeeKcal > 2400 && r.tdeeKcal < 2700, "tdee=${r.tdeeKcal}")
    }

    @Test
    fun cutGoalLowersTarget() {
        val weights = (0L..20L).map { DayWeight(it, 80.0) }
        val intakes = (0L..20L).map { DayIntake(it, 2500.0) }
        val r = computeTdee(weights, intakes, goalKgPerWeek = -0.5)!!
        assertTrue(abs(r.targetKcal - (2500.0 - 0.5 * KCAL_PER_KG / 7)) < 1.0)
    }

    @Test
    fun insufficientDataReturnsNull() {
        val weights = (0L..3L).map { DayWeight(it, 80.0) }
        val intakes = (0L..3L).map { DayIntake(it, 2500.0) }
        assertNull(computeTdee(weights, intakes, 0.0))
    }

    @Test
    fun trendSmoothsSpikes() {
        val weights = listOf(80.0, 80.0, 83.0, 80.0, 80.0).mapIndexed { i, kg -> DayWeight(i.toLong(), kg) }
        val trend = trendWeights(weights)
        assertTrue(trend[2].kg < 81.5) // spike damped
    }
}
