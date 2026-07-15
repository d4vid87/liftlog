package dev.dwm.liftlog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecoveryTest {
    @Test
    fun muscleMappingUsesMusclesThenCategory() {
        assertEquals(listOf(Muscle.GLUTES, Muscle.QUADS).sorted(), musclesFor("Glutes, Quads", "Legs").sorted())
        assertEquals(listOf(Muscle.LATS, Muscle.TRAPS), musclesFor("", "Back"))
        assertTrue(musclesFor("", "Cardio").isEmpty())
    }

    @Test
    fun fatigueDecaysAndNormalizes() {
        val loads = listOf(
            MuscleLoad(listOf(Muscle.CHEST), 1000.0, 0.0),
            MuscleLoad(listOf(Muscle.QUADS), 1000.0, 48.0), // one half-life old
        )
        val f = fatigueMap(loads)
        assertEquals(1.0, f[Muscle.CHEST]!!, 1e-9)
        assertEquals(0.5, f[Muscle.QUADS]!!, 1e-9)
    }

    @Test
    fun readinessCountsDownFixedHours() {
        val now = 1_000_000_000_000L
        val r = readiness(mapOf(Muscle.QUADS to now - 24 * 3600_000L), now)
        val quads = r.first { it.muscle == Muscle.QUADS }
        assertEquals(48, quads.hoursLeft)
        assertEquals(24f / 72f, quads.readyFraction, 1e-6f)
        val chest = r.first { it.muscle == Muscle.CHEST }
        assertEquals(0, chest.hoursLeft)
        assertEquals(1f, chest.readyFraction)
        assertEquals(Muscle.QUADS, r.first().muscle) // least ready first
    }

    private fun List<Muscle>.sorted() = sortedBy { it.name }
}
