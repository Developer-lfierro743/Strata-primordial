package strata

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Player physics: gravity + ground collision, wall blocking, jumping,
 * sneaking, and water (non-solid, buoyant). All sims use the same fixed
 * 1/60s step as the game loop.
 */
class PlayerCollisionTest {

    private val DT = 1f / 60f

    /** Flat terrain chunk (top block y=7, so the ground surface is y=8). */
    private fun flatWorld(): World {
        val w = World(seed = 42)
        val c = Chunk(32)
        c.generateFlat(8)
        w.putChunk(0, 0, 0, c)
        return w
    }

    /** Drop a player from a height and run until they settle on the ground. */
    private fun settle(world: World, start: Vec3, ticks: Int = 600): Player {
        val p = Player(world)
        p.position = start
        for (i in 0 until ticks) p.tick(DT, 0f, 0f, jump = false, sneak = false)
        return p
    }

    @Test
    fun fallsToGroundAndLands() {
        val w = flatWorld()
        val p = settle(w, Vec3(16f, 40f, 16f))
        assertTrue(p.onGround, "player should be grounded after falling")
        assertEquals(8f, p.position.y, 0.02f, "feet should rest on the surface (y=8)")
    }

    @Test
    fun cannotWalkThroughWall() {
        val w = flatWorld()
        // Wall: x 16..20, y 0..10, z 8..24
        for (x in 16..20) for (y in 0..10) for (z in 8..24) {
            w.setBlock(x, y, z, BlockId.Stone)
        }
        val p = Player(w)
        p.position = Vec3(10f, 30f, 16f)
        // Fall first, then walk straight into the wall for 2 seconds.
        for (i in 0 until 60) p.tick(DT, 0f, 0f, jump = false, sneak = false)
        for (i in 0 until 120) p.tick(DT, 1f, 0f, jump = false, sneak = false)
        assertTrue(p.onGround, "player should be grounded while walking")
        assertTrue(
            p.position.x < 16f - Player.HALF_WIDTH + 0.05f,
            "player must not enter the wall — x=${p.position.x}"
        )
    }

    @Test
    fun jumpLeavesGroundThenLands() {
        val w = flatWorld()
        val p = settle(w, Vec3(16f, 30f, 16f))
        assertTrue(p.onGround)
        val restY = p.position.y
        // Hold jump through the whole ascent (full jump), release, then track
        // the peak as the arc plays out.
        var peak = p.position.y
        for (i in 0 until 30) p.tick(DT, 0f, 0f, jump = true, sneak = false)
        for (i in 0 until 90) {
            p.tick(DT, 0f, 0f, jump = false, sneak = false)
            peak = maxOf(peak, p.position.y)
        }
        assertTrue(peak > restY + 0.5f, "full jump should lift the player (peak $peak vs $restY)")
        // Finish the arc: should land back on the ground.
        for (i in 0 until 120) p.tick(DT, 0f, 0f, jump = false, sneak = false)
        assertTrue(p.onGround, "player should land after the jump arc")
        assertEquals(restY, p.position.y, 0.05f, "landing height should match the ground")
    }

    @Test
    fun tapIsShortHopHoldIsFullJump() {
        val w = flatWorld()
        // Tap: a single one-tick press, released immediately → short hop.
        val tap = settle(w, Vec3(10f, 30f, 16f))
        tap.tick(DT, 0f, 0f, jump = true, sneak = false)
        var tapPeak = tap.position.y
        for (i in 0 until 60) {
            tap.tick(DT, 0f, 0f, jump = false, sneak = false)
            tapPeak = maxOf(tapPeak, tap.position.y)
        }
        // Hold: jump held through the whole ascent → full jump.
        val hold = settle(w, Vec3(10f, 30f, 20f))
        var holdPeak = hold.position.y
        for (i in 0 until 30) {
            hold.tick(DT, 0f, 0f, jump = true, sneak = false)
            holdPeak = maxOf(holdPeak, hold.position.y)
        }
        for (i in 0 until 60) {
            hold.tick(DT, 0f, 0f, jump = false, sneak = false)
            holdPeak = maxOf(holdPeak, hold.position.y)
        }
        assertTrue(
            holdPeak > tapPeak + 0.5f,
            "holding Space should jump higher than a tap (hold $holdPeak vs tap $tapPeak)"
        )
        assertTrue(tapPeak > 8.2f, "a quick tap should still hop a little (tap $tapPeak)")
    }

