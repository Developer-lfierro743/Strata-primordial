package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TitleScreenTest {

    @Test
    fun titleScreenProducesValidVertexData() {
        val data = TitleScreen.build(TitleScreen.TitleState(time = 0f), 1280, 720)
        assertTrue(data.isNotEmpty(), "title screen should draw something")
        assertEquals(0, data.size % 7, "title vertex data must be a multiple of 7 floats")
    }

    @Test
    fun starfieldAnimatesOverTime() {
        // The starfield is time-driven, so two frames at different times must
        // not produce identical geometry (twinkle + drift).
        val t0 = TitleScreen.build(TitleScreen.TitleState(time = 0f), 1280, 720)
        val t5 = TitleScreen.build(TitleScreen.TitleState(time = 5.3f), 1280, 720)
        assertNotSame(t0, t5, "each frame should be a fresh array")
        assertTrue(
            !t0.contentEquals(t5),
            "starfield should animate with time (twinkle/drift)"
        )
    }

    @Test
    fun allFiveButtonsExist() {
        // Click the center of each button's rect at a fixed window size and
        // verify the right action comes back. Also confirms the buttons are
        // arranged in a usable column.
        val w = 1280
        val h = 720
        val expected = listOf(
            TitleAction.SINGLEPLAYER,
            TitleAction.MULTIPLAYER,
            TitleAction.NATIVE_MODS,
            TitleAction.OPTIONS,
            TitleAction.EXIT
        )
        // Reuse the internal layout math: buttons are stacked vertically
        // starting at winH*0.44 - total/2. To avoid duplicating that math
        // here, just probe a vertical line through the center and collect
        // the actions we hit.
        var y = 0f
        val hits = ArrayList<TitleAction>()
        while (y < h) {
            val action = TitleScreen.buttonAt(w / 2f, y, w, h)
            if (action != null && (hits.isEmpty() || hits.last() != action)) {
                hits.add(action)
            }
            y += 2f
        }
        assertEquals(expected, hits, "button column order should match")
    }

    @Test
    fun buttonClickOutsideHitsNothing() {
        assertNull(TitleScreen.buttonAt(10f, 10f, 1280, 720), "top corner has no button")
        assertNull(TitleScreen.buttonAt(1270f, 710f, 1280, 720), "bottom corner has no button")
    }

    @Test
    fun versionAndCopyrightPresent() {
        assertTrue(TitleScreen.GAME_VERSION.isNotBlank(), "version should not be blank")
        assertTrue(
            TitleScreen.COPYRIGHT.contains("Novusforge Studios"),
            "copyright should name the studio"
        )
        assertTrue(TitleScreen.COPYRIGHT.contains("Do not Distribute"))
    }

    @Test
    fun screenManagerTransitions() {
        assertEquals(Screen.TITLE, ScreenManager.current, "app should boot to the title screen")
        ScreenManager.switchTo(Screen.GAME)
        assertEquals(Screen.GAME, ScreenManager.current)
        assertNotNull(ScreenManager.lastTransition)
        assertEquals(Screen.TITLE, ScreenManager.lastTransition!!.from)
        assertEquals(Screen.GAME, ScreenManager.lastTransition!!.to)
        // Switching to the same screen is a no-op (no new transition recorded).
        ScreenManager.switchTo(Screen.GAME)
        assertEquals(Screen.GAME, ScreenManager.current)
        assertEquals(Screen.TITLE, ScreenManager.lastTransition!!.from, "duplicate switch must not re-record")
        ScreenManager.switchTo(Screen.TITLE)
        assertEquals(Screen.TITLE, ScreenManager.current)
    }
}
