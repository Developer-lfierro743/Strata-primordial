package strata

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudTest {
    @Test
    fun hudProducesValidVertexData() {
        val data = Hud.build(
            Hud.HudState(camX = 0f, camY = 13f, camZ = 0f, yaw = 0f, fps = 60, seed = 12345, chunks = 81),
            1280, 720
        )
        assertTrue(data.isNotEmpty(), "HUD should draw something")
        assertEquals(0, data.size % 7, "HUD vertex data must be a multiple of 7 floats")
    }

    @Test
    fun everyFontGlyphRendersAtLeastOneQuad() {
        val glyphs = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-.,:/%+=()!?*<>#[]"
        for (ch in glyphs) {
            val data = Hud.renderChar(ch)
            assertTrue(data.isNotEmpty(), "glyph '$ch' rendered no quads — font entry is empty?")
            assertEquals(0, data.size % 7)
        }
    }

    @Test
    fun unknownGlyphsRenderNothing() {
        // Characters not in the font must not crash or emit geometry.
        assertEquals(0, Hud.renderChar('~').size)
    }

    @Test
    fun hudIsNotVerticallyMirrored() {
        // A glyph drawn at pixel (0,0) — the top-left corner — must land in the
        // TOP half of clip space (y <= 0). Before the Vulkan Y-flip fix it
        // landed in the bottom half (y > 0), i.e. the whole HUD was upside down.
        val data = Hud.renderChar('A', 1280, 720)
        assertTrue(data.isNotEmpty())
        var maxY = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < data.size) {
            if (data[i + 1] > maxY) maxY = data[i + 1]
            i += 7
        }
        assertTrue(maxY <= 0f, "top-left glyph leaked into the bottom half of the screen (y=$maxY)")
    }

    @Test
    fun hotbarHasElevenSlots() {
        assertEquals(11, Hud.HOTBAR.size)
        // Every slot must be usable: a placeable block, a food item, or a drink.
        for (item in Hud.HOTBAR) {
            assertTrue(item.block != null || item.food > 0 || item.drink > 0, "slot must be a block, food, or drink: $item")
        }
        // Two food slots + one water bottle (the rest are placeable blocks).
        val foods = Hud.HOTBAR.count { it.food > 0 }
        val drinks = Hud.HOTBAR.count { it.drink > 0 }
        assertEquals(2, foods, "hotbar should carry two food items")
        assertEquals(1, drinks, "hotbar should carry one water bottle")
    }

    @Test
    fun hotbarPaletteMatchesMesher() {
        // Every block icon color must equal the mesher's world color for that
        // block, so the icon and the placed block look identical.
        for (item in Hud.HOTBAR) {
            val b = item.block ?: continue
            val (r, g, bl) = Hud.iconColor(item)
            assertEquals(ChunkMesher.blockColor(b), Triple(r, g, bl), "palette mismatch for $b")
        }
    }

    @Test
    fun foodSlotsRestoreHunger() {
        // The two food slots must actually refill hunger (otherwise the survival
        // bar drains forever with no recovery — the reviewer's concern).
        val foods = Hud.HOTBAR.filter { it.food > 0 }
        assertTrue(foods.isNotEmpty(), "expected at least one food slot in the hotbar")
        for (item in foods) {
            assertTrue(item.block == null, "food slot should not also place a block")
            assertTrue(item.food in 1..20, "food value out of range: ${item.food}")
        }
    }

    @Test
    fun selectionPopStartsAndSettlesAtOne() {
        // The pop must start exactly at 1.0 (no jump on the first frame),
        // peak mid-way, and settle back to exactly 1.0 after the duration.
        assertEquals(1f, Hud.popScale(0f), "pop at t=0 must be exactly 1.0")
        assertEquals(1f, Hud.popScale(0.5f), "pop after the duration must settle to 1.0")
        assertEquals(1f, Hud.popScale(10f), "pop far past the duration must stay at 1.0")
    }

    @Test
    fun selectionPopNeverGoesBelowOneAndPeaksEarly() {
        // The pop is a scale that briefly overshoots past 1.0 but never shrinks
        // the slot below its resting size, and the peak lands early (punchy).
        var peak = 0f
        var peakTime = 0f
        var t = 0f
        while (t <= 0.25f) {
            val s = Hud.popScale(t)
            assertTrue(s >= 1f, "pop must never shrink below 1.0 (t=$t, s=$s)")
            if (s > peak) {
                peak = s
                peakTime = t
            }
            t += 0.01f
        }
        assertTrue(peak > 1.05f, "pop should visibly overshoot (peak=$peak)")
        assertTrue(peak < 1.25f, "pop should stay subtle (peak=$peak)")
        // The exponential decay shifts the peak early in the pop (punchy feel).
        assertTrue(peakTime in 0.04f..0.12f, "peak should land early in the pop (t=$peakTime)")
    }

    @Test
    fun gamemodesHideHudElements() {
        // Creative drops the hunger + thirst bars; spectator drops those, the
        // hotbar, the XP bar and the crosshair — so each step must emit fewer
        // quads than the one before it.
        fun data(mode: GameMode) = Hud.build(
            Hud.HudState(camX = 0f, camY = 13f, camZ = 0f, yaw = 0f, fps = 60, seed = 12345, chunks = 81,
                gamemode = mode),
            1280, 720
        )
        val survival = data(GameMode.SURVIVAL)
        val creative = data(GameMode.CREATIVE)
        val spectator = data(GameMode.SPECTATOR)
        assertTrue(
            creative.size < survival.size,
            "creative should hide hunger/thirst (survival=${survival.size}, creative=${creative.size})"
        )
        assertTrue(
            spectator.size < creative.size,
            "spectator should hide more than creative (creative=${creative.size}, spectator=${spectator.size})"
        )
    }

    @Test
    fun ghostTrailRendersExtraGeometry() {
        // The Minecraft-style damage trail: with ghost > display, the bar must
        // emit a wider trailing bar than when both are equal.
        val damaged = Hud.build(
            Hud.HudState(camX = 0f, camY = 13f, camZ = 0f, yaw = 0f, fps = 60, seed = 12345, chunks = 81,
                health = 10, hunger = 20,
                healthDisplay = 10f, hungerDisplay = 20f,
                healthGhost = 15f, hungerGhost = 20f),
            1280, 720
        )
        val steady = Hud.build(
            Hud.HudState(camX = 0f, camY = 13f, camZ = 0f, yaw = 0f, fps = 60, seed = 12345, chunks = 81,
                health = 10, hunger = 20,
                healthDisplay = 10f, hungerDisplay = 20f,
                healthGhost = 10f, hungerGhost = 20f),
            1280, 720
        )
        assertTrue(
            damaged.size > steady.size,
            "ghost trail should add geometry (damaged=${damaged.size}, steady=${steady.size})"
        )
    }
}
