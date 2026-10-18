package nl.bis.screensaver

import org.junit.Assert.assertEquals
import org.junit.Test

class MixPlanTest {
    @Test
    fun `eigen mix verdeelt per onderdeel`() {
        val plan = MixPlan.plan(Program.CUSTOM, setOf(Mode.ART, Mode.AERIALS, Mode.AMBIENT), hasPhotos = false, hasAmbient = true)
        assertEquals(listOf(Mode.ART to 2, Mode.AERIALS to 1, Mode.AMBIENT to 3), plan.rotation)
    }

    @Test
    fun `zonder sleutels of foto's vallen die onderdelen weg`() {
        val plan = MixPlan.plan(Program.CUSTOM, setOf(Mode.PHOTOS, Mode.AMBIENT, Mode.NATURE), hasPhotos = false, hasAmbient = false)
        assertEquals(listOf(Mode.NATURE to 2), plan.rotation)
    }

    @Test
    fun `blijft er niets over, dan kunst`() {
        assertEquals(listOf(Mode.ART to 1), MixPlan.plan(Program.PHOTOS, emptySet(), hasPhotos = false).rotation)
        assertEquals(
            listOf(Mode.ART to 1),
            MixPlan.plan(Program.HISTORY, emptySet(), hasPhotos = true, emptyModes = setOf(Mode.HISTORY)).rotation,
        )
    }

    @Test
    fun `lege eigen mix wordt kunst`() {
        assertEquals(listOf(Mode.ART to 2), MixPlan.plan(Program.CUSTOM, emptySet(), hasPhotos = true).rotation)
    }
}