    @Test
    fun bufferedJumpPressFiresOnLanding() {
        val w = flatWorld()
        val p = Player(w)
        p.position = Vec3(16f, 8.1f, 16f)   // barely above the ground (surface y=8)
        // One airborne tap, then release — the press must still fire on landing.
        p.tick(DT, 0f, 0f, jump = true, sneak = false)
        var jumped = false
        for (i in 0 until 40) {
            p.tick(DT, 0f, 0f, jump = false, sneak = false)
            if (p.velocity.y > 4f) {
                jumped = true
                break
            }
        }
        assertTrue(jumped, "a jump pressed just before landing should still fire (buffered)")
    }

    @Test
    fun coyoteTimeAllowsJumpAfterLeavingLedge() {
        val w = flatWorld()
        // Raised platform (surface y=9); clear the ground east of it so walking
        // off the edge is a real fall, not a one-block step-down.
        for (x in 10..20) for (z in 8..24) w.setBlock(x, 8, z, BlockId.Stone)
        for (x in 21..30) for (z in 8..24) for (y in 0..9) w.setBlock(x, y, z, BlockId.Air)
        val p = Player(w)
        p.position = Vec3(19.4f, 20f, 16f)
        for (i in 0 until 600) p.tick(DT, 0f, 0f, jump = false, sneak = false)
        assertTrue(p.onGround, "player should stand on the platform")
        // Walk east off the edge; press jump within the coyote grace window.
        var airborneTicks = 0
        var jumped = false
        for (i in 0 until 60) {
            val press = airborneTicks in 1..4   // within the coyote grace window
            p.tick(DT, 1f, 0f, jump = press, sneak = false)
            if (!p.onGround && p.velocity.y <= 0f) airborneTicks++
            else if (p.onGround) airborneTicks = 0
            if (p.velocity.y > 4f) {
                jumped = true
                break
            }
        }
        assertTrue(jumped, "jump pressed just after leaving a ledge should fire (coyote time)")
    }

    @Test
    fun walkingKeepsPlayerGroundedAtConstantHeight() {
        val w = flatWorld()
        val p = settle(w, Vec3(10f, 30f, 16f))
        val startY = p.position.y
        var maxDeviation = 0f
        for (i in 0 until 120) {
            p.tick(DT, 1f, 0f, jump = false, sneak = false)
            maxDeviation = maxOf(maxDeviation, abs(p.position.y - startY))
        }
        assertTrue(p.onGround, "player stays grounded while walking")
        assertTrue(maxDeviation < 0.1f, "height should not bounce while walking (dev $maxDeviation)")
        assertTrue(p.position.x > 14f, "player should have moved forward (x=${p.position.x})")
    }

    @Test
    fun sneakIsSlowerThanWalk() {
        val w = flatWorld()
        val walk = settle(w, Vec3(8f, 30f, 16f))
        val sneak = settle(w, Vec3(8f, 30f, 20f))
        for (i in 0 until 60) {
            walk.tick(DT, 1f, 0f, jump = false, sneak = false)
            sneak.tick(DT, 1f, 0f, jump = false, sneak = true)
        }
        assertTrue(
            walk.position.x > sneak.position.x + 1f,
            "walking should outpace sneaking (walk ${walk.position.x} vs sneak ${sneak.position.x})"
        )
    }

    @Test
    fun waterIsNotSolidAndBuoyant() {
        val w = flatWorld()
        // Water pool: y 8..14 over x 8..24 / z 8..24, sitting on the flat ground.
        for (x in 8..24) for (y in 8..14) for (z in 8..24) {
            w.setBlock(x, y, z, BlockId.Water)
        }
        val p = Player(w)
        p.position = Vec3(16f, 30f, 16f)
        var sawWater = false
        for (i in 0 until 240) {
            p.tick(DT, 0f, 0f, jump = false, sneak = false)
            if (p.inWater) sawWater = true
        }
        assertTrue(sawWater, "player should be marked inWater when submerged")
        assertFalse(p.onGround, "player should float in water, not stand on it")
        // Buoyancy caps the sink rate; the player must still be in the pool.
        assertTrue(p.position.y > 6f, "player should not sink through the floor (y=${p.position.y})")
        assertTrue(p.velocity.y > -2f, "sink rate should be gentle (vy=${p.velocity.y})")
    }

