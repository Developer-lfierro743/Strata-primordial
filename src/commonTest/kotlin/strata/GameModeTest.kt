package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameModeTest {

    @Test
    fun fromParsesFullLabels() {
        assertEquals(GameMode.SURVIVAL, GameMode.from("survival"))
        assertEquals(GameMode.CREATIVE, GameMode.from("creative"))
        assertEquals(GameMode.SPECTATOR, GameMode.from("spectator"))
    }

    @Test
    fun fromIsCaseInsensitiveAndTrims() {
        assertEquals(GameMode.CREATIVE, GameMode.from("  CREATIVE "))
        assertEquals(GameMode.SURVIVAL, GameMode.from("SurViVaL"))
    }

    @Test
    fun fromParsesAliases() {
        assertEquals(GameMode.SURVIVAL, GameMode.from("s"))
        assertEquals(GameMode.CREATIVE, GameMode.from("c"))
        assertEquals(GameMode.SPECTATOR, GameMode.from("sp"))
    }

    @Test
    fun fromRejectsUnknownModes() {
        assertNull(GameMode.from("hardcore"))
        assertNull(GameMode.from("adventure"))
        assertNull(GameMode.from(""))
        assertNull(GameMode.from("x"))
    }

    @Test
    fun nextCyclesThroughAllModes() {
        assertEquals(GameMode.CREATIVE, GameMode.SURVIVAL.next())
        assertEquals(GameMode.SPECTATOR, GameMode.CREATIVE.next())
        assertEquals(GameMode.SURVIVAL, GameMode.SPECTATOR.next())
    }

    @Test
    fun labelsAndAliasesAreDistinct() {
        for (mode in GameMode.entries) {
            assertEquals(mode, GameMode.from(mode.label))
            assertEquals(mode, GameMode.from(mode.short))
        }
    }
}
