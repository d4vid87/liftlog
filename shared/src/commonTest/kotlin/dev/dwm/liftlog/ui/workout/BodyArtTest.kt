package dev.dwm.liftlog.ui.workout

import dev.dwm.liftlog.domain.Muscle
import kotlin.test.Test
import kotlin.test.assertTrue

class BodyArtTest {
    @Test
    fun everyMuscleIsMappedToASlug() {
        val mapped = slugMuscle.values.toSet()
        for (m in Muscle.entries) assertTrue(m in mapped, "no body region maps to $m")
    }

    @Test
    fun everyPathStringIsWellFormed() {
        for ((slug, ds) in BodyArt.front + BodyArt.back) {
            assertTrue(ds.isNotEmpty(), "slug $slug has no paths")
            for (d in ds) assertTrue(d.isNotBlank() && d.first().uppercaseChar() == 'M', "bad path in $slug: ${d.take(12)}")
        }
    }
}
