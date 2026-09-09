package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TerrainTest {
    @Test
    fun heightIsDeterministic() {
        assertEquals(Terrain.heightAt(42, 3, 7), Terrain.heightAt(42, 3, 7))
        assertEquals(Terrain.heightAt(42, -5, 100), Terrain.heightAt(42, -5, 100))
    }

    @Test
    fun differentSeedsGiveDifferentTerrain() {
        var different = 0
        for (x in 0..20) for (z in 0..20) {
            if (Terrain.heightAt(1, x, z) != Terrain.heightAt(2, x, z)) different++
        }
        assertTrue(different > 20, "expected seeds to produce different terrain")
    }

    @Test
    fun heightIsBounded() {
        for (x in 0..10) for (z in 0..10) {
            val h = Terrain.heightAt(7, x, z)
            assertTrue(h in 2..30, "height $h out of range at ($x,$z)")
        }
    }
}
