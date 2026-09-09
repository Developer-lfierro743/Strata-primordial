package strata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fixed-step tests for the survival sim (double hearts, thirst, XP). */
class SurvivalTest {

    /** 1/60 fixed step, like the game's accumulator. */
    private fun tickSeconds(s: Survival, seconds: Float, underwater: Boolean = false) {
        val steps = (seconds * 60f).toInt()
        repeat(steps) { s.tick(1f / 60f, underwater) }
    }

    @Test
    fun doubleHeartsStartAtFortyHealth() {
        val s = Survival()
        assertEquals(40f, s.health, "double hearts = 40 HP")
        assertEquals(40f, Survival.MAX_HEALTH)
    }

    @Test
    fun hungerDrainsOnePointEveryThirtySeconds() {
        val s = Survival()
        tickSeconds(s, 31f)
        assertEquals(19f, s.hunger)
    }

    @Test
    fun thirstDrainsFasterThanHunger() {
        val s = Survival()
        tickSeconds(s, 37f)
        assertEquals(19f, s.hunger, "hunger: 1 drain per 30s")
        assertEquals(18f, s.thirst, "thirst: 2 drains per 37s (18s interval)")
    }

    @Test
    fun eatRestoresHungerCappedAtMax() {
        val s = Survival()
        s.hunger = 0f
        s.eat(6f)
        assertEquals(6f, s.hunger)
        s.eat(8f)
        assertEquals(14f, s.hunger)
        s.eat(100f)
        assertEquals(20f, s.hunger, "hunger must cap at 20")
    }

    @Test
    fun drinkRestoresThirstCappedAtMax() {
        val s = Survival()
        s.thirst = 3f
        s.drink(5f)
        assertEquals(8f, s.thirst)
        s.drink(100f)
        assertEquals(20f, s.thirst, "thirst must cap at 20")
    }

    @Test
    fun regenRequiresBothFoodAndWater() {
        val s = Survival()
        s.health = 10f
        s.hunger = 19f
        s.thirst = 5f
        // Well-fed but dehydrated: no regen.
        tickSeconds(s, 5f)
        assertEquals(10f, s.health, "no regen while dehydrated")
        // Now hydrated too: regen kicks in (+1 per 4s).
        s.thirst = 19f
        tickSeconds(s, 5f)
        assertEquals(11f, s.health, "regen once fed AND hydrated")
    }

    @Test
    fun drowningDrainsHealthButNeverBelowFloor() {
        val s = Survival()
        s.health = 2f
        tickSeconds(s, 3f, underwater = true)   // 1 drain at 2.5s
        assertEquals(1f, s.health)
        tickSeconds(s, 600f, underwater = true)  // keeps draining but hits floor
        assertEquals(1f, s.health, "health must never drop below the floor")
    }

    @Test
    fun starvationAndDehydrationDrainHealth() {
        val s = Survival()
        s.hunger = 0f
        s.thirst = 0f
        s.health = 10f
        // 25s: starve at 8/16/24 (3 drains) + dehydrate at 6/12/18/24 (4) = 7.
        tickSeconds(s, 25f)
        assertEquals(3f, s.health)
    }

    @Test
    fun xpLevelsUpWithGrowingCost() {
        val s = Survival()
        assertEquals(10f, s.xpToNext, "first level needs 10 XP")
        val ups = s.addXp(10f + 3f)
        assertEquals(1, ups)
        assertEquals(1, s.level)
        assertEquals(3f, s.xp, "leftover XP carries into the next level")
        assertEquals(15f, s.xpToNext, "each level costs more")
        // A big pile can level multiple times. Costs grow per level: next up is
        // 15, then 20 (level 2), then 25 (level 3). 48 XP = 15 + 20 + 13 leftover.
        val more = s.addXp(45f)
        assertEquals(2, more)
        assertEquals(3, s.level)
        assertTrue(s.xp in 0f..s.xpToNext, "XP progress always stays below the next threshold")
    }

    @Test
    fun eatingAndDrinkingGrantXp() {
        val s = Survival()
        s.eat(1f)
        s.drink(1f)
        assertEquals(4f, s.xp, "eat + drink should each grant 2 XP")
    }

    @Test
    fun xpProgressIsClampedToUnitInterval() {
        val s = Survival()
        s.addXp(5f)   // 5/10
        assertEquals(0.5f, s.xpProgress)
        s.addXp(100f) // levels up multiple times
        assertTrue(s.xpProgress in 0f..1f)
        assertTrue(s.xpProgress > 0f || s.level > 0)
    }
}
