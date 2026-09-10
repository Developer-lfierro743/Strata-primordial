package strata

import kotlin.math.roundToInt

/**
 * The player's survival stats — the truth behind the animated HUD bars.
 *
 * Holds health, hunger, thirst and experience, plus the slow drain/regen
 * timers that drive them. This is pure state + fixed-step math so it lives in
 * commonMain and is unit-testable; the HUD eases its *display* values toward
 * these truths per frame (a rendering concern, handled in Main.kt).
 *
 * Feature parity with the formula:
 *  - **2× double hearts** — [MAX_HEALTH] is 40 (20 hearts) instead of 20.
 *  - **Thirst bar** — drains faster than hunger; empty thirst dehydrates you.
 *  - **Better XP bar** — XP gained from breaking blocks / eating / drinking,
 *    levels up with a growing per-level cost.
 * No death: health bottoms out at [HEALTH_FLOOR] (the gentle-sim rule).
 */
class Survival(
    var health: Float = MAX_HEALTH,
    var hunger: Float = MAX_HUNGER,
    var thirst: Float = MAX_THIRST,
    var xp: Float = 0f,
    var level: Int = 0
) {
    companion object {
        /** Double hearts: 40 HP = 20 hearts (Minecraft has 20 HP / 10 hearts). */
        const val MAX_HEALTH = 40f
        const val MAX_HUNGER = 20f
        const val MAX_THIRST = 20f

        /** Hunger loses one point every [HUNGER_DRAIN_SECONDS]. */
        const val HUNGER_DRAIN_SECONDS = 30f
        /** Thirst drains faster than hunger. */
        const val THIRST_DRAIN_SECONDS = 18f
        const val REGEN_INTERVAL = 4f      // +1 HP per interval while fed + hydrated
        const val DROWN_INTERVAL = 2.5f    // -1 HP per interval while underwater
        const val STARVE_INTERVAL = 8f     // -1 HP per interval with empty hunger
        const val DEHYDRATE_INTERVAL = 6f  // -1 HP per interval with empty thirst
        /** Health never drops below this (no death in the current sim). */
        const val HEALTH_FLOOR = 1f

        /** A bar is "full enough" to regenerate when at/above this. */
        const val REGEN_THRESHOLD = 18f
    }

    private var hungerTimer = 0f
    private var thirstTimer = 0f
    private var regenTimer = 0f
    private var drownTimer = 0f
    private var starveTimer = 0f
    private var dehydrateTimer = 0f

    /** XP needed to reach the next level — grows with each level. */
    val xpToNext: Float get() = 10f + level * 5f

    /**
     * Advance the survival sim one fixed step of [dt] seconds.
     *
     * @param underwater true when the player's head is below sea level (drowns).
     */
    fun tick(dt: Float, underwater: Boolean) {
        /* Hunger + thirst drain over time (thirst faster than hunger). */
        hungerTimer += dt
        if (hungerTimer >= HUNGER_DRAIN_SECONDS) {
            hungerTimer = 0f
            hunger = (hunger - 1f).coerceAtLeast(0f)
        }
        thirstTimer += dt
        if (thirstTimer >= THIRST_DRAIN_SECONDS) {
            thirstTimer = 0f
            thirst = (thirst - 1f).coerceAtLeast(0f)
        }

        /* Regenerate only while BOTH well-fed and well-hydrated. */
        if (hunger >= REGEN_THRESHOLD && thirst >= REGEN_THRESHOLD && health < MAX_HEALTH) {
            regenTimer += dt
            if (regenTimer >= REGEN_INTERVAL) {
                regenTimer = 0f
                health = (health + 1f).coerceAtMost(MAX_HEALTH)
            }
        } else {
            regenTimer = 0f
        }

        /* Drowning. */
        if (underwater) {
            drownTimer += dt
            if (drownTimer >= DROWN_INTERVAL) {
                drownTimer = 0f
                health = (health - 1f).coerceAtLeast(HEALTH_FLOOR)
            }
        } else {
            drownTimer = 0f
        }

        /* Starvation (empty hunger) + dehydration (empty thirst). */
        if (hunger <= 0f) {
            starveTimer += dt
            if (starveTimer >= STARVE_INTERVAL) {
                starveTimer = 0f
                health = (health - 1f).coerceAtLeast(HEALTH_FLOOR)
            }
        } else {
            starveTimer = 0f
        }
        if (thirst <= 0f) {
            dehydrateTimer += dt
            if (dehydrateTimer >= DEHYDRATE_INTERVAL) {
                dehydrateTimer = 0f
                health = (health - 1f).coerceAtLeast(HEALTH_FLOOR)
            }
        } else {
            dehydrateTimer = 0f
        }
    }

    /** Eat food: restores hunger (capped) and grants a little XP. */
    fun eat(food: Float) {
        hunger = (hunger + food).coerceAtMost(MAX_HUNGER)
        addXp(2f)
    }

    /** Drink water: restores thirst (capped) and grants a little XP. */
    fun drink(amount: Float) {
        thirst = (thirst + amount).coerceAtMost(MAX_THIRST)
        addXp(2f)
    }

    /**
     * Gain [points] XP, leveling up whenever the bar fills. Returns the number
     * of level-ups so callers can announce it.
     */
    fun addXp(points: Float): Int {
        xp += points
        var ups = 0
        while (xp >= xpToNext) {
            xp -= xpToNext
            level++
            ups++
        }
        return ups
    }

    /** Progress toward the next level, in 0..1 (for the XP bar). */
    val xpProgress: Float get() = (xp / xpToNext).coerceIn(0f, 1f)

    /** Nicer log/debug printing. */
    override fun toString(): String =
        "Survival(hp=${health.roundToInt()}/${MAX_HEALTH.roundToInt()}, " +
            "hunger=${hunger.roundToInt()}/20, thirst=${thirst.roundToInt()}/20, " +
            "lvl=$level xp=${xp.roundToInt()}/${xpToNext.roundToInt()})"
}
