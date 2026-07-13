package dev.dwm.liftlog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalcsTest {
    @Test
    fun e1rmEpley() {
        assertEquals(100.0, e1rm(100.0, 1))
        assertEquals(100.0 * (1 + 5 / 30.0), e1rm(100.0, 5))
    }

    @Test
    fun platesBasic() {
        // 100kg on 20kg bar = 40 per side = 25 + 15
        assertEquals(listOf(25.0, 15.0), platesPerSide(100.0))
        // exact bar weight = no plates
        assertTrue(platesPerSide(20.0)!!.isEmpty())
        // below bar impossible
        assertNull(platesPerSide(10.0))
        // fractional: 102.5 -> 41.25 per side
        assertEquals(listOf(25.0, 15.0, 1.25), platesPerSide(102.5))
    }
}
