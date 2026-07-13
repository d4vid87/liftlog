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

    private fun List<Muscle>.sorted() = sortedBy { it.name }
}