    @Test
    fun swimmingUpRisesThroughWater() {
        val w = flatWorld()
        for (x in 8..24) for (y in 8..20) for (z in 8..24) {
            w.setBlock(x, y, z, BlockId.Water)
        }
        val p = Player(w)
        p.position = Vec3(16f, 10f, 16f)   // deep in the pool
        for (i in 0 until 30) p.tick(DT, 0f, 0f, jump = true, sneak = false)
        assertTrue(p.inWater)
        assertTrue(p.position.y > 11.4f, "swimming up should rise through water (y=${p.position.y})")
    }

    /* ── Game modes ── */

    @Test
    fun creativeHoversWithoutGravity() {
        val w = flatWorld()
        val p = Player(w)
        p.position = Vec3(16f, 30f, 16f)
        for (i in 0 until 120) p.tick(DT, 0f, 0f, jump = false, sneak = false, mode = GameMode.CREATIVE)
        assertEquals(30f, p.position.y, 0.01f, "creative should hold altitude with no input")
        assertFalse(p.onGround, "flying is not standing")
    }

    @Test
    fun creativeFliesUpButStopsAtCeiling() {
        val w = flatWorld()
        // Ceiling slab at y=24 (must stay inside the 32-tall chunk).
        for (x in 8..24) for (z in 8..24) w.setBlock(x, 24, z, BlockId.Stone)
        val p = Player(w)
        p.position = Vec3(16f, 10f, 16f)
        for (i in 0 until 120) p.tick(DT, 0f, 0f, jump = true, sneak = false, mode = GameMode.CREATIVE)
        val maxY = 24f - Player.HEIGHT   // feet can't push the head past the slab
        assertTrue(p.position.y > 20f, "creative should climb while holding jump (y=${p.position.y})")
        assertTrue(p.position.y <= maxY + 0.05f, "creative must not phase through the ceiling (y=${p.position.y})")
    }

    @Test
    fun creativeCannotWalkThroughWall() {
        val w = flatWorld()
        for (x in 16..20) for (y in 0..10) for (z in 8..24) {
            w.setBlock(x, y, z, BlockId.Stone)
        }
        val p = Player(w)
        p.position = Vec3(10f, 8f, 16f)
        for (i in 0 until 120) p.tick(DT, 1f, 0f, jump = false, sneak = false, mode = GameMode.CREATIVE)
        assertTrue(p.position.x < 16f - Player.HALF_WIDTH + 0.05f, "creative flight still collides (x=${p.position.x})")
    }

    @Test
    fun spectatorNoclipsThroughWall() {
        val w = flatWorld()
        for (x in 16..20) for (y in 0..10) for (z in 8..24) {
            w.setBlock(x, y, z, BlockId.Stone)
        }
        val p = Player(w)
        p.position = Vec3(10f, 8f, 16f)
        for (i in 0 until 120) p.tick(DT, 1f, 0f, jump = false, sneak = false, mode = GameMode.SPECTATOR)
        assertTrue(p.position.x > 20.5f, "spectator should pass straight through the wall (x=${p.position.x})")
        assertEquals(8f, p.position.y, 0.01f, "no-clip flight keeps altitude")
    }

    @Test
    fun spectatorDivesAndClimbs() {
        val w = flatWorld()
        val p = Player(w)
        p.position = Vec3(16f, 30f, 16f)
        for (i in 0 until 60) p.tick(DT, 0f, 0f, jump = false, sneak = true, mode = GameMode.SPECTATOR)
        assertTrue(p.position.y < 25f, "sneak should dive (y=${p.position.y})")
        for (i in 0 until 60) p.tick(DT, 0f, 0f, jump = true, sneak = false, mode = GameMode.SPECTATOR)
        assertTrue(p.position.y > 27f, "jump should climb again (y=${p.position.y})")
    }
}
