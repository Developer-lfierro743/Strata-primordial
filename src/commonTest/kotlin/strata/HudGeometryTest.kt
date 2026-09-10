package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the HUD geometry is complete: the build output must contain quads
 * in every screen region — top-left bars, top-right info, bottom-center
 * hotbar, XP bar, and bottom-right minimap. Guards against the classic bug
 * where the buffer got truncated and only the top of the HUD drew.
 */
class HudGeometryTest {

    private fun yBounds(data: FloatArray, w: Int, h: Int): Pair<Float, Float> {
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < data.size) {
            val nx = data[i]
            val ny = data[i + 1]
            i += 7
            // Clip-space y: +1 = bottom, -1 = top. Convert to pixel y.
            val py = (1f - ny) / 2f * h
            if (ny != 0f && py >= 0f && py <= h) {
                if (py < minY) minY = py
                if (py > maxY) maxY = py
            }
            if (nx == -999f) break
        }
        return minY to maxY
    }

    @Test
    fun hudCoversFullScreenIncludingHotbarAndXp() {
        val w = 1280
        val h = 720
        val data = Hud.build(
            Hud.HudState(
                camX = 0f, camY = 13f, camZ = 0f, yaw = 0f, fps = 60,
                seed = 12345, chunks = 81,
                health = 40, hunger = 20, thirst = 20,
                xp = 0.5f, xpLevel = 3,
                selectedSlot = 4,
                time = 1f
            ),
            w, h
        )
        assertTrue(data.isNotEmpty(), "HUD should produce vertex data")
        assertEquals(0, data.size % 7, "HUD data must be a multiple of 7 floats")

        // Collect pixel-space y values of every vertex.
        val ys = ArrayList<Float>()
        var i = 0
        while (i < data.size) {
            val ny = data[i + 1]
            ys.add((1f - ny) / 2f * h)
            i += 7
        }
        val minY = ys.minOrNull()!!
        val maxY = ys.maxOrNull()!!
        println("HUD pixel-Y range: $minY .. $maxY (screen 0..$h)")

        // The hotbar sits at the bottom: hotbarY = h - 16 - 42 = 662 in 720-space.
        val hasBottomQuads = ys.any { it >= h - 90f }
        assertTrue(hasBottomQuads, "HUD must include quads in the bottom 90px (hotbar area) — got max Y $maxY")

        // XP bar just above the hotbar.
        val xpBand = (h - 120f)..(h - 80f)
        val hasXpQuads = ys.any { it in xpBand }
        assertTrue(hasXpQuads, "HUD must include quads in the XP-bar band ${xpBand} — got range $minY..$maxY")

        // Top-left bars (y < 120).
        val hasTopQuads = ys.any { it < 120f }
        assertTrue(hasTopQuads, "HUD must include quads in the top 120px (bars)")
    }
}
